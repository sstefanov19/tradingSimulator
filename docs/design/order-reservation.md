# Design Doc: Fund & Holding Reservation (Admission-Time Invariant Enforcement)

**Status:** Implemented (branch `feature/order-reservation`)
**Date:** 2026-05-29
**Scope:** Order placement → outbox → Kafka → execution pipeline

---

## 1. Context & Problem

Order placement is asynchronous: `OrderService.publishOrder` persists a `PENDING`
order plus an outbox row and returns `202 Accepted`; the order is later executed by
`OrderExecutionService` off a Kafka topic.

Before this change, **affordability was checked only at execution**, guarded by
pessimistic row locks on `users` and `holdings`. Admission accepted every order
unconditionally. Consequences:

- **Over-commit.** A user with $100 could submit 50 BUY orders for $100 each. All 50
  were accepted and acknowledged; 49 then failed at settlement. The write side
  acknowledged commands it could never honor.
- **Silent, futile failure.** On execution error the code set `status = "FAILED"` and
  rethrew inside the same `@Transactional` method — so the `FAILED` write was rolled
  back by its own rethrow. The order stayed `PENDING`, and there was no terminal,
  client-visible rejection state.

The underlying principle: **invariants must be enforced at the command-acceptance
boundary, and every command must reach a terminal state (filled or rejected).**

## 2. Goals / Non-Goals

**Goals**
- Reject un-affordable orders synchronously at admission (HTTP `422`), before they enter
  the pipeline.
- Guarantee balance and holdings can never go negative, even with many concurrent
  in-flight orders for the same account.
- Give failed orders a committed, client-visible `REJECTED` terminal state and never leak
  or double-charge reserved resources.

**Non-Goals**
- Full dead-letter-queue / poison-message handling for the Kafka consumer (tracked
  separately — see §9).
- Limit orders, partial fills, or price-tolerance semantics. Orders remain `MARKET`.
- Exposing reserved/available balance in read APIs (optional follow-up).

## 3. Key Concepts

- **Available balance** = `balance − reserved`. Cash spendable by *new* BUYs.
- **Available quantity** = `quantity − reservedQuantity`. Units sellable by *new* SELLs.
- **Reserve** (admission): move resources into escrow so concurrent orders see them as
  unavailable.
- **Settle** (fill): consume the reservation — debit actual cost / decrement units.
- **Release** (rejection): return the reservation untouched.

## 4. Data Model

| Table | New column | Type | Meaning |
|-------|-----------|------|---------|
| `users` | `reserved` | `NUMERIC NOT NULL DEFAULT 0` | cash escrowed across pending BUYs |
| `holdings` | `reserved_quantity` | `NUMERIC NOT NULL DEFAULT 0` | units escrowed across pending SELLs |
| `orders` | `reserved_amount` | `NUMERIC` (null for SELL) | cash reserved for this BUY, enabling exact settle/release |

`reserved_amount` is stored per-order so settlement/release frees *exactly* what was
reserved, regardless of price drift.

## 5. Design

### 5.1 Admission — `OrderService.publishOrder` (inside existing `@Transactional`)

After the idempotency check:

- **BUY:** lock the user row (`findByIdWithLock`); compute
  `cost = getPrice(ticker).price() × quantity`. If `balance − reserved < cost` →
  throw `InsufficientFundsException`. Else `reserved += cost` and stamp
  `order.reservedAmount = cost`.
- **SELL:** lock the holding (`findByUserIdAndTicker`, `PESSIMISTIC_WRITE`). If absent or
  `quantity − reservedQuantity < qty` → throw `InsufficientHoldingsException`. Else
  `reservedQuantity += qty`.

The order, outbox `UserTransaction`, and outbox-pending event are then written exactly as
before. Row locks make concurrent admissions serialize, so the available-balance check is
race-free.

### 5.2 Settlement — `OrderExecutionService`

Execution now runs the success path inside an explicit `TransactionTemplate` (matching the
pattern already used in `OutboxPoller`) rather than a method-level `@Transactional`, so the
commit/rollback boundary is unambiguous when a failure must be reacted to.

- **BUY fill:** `UserService.settleBuy(userId, actualCost, reservedAmount)` debits the
  actual cost and frees the reserved amount atomically under lock. If price rose past the
  reservation and `balance − actualCost < 0`, it throws — the backstop.
- **SELL fill:** decrement both `quantity` and `reservedQuantity` by the sold amount; credit
  proceeds. The holding is deleted only when *both* reach zero.

### 5.3 Failure handling

```
executeOrder:
  doExecute() in TransactionTemplate   ← settlement; rolls back on any throw
  ├─ InsufficientFunds/Holdings (business rejection):
  │     rejectAndRelease() in a SEPARATE committed tx:
  │       release reservation + set status = REJECTED
  │     swallow  → Kafka offset advances (terminal)
  └─ any other exception (transient, e.g. price feed down):
        reservation left intact, order left PENDING
        rethrow → Kafka redelivers → retry settles cleanly
```

The committed reject/release is the crux: it survives the rolled-back settlement
transaction, so a rejected order's escrow is always returned and its terminal state
persists. Distinguishing business rejection (terminal) from transient failure (retry)
prevents both infinite poison loops and double-release on redelivery.

### 5.4 API

New `InsufficientFundsException` / `InsufficientHoldingsException` are mapped to HTTP
`422 Unprocessable Entity` (RFC 7807 `ProblemDetail`) by a new `@RestControllerAdvice`
(`ApiExceptionHandler`). Successful placement still returns `202 Accepted`.

## 6. Concurrency & Correctness

- All balance/holding mutations occur under `PESSIMISTIC_WRITE` locks, so read-modify-write
  on `reserved` / `reservedQuantity` is serialized per account/holding.
- Idempotency is unchanged: the dedupe key is recorded **only on successful fill**, so a
  rejected order is never marked done, and a redelivered transient failure re-executes.
- Reservation invariants: `0 ≤ reserved`, `0 ≤ reservedQuantity ≤ quantity` hold because
  every reserve is matched by exactly one settle or one release.

## 7. Alternatives Considered

- **Compute available balance on the fly** (`balance − SUM(pending BUY costs)`) instead of a
  stored `reserved` column. Rejected: extra query per admission, racier, and inconsistent
  with the existing locked-row mutation style.
- **Reserve with a price buffer** (e.g. `cost × 1.02`) to absorb upward drift. Rejected for
  now in favor of the simpler reserve-at-current-price + negative-balance backstop; a buffer
  can be layered on later if rejection-at-settlement proves common.
- **Introduce Flyway** for the schema change. Deferred: the project currently has no
  migration framework, so a checked-in SQL script matches existing conventions with the
  least new surface.

## 8. Schema Migration & Rollout

The app runs with `spring.jpa.hibernate.ddl-auto=validate`, so the columns must exist before
boot. Apply once before deploying:

```bash
psql "$DATABASE_URL" -f src/main/resources/db/reservation.sql
```

The script uses `ADD COLUMN IF NOT EXISTS ... DEFAULT 0`, which backfills existing rows and
is safe to re-run. Roll forward only (no destructive changes); rollback = redeploy the prior
build (the unused columns are harmless).

## 9. Testing

- **`OrderServiceTest`** (Mockito): BUY/SELL reserve correctly; reject when
  `available < required`; duplicate key is a no-op (no double reserve); `reservedAmount` is
  stamped on BUY and null on SELL.
- **`OrderExecutionServiceTest`** (Mockito, real `TransactionTemplate` over a stub manager):
  BUY fill settles + frees the exact reservation; business rejection releases + marks
  `REJECTED` and does **not** record idempotency; SELL fill decrements reserved quantity;
  transient failure rethrows and keeps the reservation.

Run: `./mvnw test -Dtest=OrderServiceTest,OrderExecutionServiceTest` (9 tests, no infra).

> Integration tests (`@SpringBootTest`) require live Postgres/Kafka/Redis **and**
> `reservation.sql` applied; an end-to-end "two $100 BUYs → `202` then `422`" case belongs
> there. Note `OrderControllerIntegrationTest` was already inconsistent (asserts `200` vs the
> controller's `202`) prior to this change.

## 10. Future Work

- **Dead-letter queue / retry policy** for the consumer: cap retries on transient failures
  and route true poison messages to `order.DLT` instead of relying on default redelivery.
- Expose `availableBalance = balance − reserved` (and available quantity) in read APIs.
- Optional reservation buffer for MARKET BUYs to reduce settlement-time rejections.
- Reaper for reservations whose orders never execute (today the outbox reaper retries
  indefinitely, which holds the reservation — acceptable but unbounded).
