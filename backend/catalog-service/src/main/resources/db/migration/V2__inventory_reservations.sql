CREATE TABLE inventory_reservations (
    reservation_id VARCHAR(128) PRIMARY KEY,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    state VARCHAR(16) NOT NULL CHECK (state IN ('NEW', 'RESERVED', 'RELEASED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Keep released rows: they fence delayed requests and must not be TTL-deleted.
