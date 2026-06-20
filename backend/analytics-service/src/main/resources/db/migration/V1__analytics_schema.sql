CREATE TABLE IF NOT EXISTS analytics_summary (
    id BIGINT PRIMARY KEY,
    total_orders BIGINT NOT NULL,
    total_revenue NUMERIC(19, 2) NOT NULL
);

INSERT INTO analytics_summary (id, total_orders, total_revenue)
VALUES (1, 0, 0)
ON CONFLICT (id) DO NOTHING;
