CREATE TABLE inventory (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id          BIGINT                   NOT NULL,
    available_quantity  INTEGER                  NOT NULL DEFAULT 0,
    reserved_quantity   INTEGER                  NOT NULL DEFAULT 0,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    -- Última línea de defensa contra el overselling: la BD rechaza cualquier stock negativo
    CONSTRAINT ck_inventory_available_non_negative CHECK (available_quantity >= 0),
    CONSTRAINT ck_inventory_reserved_non_negative CHECK (reserved_quantity >= 0)
);

-- Relación 1:1 con products
CREATE UNIQUE INDEX ux_inventory_product_id ON inventory (product_id);
