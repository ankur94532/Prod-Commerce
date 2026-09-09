ALTER TABLE orders ADD COLUMN workflow_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN recovery_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN next_recovery_at TIMESTAMPTZ;
CREATE INDEX idx_orders_recovery ON orders (status, next_recovery_at, updated_at)
    WHERE workflow_version = 1 AND status IN ('PENDING_PAYMENT', 'COMPENSATING');
