CREATE TABLE stock (
    product_id         UUID PRIMARY KEY,
    total_quantity     INTEGER NOT NULL CHECK (total_quantity >= 0),
    reserved_quantity  INTEGER NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_stock_reserved_le_total CHECK (reserved_quantity <= total_quantity)
);