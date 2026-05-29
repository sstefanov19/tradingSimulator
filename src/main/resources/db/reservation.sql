-- Reservation / escrow columns for admission-time invariant enforcement.
-- Must be applied before deploying the feature/order-reservation branch:
-- spring.jpa.hibernate.ddl-auto=validate will fail to boot otherwise.
--
-- Apply against the Postgres instance, e.g.:
--   psql "$DATABASE_URL" -f src/main/resources/db/reservation.sql

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS reserved NUMERIC NOT NULL DEFAULT 0;

ALTER TABLE holdings
    ADD COLUMN IF NOT EXISTS reserved_quantity NUMERIC NOT NULL DEFAULT 0;

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS reserved_amount NUMERIC;
