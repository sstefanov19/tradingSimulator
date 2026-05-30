# Design Doc: Dead-Letter Queue & Poison-Message Handling

**Status:** Implemented (branch `feature/order-reservation`)
**Date:** 2026-05-30
**Scope:** Kafka order consumer → execution → dead-letter pipeline
**Supersedes:** P0 in `PLAN.md`. Builds on [order-reservation.md](order-reservation.md).
**Build notes:** task-level breakdown in [p0-dlq-implementation.md](p0-dlq-implementation.md).

---

## 1. Context & Problem

Orders execute asynchronously: `OutboxPoller` publishes an `OrderEvent` to the `order`
topic, and `OrderConsumer` calls `OrderExecutionService.executeOrder` off that topic.

The reservation work ([order-reservation.md](order-reservation.md) §5.3) gave **business**
rejections (`InsufficientFundsException` / `InsufficientHoldingsException`) a committed
`REJECTED` terminal state — they are caught in-service and never rethrown. But a **true
poison message** had no resting place:

- A non-business `RuntimeException` (a bug, a permanently-failing dependency) was rethrown
  and relied on Spring Kafka's default `DefaultErrorHandler`, which retries a fixed number
  of times then **logs and drops the offset**. The failure vanishes.
- A **deserialization failure** (schema drift, a malformed payload) threw *inside* the
  consumer's `JsonDeserializer`, before the listener ran. The offset could not advance and
  the consumer **redelivered the same bad record forever** — an infinite poison loop.
- There was no distinction between "retry, this is transient" and "give up, this will never
  succeed", and no terminal, inspectable destination for the latter.

The underlying principle: **every message must reach a terminal resting place — filled,
rejected, or dead-lettered — and a message that can never succeed must not loop forever or
disappear silently.**

## 2. Goals / Non-Goals

**Goals**
- Transient failures retry with bounded backoff, then stop.
- Messages that exhaust retries (and un-retryable failures like deserialization errors) land
  in a dead-letter topic `order.DLT` for inspection/replay — never silently dropped, never
  looping.
- A dead-lettered order releases its admission-time reservation (it is terminal, exactly like
  a business rejection) so poison messages cannot leak escrow.
- Dead-letters are observable (logged + a metric), not silently parked.
- Idempotency still holds if a record is replayed from `order.DLT`.

**Non-Goals**
- Automated DLT replay tooling — P0 ships inspection only; replay is manual.
- Reconciling escrow for **deserialization-failure** dead-letters (no parseable order to
  release — see §6, tracked in `TODOS.md`).
- A general lifetime-bound sweep for orders stuck *before* reaching the consumer (that is the
  separate P4 reservation-leak guard).
- Per-account ordering across partitions (P1), order TTL (P2).

## 3. Key Concepts

- **Transient failure** — a non-business `RuntimeException` thrown during settlement (price
  feed down, DB blip). Retryable: the settlement transaction rolled back, so a retry
  re-executes cleanly.
- **Poison message** — a record that will never succeed: a deserialization failure, or a
  message that throws on every retry. Must be dead-lettered.
- **Dead-letter (DLT)** — moving a terminal failure to `order.DLT` after releasing any escrow,
  so the offset advances and the record is preserved for inspection/replay.
- **Bounded retry** — `FixedBackOff(2s, 2)`: the initial attempt plus 2 retries (~4s) on the
  listener thread, then recover.

## 4. Design

### 4.1 Topology

```
 outbox ─▶ order topic (3 partitions)
                │
   ┌────────────┴─────────────┐
   │                          │
 OrderConsumer            PriceConsumer
 group=order-service      group=price-service
 (orderKafkaListener-     (default factory —
  ContainerFactory)        NO DLT handler)
   │
   ▼
 DefaultErrorHandler  (scoped to OrderConsumer's factory only)
   ├─ FixedBackOff(2s × 2) ── transient retry on listener thread
   └─ OrderDltRecoverer (on exhaustion / non-retryable)
        ├─ value is OrderEvent → rejectAndRelease() then publish
        └─ value is null (deser fail) → publish raw bytes only
                │
                ▼
        order.DLT (3 partitions) ─▶ DltListener: log + orders.dlt counter
```

### 4.2 Deserialization safety — `ErrorHandlingDeserializer`

Both key and value deserializers are wrapped in `ErrorHandlingDeserializer`
(`application.properties`), each delegating to the real deserializer
(`StringDeserializer` / `JsonDeserializer`). A malformed payload now surfaces as a
`DeserializationException` carried in record headers with a `null` value, instead of
throwing inside the deserializer and looping. `DefaultErrorHandler` treats
`DeserializationException` as **non-retryable by default**, so it skips the backoff and goes
straight to the recoverer.

### 4.3 Bounded retry + recovery — `KafkaErrorConfig`, `OrderDltRecoverer`

```
executeOrder outcome:
  business rejection  → caught in-service, REJECTED + escrow released   (never reaches handler)
  transient throw     → rethrown → FixedBackOff(2s × 2) retries
       ├─ later succeeds → FILLED
       └─ exhausted      → OrderDltRecoverer
  deserialization fail → non-retryable → OrderDltRecoverer (no backoff)

OrderDltRecoverer.accept(record, ex):
  if record.value() instanceof OrderEvent ev:
      executionService.rejectAndRelease(ev)   // REJECTED + escrow released (reuses §5.3 path)
  // else: deser failure — null value, nothing to release
  deadLetterPublishingRecoverer.accept(record, ex)   // → order.DLT, same partition number
```

Releasing escrow **before** publishing is the crux: a dead-lettered order is terminal, so it
must return its reservation exactly like a business rejection — otherwise every poison BUY
would lock a user's funds permanently. This reuses `OrderExecutionService.rejectAndRelease`
(made `public`), which runs in its own committed transaction.

### 4.4 DLT publishing — serializer routing

`DeadLetterPublishingRecoverer` republishes to `order.DLT` on the **same partition number** as
the source (so `order.DLT` mirrors `order` at 3 partitions). Its template uses a
`DelegatingByTypeSerializer` routing by value type:
- `OrderEvent` → `JsonSerializer` (retries-exhausted case),
- `byte[]` → `ByteArraySerializer` (deser-failure case, the original raw payload).

A single JSON serializer would have base64-mangled the raw bytes; the delegating serializer
preserves them for faithful inspection.

### 4.5 Observability — `DltListener`

A `@KafkaListener` on `order.DLT` (group `order-dlt`, default factory) logs the failed key +
`DLT_EXCEPTION_MESSAGE` header and increments an `orders.dlt` Micrometer counter (scraped by
Prometheus alongside the existing order metrics). It only logs and counts — it never rethrows,
so a malformed record (null value via `ErrorHandlingDeserializer`) cannot loop back into the DLT.

## 5. Why the error handler is scoped, not global

`PriceConsumer` (`PriceConsumer.java:25`) **also** listens on the `order` topic
(group `price-service`) to snapshot prices. Spring Boot auto-applies a lone
`CommonErrorHandler` bean to *every* listener container. A global handler would therefore
route a failed *price snapshot* to `order.DLT` and the recoverer would reject the order and
release its escrow — **corrupting an order because an unrelated side-effect failed.**

So the `DefaultErrorHandler` is attached to a dedicated
`orderKafkaListenerContainerFactory` used only by `OrderConsumer`. `PriceConsumer` and
`DltListener` keep the default factory. `OrderDltRecoverer` is a `ConsumerRecordRecoverer`
(not a `CommonErrorHandler`), so Boot's auto-application never picks it up either.

Side effect handled: the global `ErrorHandlingDeserializer` means `PriceConsumer` can now
receive a `null` event on a malformed payload, so it gained an early-return guard (the order
group's handler still dead-letters that message).

## 6. Correctness

- **Idempotency on replay (already satisfied).** `executeOrder` short-circuits on a known
  `idempotencyKey` and stamps it only inside the successful settlement transaction. Replaying
  a DLT record whose order already `FILLED` increments `duplicateCounter` and returns;
  replaying one that never committed re-executes cleanly. No new code.
- **No double-release.** Recovery fires once per record. `rejectAndRelease` guards on a
  missing order (`findById(...).orElse(null)`).
- **Deserialization-failure escrow gap.** A deser-failure dead-letter has no parseable
  `orderId`, so its reservation is **not** released. Rare (we serialize our own events; only
  schema drift hits it). It is logged and dead-lettered for manual replay; full reconciliation
  is deferred (`TODOS.md`).
- **Partition stall is bounded.** `DefaultErrorHandler` retries block the listener thread.
  `FixedBackOff(2s × 2)` caps the worst-case stall at ~4s per poison message.

## 7. Alternatives Considered

- **Global `DefaultErrorHandler` bean.** Simpler wiring, but auto-applied to `PriceConsumer`,
  which cross-contaminates `order.DLT` and rejects orders on price-snapshot failures (§5).
  Rejected for a scoped container factory.
- **Single JSON template for the DLT.** Would not throw on `byte[]`, but base64-encodes the
  raw deser-failure payload, mangling it for inspection. Rejected for `DelegatingByTypeSerializer`.
- **Externalized backoff config** (`app.kafka.retry.*`). Deferred: `FixedBackOff(2s, 2)` is
  hard-coded for P0; externalize only if ops needs runtime tuning. Avoids premature config surface.
- **Release escrow in P4's sweep instead of here.** Would ship a known fund-leak on every
  poison message until P4 exists. Rejected — P0 closes the leak at its source.

## 8. Rollout

No schema change. `order.DLT` is declared as a `NewTopic` bean (auto-created on boot by the
admin client, matching `order`'s 3 partitions). The `ErrorHandlingDeserializer` change is in
`application.properties`. Roll forward only; rollback = redeploy the prior build (the DLT
topic is harmless if unused). New dependency: `spring-kafka-test` (test scope only).

## 9. Testing

16 tests, no external infra (embedded broker only). Run:
`./mvnw test -Dtest=OrderDltRecovererTest,DltListenerTest,OrderExecutionServiceTest,OrderDlqIntegrationTest`

- **`OrderDltRecovererTest`** (Mockito): a parseable order releases escrow **then** publishes
  (verified in order); a deser failure (null value) publishes **without** releasing escrow.
- **`OrderExecutionServiceTest`** (extended): SELL business-rejection releases reserved
  quantity + marks `REJECTED` — the same release path the DLT recovery uses for sells.
- **`DltListenerTest`**: `orders.dlt` counter increments per dead-letter.
- **`OrderDlqIntegrationTest`** (`@EmbeddedKafka`, 3 partitions): a transient failure that
  later succeeds **fills without** dead-lettering; an always-throwing message **exhausts
  retries → `order.DLT`** and the order's escrow is released; a malformed payload goes
  **straight to `order.DLT`** (no loop) **without** releasing escrow.

> The integration test builds the container wiring directly against the embedded broker (no
> `@SpringBootTest`, so no Postgres/Redis needed). It faithfully replicates the `@KafkaListener`
> adapter's behaviour of rethrowing a deserialization-exception header, which a raw
> `MessageListener` does not do automatically.

## 10. Future Work

- **Deser-failure escrow reconciliation** — recover the `orderId` from headers (or a
  reconciliation job) so deserialization dead-letters also release escrow.
- **Automated DLT replay** — tooling/runbook to replay `order.DLT` back into `order` once a
  root cause is fixed (idempotency already makes replay safe).
- **Externalized retry policy** — make backoff interval/attempts tunable without a redeploy.
- **Per-account ordering (P1), order TTL (P2), reservation-leak sweep (P4)** — see `PLAN.md`.
