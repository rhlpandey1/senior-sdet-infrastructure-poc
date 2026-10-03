CREATE TABLE orders (
    id VARCHAR(50) PRIMARY KEY,
    customer_name VARCHAR(100),
    product VARCHAR(100),
    quantity INT,
    status VARCHAR(30)
);

INSERT INTO orders (id, customer_name, product, quantity, status)
VALUES ('ORD-1001', 'Rahul', 'Laptop', 1, 'CREATED');
