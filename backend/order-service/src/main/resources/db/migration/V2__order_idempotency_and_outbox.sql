ALTER TABLE orders ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'INR';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_provider VARCHAR(64);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_transaction_id VARCHAR(128);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_orders_user_id_idempotency_key'
    ) THEN
        ALTER TABLE orders
            ADD CONSTRAINT uk_orders_user_id_idempotency_key UNIQUE (user_id, idempotency_key);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_orders_user_id_created_at ON orders (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS outbox_events (
    id VARCHAR(36) PRIMARY KEY,
    aggregate_type VARCHAR(128) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_outbox_events_due ON outbox_events (published_at, next_attempt_at, created_at);
