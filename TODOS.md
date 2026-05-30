# TODOS

## Deser-failure escrow reconciliation
- **What:** When a poison message fails *deserialization*, the DLT recovery
  handler has no parseable `OrderEvent`, so it cannot release the order's
  reservation. The escrow leaks for that order.
- **Why:** Closes the one remaining fund-leak path in P0's DLQ design.
- **Pros:** Full escrow-leak coverage; no manual intervention.
- **Cons:** Needs orderId recovery from Kafka headers or a reconciliation job;
  non-trivial for a rare case (we serialize our own events — only schema drift hits it).
- **Context:** See `docs/design/p0-dlq-implementation.md` → Failure modes table.
  P0 ships a null-guard + log so these are at least visible.
- **Depends on / blocked by:** P0 DLQ landing first.

## Automated DLT replay tooling / runbook
- **What:** A documented (or scripted) way to replay records from `order.DLT`
  back into `order` after the root cause is fixed.
- **Why:** P0 only makes dead-lettered messages *inspectable*; recovery is manual.
- **Pros:** Operable recovery story; idempotency already makes replay safe.
- **Cons:** Tooling effort; not needed until the first real DLT incident.
- **Context:** Idempotency guard already protects replay (`OrderExecutionService:60`).

## Cross-cutting test debt (from PLAN.md)
- **What:** `OrderControllerIntegrationTest` asserts 200 while the controller
  returns 202, and posts a BUY for a non-existent user. Fix the assertion and add
  an end-to-end over-commit case (two $100 BUYs → 202 then 422).
- **Why:** Test is red and not protecting the reservation invariant.
- **Context:** Wire via Testcontainers like `PriceTickerServiceTest`. Pre-existing,
  not introduced by P0.
