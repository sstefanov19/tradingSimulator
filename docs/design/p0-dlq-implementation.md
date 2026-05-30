# P0 Implementation Plan — Dead-Letter Queue & Poison-Message Handling

Status: reviewed via `/plan-eng-review` (2026-05-30). Decisions locked below.
Scope source: `PLAN.md` → P0. Builds on the reservation invariant from PR #6
(`docs/design/order-reservation.md`).

## Goal

Transient failures retry with bounded backoff; messages that exhaust retries
land in `order.DLT` for inspection/replay **and release their escrow** — never
silently dropped, never looping forever, never leaking a reservation.

## Locked decisions (from review)

1. **ErrorHandlingDeserializer** wraps both key + value deserializers, so a
   malformed payload becomes a `DeserializationException` routed to the DLT
   instead of looping inside the deserializer forever.
2. **`order.DLT` mirrors 3 partitions** to match the source `order` topic, so
   the recoverer's default same-partition resolver always lands.
3. **DLT listener** logs the failure reason + increments an `orders.dlt`
   Micrometer counter (reuses the counter pattern in `OrderExecutionService`).
4. **`FixedBackOff(2000L, 2L)`** — initial try + 2 retries over ~4s, then DLT.
5. **Escrow release on DLT recovery** — before publishing to the DLT, mark the
   order `REJECTED` and release its reservation via the existing
   `rejectAndRelease` path. A DLT'd order is terminal, so it must release escrow
   exactly like a business rejection. (Closes a fund-leak the bare DLQ creates.)
6. **Integration tests use `@EmbeddedKafka`** (spring-kafka-test), not
   Testcontainers — lighter, purpose-built for error-handler verification.

## Data flow

```
  order topic                          DefaultErrorHandler (new bean)
  ┌──────────┐   OrderEvent   ┌──────────────────────────────────────┐
  │ partition │──────────────▶│ OrderConsumer.consume                 │
  │  0/1/2    │               │   └─ executeOrder(event)              │
  └──────────┘               └───────────────┬──────────────────────┘
       ▲ malformed payload                    │
       │                                       │ outcome
       │                    ┌──────────────────┼─────────────────────────────┐
       │                    │                  │                              │
  ErrorHandlingDeserializer │  business reject  │  generic RuntimeException     │
  throws DeserializationEx  │  (caught in svc,  │  rethrown                     │
       │                    │   REJECTED + esc  │       │                       │
       │                    │   released, offset│       ▼                       │
       │                    │   advances) DONE  │  FixedBackOff(2s × 2)         │
       │                    └───────────────────┘   retry in-thread             │
       │                                              │            │            │
       │                                       transient│        exhausted /     │
       │                                       recovers │        DeserializationEx│
       │                                              ▼            ▼            │
       │                                           FILLS    RECOVERY HANDLER     │
       │                                                    ├─ event != null:    │
       │                                                    │   rejectAndRelease │
       │                                                    │   (REJECTED + esc) │
       │                                                    ├─ event == null     │
       │                                                    │   (deser fail):    │
       │                                                    │   skip release     │
       │                                                    └─ publish ─────────▶ order.DLT (3 part.)
       │                                                                              │
       │                                                                     DLT listener:
       │                                                                     log reason + orders.dlt++
       └──────────────────────────────────────────────────────────────────────────┘
```

## File-by-file changes

### 1. `src/main/resources/application.properties`
Wrap deserializers; declare retry knobs as plain constants in code (not
configurable for P0 — revisit if ops needs runtime tuning).

```
spring.kafka.consumer.key-deserializer=org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
spring.kafka.consumer.properties.spring.deserializer.key.delegate.class=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.properties.spring.deserializer.value.delegate.class=org.springframework.kafka.support.serializer.JsonDeserializer
```
Keep the existing `spring.json.trusted.packages` line — it now applies to the delegate.

### 2. `kafka/KafkaTopicConfig.java`
Add the DLT topic mirroring source partitions:
```java
@Bean
public NewTopic orderDltTopic() {
    return TopicBuilder.name("order.DLT").partitions(3).replicas(1).build();
}
```

### 3. `kafka/KafkaErrorConfig.java` (NEW)
The error-handler bean. Spring Boot auto-wires a single `DefaultErrorHandler`
bean into the auto-configured listener container factory — no factory override
needed.

```java
@Configuration
public class KafkaErrorConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<Object, Object> template,
            OrderExecutionService executionService) {

        var recoverer = new DeadLetterPublishingRecoverer(template,
                (rec, ex) -> new TopicPartition("order.DLT", rec.partition()));

        DefaultErrorHandler handler = new DefaultErrorHandler((rec, ex) -> {
            OrderEvent event = (rec.value() instanceof OrderEvent oe) ? oe : null;
            if (event != null) {
                // Terminal: a DLT'd order must release escrow like a rejection.
                executionService.rejectAndRelease(event);
            }
            // else: deserialization failure — no parseable order to release.
            recoverer.accept(rec, ex);
        }, new FixedBackOff(2000L, 2L));

        // DeserializationException is non-retryable by default in DefaultErrorHandler,
        // so deser poison messages skip the backoff and go straight to recovery.
        return handler;
    }
}
```
Notes:
- `KafkaTemplate<Object,Object>` for the recoverer publishes the original bytes
  + exception headers; the app's typed producer template is separate.
- Confirm the recoverer template's value serializer tolerates the raw payload
  (JsonSerializer with headers, or a ByteArray template) so a deser-failure
  record (raw bytes) can be republished. If the typed `JsonSerializer` chokes on
  raw bytes, register a dedicated `KafkaTemplate` with `ByteArraySerializer` for
  the DLT path. **Verify during implementation.**

### 4. `service/OrderExecutionService.java`
Change `rejectAndRelease(OrderEvent)` from `private` to package-private (or
public) so `KafkaErrorConfig` can call it. No logic change — it already runs in
its own committed transaction and is idempotent on a missing/already-rejected
order (`findById(...).orElse(null)` guard at `:151`).

### 5. `kafka/OrderConsumer.java` (or a new `kafka/DltListener.java`)
Add the DLT listener:
```java
@KafkaListener(topics = "order.DLT", groupId = "order-dlt")
public void onDead(ConsumerRecord<String, Object> rec,
                   @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String reason) {
    log.error("Order to DLT: key={} reason={}", rec.key(), reason);
    dltCounter.increment(); // Counter.builder("orders.dlt")
}
```

## Idempotency on replay (acceptance criterion — already satisfied)
`executeOrder:60-63` short-circuits on a known `idempotencyKey`, and the key is
saved inside the execution transaction (`:110`). Replaying a DLT record whose
order already `FILLED` increments `duplicateCounter` and returns — no double
execution. Replaying one that never committed re-executes cleanly. No new work;
covered by a regression test below.

## Test plan (all NEW — 0% existing coverage)

Unit (`OrderExecutionServiceTest` / new `KafkaErrorConfigTest`, Mockito):
- Recovery lambda, `event != null` → calls `rejectAndRelease`, then recoverer.
- Recovery lambda, `event == null` (deser failure) → recoverer only, no release.
- **CRITICAL** poison BUY → balance reservation released, order `REJECTED`.
- **CRITICAL** poison SELL → holding `reservedQuantity` released, `REJECTED`.
- DLT listener → `orders.dlt` counter increments, reason logged.
- Idempotency replay → `duplicateCounter`, no re-execution (guards PR #6 regression).

Integration (`OrderDlqIntegrationTest`, `@EmbeddedKafka topics={order, order.DLT}, partitions=3`):
- Transient throw that later succeeds → retried within backoff → order `FILLED`.
- Always-throws → 3 tries exhausted → record on `order.DLT`, order `REJECTED`,
  reservation released, no infinite redelivery.
- Malformed JSON payload → `DeserializationException` → straight to `order.DLT`
  (no backoff, no loop).

## Failure modes
| Codepath | Realistic prod failure | Test? | Error handling? | Visible? |
|---|---|---|---|---|
| Recovery handler | `rejectAndRelease` itself throws (DB down) → record not DLT'd, retried by handler | add test | handler propagates → ret/redeliver | yes (logs) |
| DLT publish | DLT topic missing/under-partitioned → recoverer send fails | covered by mirror-3 decision | send error logged, offset not committed | yes |
| Deser failure | schema drift → null event → escrow NOT released | unit (null branch) | DLT only; flagged for manual replay | **partial** — see TODO |
| Recoverer serializer | raw-bytes record can't be republished by JsonSerializer template | verify in impl | needs ByteArray template | yes (send error) |

Critical gap flagged: **deser-failure poison messages release no escrow** (no
parseable orderId). Rare (we serialize our own events; only schema drift hits
it), but it means a manual-replay runbook is needed — see TODO.

## NOT in scope (deferred, with rationale)
- **P1 per-account ordering** — keying by `userId` is orthogonal; DLQ works
  regardless of partition assignment.
- **Configurable backoff knobs** — hard-coded `2000L, 2L` for P0; externalize
  only if ops asks. Avoids premature config surface.
- **Automated DLT replay tooling** — P0 ships inspection (log + counter) only;
  replay is manual via Kafka console for now.
- **General reservation-leak sweep (P4)** — P0 fixes the leak *at the DLT source*;
  the time-bound sweep for orders stuck *before* reaching execution is still P4.
- **Deser-failure escrow reconciliation** — needs orderId recovery from headers
  or a reconciliation job; deferred as a TODO, not P0.

## What already exists (reused, not rebuilt)
- `rejectAndRelease` (`OrderExecutionService.java:149`) — committed REJECTED +
  reservation release. Reused verbatim by the recovery handler.
- Idempotency guard + save — makes DLT replay safe with zero new code.
- Micrometer counter pattern (`:52-56`) — `orders.dlt` follows it.
- Spring Kafka `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` [Layer 1] —
  framework built-ins; no custom retry loop.

## Parallelization
Sequential implementation, limited parallelization: config (properties + topic),
the error-handler bean, and the DLT listener all converge on the same Kafka
wiring and the `OrderExecutionService` visibility change. One lane. Tests can be
written alongside each unit.

## Implementation tasks
- [x] **T1** — config — Wrapped deserializers in `ErrorHandlingDeserializer`;
  added `order.DLT` (3 partitions). `application.properties`, `KafkaTopicConfig.java`.
- [x] **T2** — kafka — `KafkaErrorConfig` (`DefaultErrorHandler`, FixedBackOff 2s×2)
  + `OrderDltRecoverer` (rejectAndRelease then DLT-publish, null-guarded). Made
  `rejectAndRelease` public.
- [x] **T3** — kafka — `DltListener` logs reason + `orders.dlt` counter.
- [x] **T4** — test — 16 tests green: unit recoverer/listener + 3 `@EmbeddedKafka`
  flows + SELL-reject regression.
- [x] **T5** — resolved — single DLT template with `DelegatingByTypeSerializer`
  routes `OrderEvent`→JSON and `byte[]`→raw, so deser-failure bytes republish cleanly.

## Implementation notes (deviations from the reviewed plan)

Two things the code forced that the plan hadn't anticipated:

1. **Scoped container factory, not a global error handler.** `PriceConsumer`
   (`PriceConsumer.java:25`) also listens on `order`. A global `CommonErrorHandler`
   bean would be auto-applied to it, so a failed *price snapshot* would route to
   `order.DLT` and the recoverer would reject the order + release escrow. The error
   handler is therefore attached to a dedicated `orderKafkaListenerContainerFactory`
   used only by `OrderConsumer`. `PriceConsumer`/`DltListener` keep the default factory.
2. **`PriceConsumer` null guard.** The global `ErrorHandlingDeserializer` change means
   `PriceConsumer` can now receive a `null` event on a malformed payload. Added an
   early-return guard so it skips the snapshot instead of NPE-spamming (the order
   group's handler still dead-letters that message).

New file `OrderDltRecoverer.java` holds the recovery logic as a testable
`ConsumerRecordRecoverer` (kept out of the `@Bean` graph as a `CommonErrorHandler`,
so Boot can't auto-apply it).
```
