-- ============================================================
-- V2: Sample seed data for local development
-- ============================================================

-- Sample products
INSERT INTO products (id, name, description, price, category, status, created_at, updated_at) VALUES
(1000000000001, 'MacBook Pro 16"',   'Apple M3 Pro chip, 18GB RAM, 512GB SSD',   2499.00, 'electronics', 1, NOW(3), NOW(3)),
(1000000000002, 'Sony WH-1000XM5',  'Industry-leading noise cancelling headphones', 349.99, 'electronics', 1, NOW(3), NOW(3)),
(1000000000003, 'Nike Air Max 270',  'Max Air cushioning for all-day comfort',       129.99, 'footwear',    1, NOW(3), NOW(3)),
(1000000000004, 'Flash Sale Widget', 'Limited edition — only 10 units available!',    99.00, 'special',     1, NOW(3), NOW(3));

-- Corresponding inventory
INSERT INTO inventory (id, product_id, total_stock, available_stock, locked_quantity, version) VALUES
(2000000000001, 1000000000001, 500,  500,  0, 0),
(2000000000002, 1000000000002, 1000, 1000, 0, 0),
(2000000000003, 1000000000003, 2000, 2000, 0, 0),
(2000000000004, 1000000000004, 10,   10,   0, 0);  -- flash sale item: only 10 units

-- Sample user (password = "password123" BCrypt hash)
INSERT INTO users (id, username, email, password_hash, phone, status, created_at, updated_at) VALUES
(3000000000001, 'alice', 'alice@example.com',
 '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
 '+1-555-0100', 1, NOW(3), NOW(3));
