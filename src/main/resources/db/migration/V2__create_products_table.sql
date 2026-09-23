CREATE TABLE products (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name         VARCHAR(150)             NOT NULL,
    description  VARCHAR(2000),
    sku          VARCHAR(64)              NOT NULL,
    price        NUMERIC(12, 2)           NOT NULL,
    active       BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_products_price_positive CHECK (price > 0)
);

CREATE UNIQUE INDEX ux_products_sku ON products (sku);
CREATE INDEX idx_products_active ON products (active);
