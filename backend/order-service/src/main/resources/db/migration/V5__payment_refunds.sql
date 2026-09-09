-- Records the provider's refund identifier so a retried compensation can tell a refund it
-- already issued from one it still owes. Without it, a repeated recovery pass would ask the
-- provider to refund again on every attempt.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_refund_id VARCHAR(128);

-- Recovery scans orders by state; this keeps that scan off a sequential read as the table grows.
CREATE INDEX IF NOT EXISTS idx_orders_recovery_state
    ON orders (status, updated_at)
    WHERE status IN ('PENDING_PAYMENT', 'COMPENSATING');
