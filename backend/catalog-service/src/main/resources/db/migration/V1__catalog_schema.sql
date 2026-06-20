CREATE TABLE IF NOT EXISTS products (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    category_slug VARCHAR(255),
    brand VARCHAR(255),
    stock_quantity INTEGER,
    active BOOLEAN NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_products_active ON products (active);
CREATE INDEX IF NOT EXISTS idx_products_category_active ON products (category_slug, active);

CREATE TABLE IF NOT EXISTS product_images (
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    image_url VARCHAR(1024)
);

CREATE INDEX IF NOT EXISTS idx_product_images_product_id ON product_images (product_id);

CREATE TABLE IF NOT EXISTS product_attributes (
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    attr_key VARCHAR(255) NOT NULL,
    attr_value VARCHAR(1024),
    PRIMARY KEY (product_id, attr_key)
);
