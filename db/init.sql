CREATE TABLE IF NOT EXISTS orders (
    id VARCHAR(50) PRIMARY KEY,
    customer_name VARCHAR(100),
    product VARCHAR(100),
    quantity INT,
    status VARCHAR(30)
);

INSERT INTO orders (id, customer_name, product, quantity, status)
VALUES ('ORD-1001', 'Rahul', 'Laptop', 1, 'CREATED')
ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS inventory (
    product VARCHAR(100) PRIMARY KEY,
    available_quantity INT NOT NULL,
    status VARCHAR(30) NOT NULL
);

INSERT INTO inventory (product, available_quantity, status)
VALUES ('Laptop', 10, 'AVAILABLE')
ON CONFLICT (product) DO NOTHING;
