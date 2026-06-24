CREATE TABLE inventory_items (
    product_id VARCHAR(50) PRIMARY KEY,
    quantity_available INT NOT NULL
);

INSERT INTO inventory_items (product_id, quantity_available) VALUES
('p1', 100),
('p2', 50);
