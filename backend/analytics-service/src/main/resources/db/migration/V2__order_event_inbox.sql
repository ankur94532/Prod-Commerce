CREATE TABLE processed_order_events (
    order_id VARCHAR(255) PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Scoped to order.created: one logical event per order, retained across offset replays.
