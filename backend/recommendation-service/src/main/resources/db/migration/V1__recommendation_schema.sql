CREATE TABLE IF NOT EXISTS product_stats (
    id BIGSERIAL PRIMARY KEY,
    product_id VARCHAR(64) NOT NULL UNIQUE,
    product_name VARCHAR(255) NOT NULL,
    total_quantity BIGINT NOT NULL,
    total_revenue NUMERIC(19, 2) NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_product_stats_product_id ON product_stats (product_id);
