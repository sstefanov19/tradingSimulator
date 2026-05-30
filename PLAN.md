# Engineering Plan — Outstanding Architectural Issues

Backlog of architectural edge cases identified during review of the order pipeline
(`OrderService` → outbox → Kafka → `OrderExecutionService`). Ordered by priority.

> ✅ **Done:** Fund & holding reservation / admission-time invariant enforcement —
> shipped in PR #6 (`feature/order-reservation`). See `docs/design/order-reservation.md`.

---

## P0 — Dead-letter queue & poison-message handling

**Problem.** `OrderConsumer` calls `executeOrder` with no configured error handler or DLQ.
The reservation work gave business rejections a committed `REJECTED` terminal state, but a
*true poison message* (e.g. deserialization failure, a permanently failing dependency, a
non-business `RuntimeException`) is rethrown and relies on Spring Kafka's default
`DefaultErrorHandler` — which retries a fixed number of times then logs and drops the
offset. There is no terminal resting place and no distinction between "retry forever" and
"give up after N".

**Goal.** Transient failures retry with backoff; messages that exhaust retries land in a
dead-letter topic for inspection/replay instead of being silently dropped or looping.

**Approach.**
- Configure a `DefaultErrorHandler` with a `FixedBackOff` (bounded attempts) +
  `DeadLetterPublishingRecoverer` publishing to `order.DLT`.
- Classify exceptions: `InsufficientFunds/HoldingsException` are already terminal (handled
  in-service, not rethrown); make the error handler treat unexpected exceptions as
  retryable, then DLT.
- Add a topic `order.DLT` in `KafkaTopicConfig`; add a consumer or alert on it.
- Ensure idempotency still holds on replay from the DLT.

**Files.** `kafka/OrderConsumer.java`, `kafka/KafkaTopicConfig.java`, a Kafka error-handler
`@Bean` (likely a new `kafka/KafkaErrorConfig.java`).

**Acceptance.** A message that always throws a non-business exception ends in `order.DLT`
after the configured attempts; a transient failure that later succeeds is retried and fills;
no infinite redelivery loop.

---

## P1 — Per-account ordering across Kafka partitions

**Problem.** The outbox publishes with the Kafka key = `idempotencyKey`
(`OutboxPoller.poll`), not `userId`. Events for the same user therefore spread across
partitions and are consumed concurrently. Correctness is currently saved by pessimistic row
locks, but **ordering** is not guaranteed — e.g. a SELL can be processed before an earlier
BUY that funds it, producing avoidable rejections, and metrics/audit ordering is
non-deterministic.

**Goal.** Preserve per-account (and ideally per-account+ticker) processing order without
losing parallelism across accounts.

**Approach.**
- Key Kafka records by `userId` (or `userId:ticker`) so all of an account's events hash to
  one partition and are processed in order.
- Confirm consumer concurrency ≤ partition count and that this doesn't starve throughput;
  document the trade-off.

**Files.** `kafka/OutboxPoller.java` (the `kafkaTemplate.send` key), `kafka/KafkaTopicConfig.java`
(partition count), consumer concurrency config.

**Acceptance.** Interleaved BUY-then-SELL for one user always execute in submission order;
throughput across many users is unaffected.

---

## P2 — Order TTL / price staleness

**Problem.** A `MARKET` order that sits `PENDING` (Kafka backlog, retries, reaper requeue)
executes at whatever the price is *whenever* it runs — possibly far from the price when the
user clicked. There is no expiry.

**Goal.** Orders that can't execute within a freshness window are rejected (and reservation
released) rather than filled at a stale price.

**Approach.**
- Add a `maxAge`/TTL; at execution, if `now - order.timestamp > TTL`, reject + release via
  the existing `rejectAndRelease` path.
- Make TTL configurable; default conservative (e.g. 30–60s for MARKET).

**Files.** `service/OrderExecutionService.java`, config.

**Acceptance.** An event delivered after the TTL is rejected with a clear reason and its
reservation is released; within-TTL events fill normally.

---

## P3 — Surface reserved/available balance in read APIs

**Problem.** Clients can't see spendable funds — only raw `balance`, which now diverges from
what they can actually commit (`balance − reserved`). Same for holdings
(`quantity − reservedQuantity`).

**Goal.** Read endpoints expose available vs. reserved so UIs reflect escrow.

**Approach.**
- Add `reserved` / `availableBalance` to the balance/user read response and
  `reservedQuantity` / `availableQuantity` to `HoldingResponse`.

**Files.** `controller/UserController.java`, `controller/HoldingController.java`,
`dto/HoldingResponse.java`, relevant service reads.

**Acceptance.** Balance and holdings reads return both committed and available figures.

---

## P4 — Reservation leak guard (orphaned escrow)

**Problem.** If an order's outbox row never progresses to execution (permanent publish
failure beyond the reaper's reach, or a bug), its reservation is held indefinitely. The
`OutboxPoller` reaper retries `PROCESSING` rows but does not bound total lifetime.

**Goal.** No reservation is held forever for an order that will never execute.

**Approach.**
- Bound order/outbox lifetime; a sweep that marks long-stuck orders `REJECTED` and releases
  their reservation (reuse `rejectAndRelease` semantics). Coordinate with P2 (TTL) — they may
  share machinery.

**Files.** `kafka/OutboxPoller.java` (or a dedicated scheduled sweep), `service/OrderExecutionService.java`.

**Acceptance.** An order stuck past its bound is rejected and its escrow returned; metric/log
emitted.

---

## Cross-cutting test debt

- `OrderControllerIntegrationTest` asserts `status().isOk()` (200) while the controller
  returns `202 Accepted`, and posts a BUY for a non-existent user — it was red before the
  reservation change. Fix the assertion and add an end-to-end over-commit case
  (two $100 BUYs → `202` then `422`) once test infra (Postgres/Kafka/Redis + `reservation.sql`)
  is wired, ideally via Testcontainers like `PriceTickerServiceTest`.

---

## GSTACK REVIEW REPORT

P0 implementation plan reviewed via `/plan-eng-review` (2026-05-30). Full plan:
`docs/design/p0-dlq-implementation.md`.

| Review | Trigger | Why | Runs | Status | Findings |
|--------|---------|-----|------|--------|----------|
| CEO Review | `/plan-ceo-review` | Scope & strategy | 0 | — | — |
| Codex Review | `/codex review` | Independent 2nd opinion | 0 | — | — |
| Eng Review | `/plan-eng-review` | Architecture & tests (required) | 1 | ISSUES_OPEN | 6 issues, 1 critical gap |
| Design Review | `/plan-design-review` | UI/UX gaps | 0 | — | — |
| DX Review | `/plan-devex-review` | Developer experience gaps | 0 | — | — |

- **CRITICAL GAP:** DLT recovery without escrow release leaks reservations →
  resolved in-plan (decision 5: `rejectAndRelease` on DLT recovery).
- **UNRESOLVED:** 0 — all 6 findings decided by user.
- **VERDICT:** ENG CLEARED — plan is implementation-ready. Outside voice skipped.

