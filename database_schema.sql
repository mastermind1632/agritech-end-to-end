-- AgriFinanceAPIs schema for the current Java project.
-- IDs are VARCHAR(36) UUIDs so they match Farmer.java and Finance entities.
-- The application also uses spring.jpa.hibernate.ddl-auto=update.

CREATE TABLE IF NOT EXISTS farmers (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    location VARCHAR(150),
    contact VARCHAR(100) UNIQUE,
    password_hash VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS suppliers (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    location VARCHAR(150),
    contact VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS supplier_products (
    id VARCHAR(36) PRIMARY KEY,
    supplier_id VARCHAR(36) NOT NULL REFERENCES suppliers(id) ON DELETE CASCADE,
    product_name VARCHAR(150) NOT NULL,
    price DECIMAL(12,2) NOT NULL CHECK (price >= 0)
);

CREATE TABLE IF NOT EXISTS expenses (
    id BIGSERIAL PRIMARY KEY,
    farmer_id VARCHAR(36) NOT NULL REFERENCES farmers(id) ON DELETE CASCADE,
    item VARCHAR(150) NOT NULL,
    category VARCHAR(100),
    amount DECIMAL(12,2) NOT NULL CHECK (amount >= 0),
    date DATE NOT NULL DEFAULT CURRENT_DATE
);

CREATE TABLE IF NOT EXISTS income (
    id BIGSERIAL PRIMARY KEY,
    farmer_id VARCHAR(36) NOT NULL REFERENCES farmers(id) ON DELETE CASCADE,
    item VARCHAR(150) NOT NULL,
    amount DECIMAL(12,2) NOT NULL CHECK (amount >= 0),
    date DATE NOT NULL DEFAULT CURRENT_DATE
);

CREATE TABLE IF NOT EXISTS group_orders (
    id VARCHAR(36) PRIMARY KEY,
    product_id VARCHAR(36) NOT NULL REFERENCES supplier_products(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL DEFAULT 'open',
    target_quantity INTEGER NOT NULL CHECK (target_quantity > 0),
    current_quantity INTEGER NOT NULL DEFAULT 0 CHECK (current_quantity >= 0),
    discount_rate DECIMAL(5,2) DEFAULT 0 CHECK (discount_rate >= 0 AND discount_rate <= 100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS group_order_items (
    id VARCHAR(36) PRIMARY KEY,
    group_order_id VARCHAR(36) NOT NULL REFERENCES group_orders(id) ON DELETE CASCADE,
    farmer_id VARCHAR(36) NOT NULL REFERENCES farmers(id) ON DELETE CASCADE,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    total_price DECIMAL(12,2) NOT NULL CHECK (total_price >= 0),
    joined_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ai_recommendations (
    id VARCHAR(36) PRIMARY KEY,
    farmer_id VARCHAR(36) NOT NULL REFERENCES farmers(id) ON DELETE CASCADE,
    recommended_product_id VARCHAR(36) REFERENCES supplier_products(id) ON DELETE SET NULL,
    suggested_group_order_id VARCHAR(36) REFERENCES group_orders(id) ON DELETE SET NULL,
    reason TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS notifications (
    id VARCHAR(36) PRIMARY KEY,
    supplier_id VARCHAR(36) NOT NULL REFERENCES suppliers(id) ON DELETE CASCADE,
    group_order_id VARCHAR(36) REFERENCES group_orders(id) ON DELETE CASCADE,
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_supplier_products_supplier_id ON supplier_products(supplier_id);
CREATE INDEX IF NOT EXISTS idx_expenses_farmer_id ON expenses(farmer_id);
CREATE INDEX IF NOT EXISTS idx_income_farmer_id ON income(farmer_id);
CREATE INDEX IF NOT EXISTS idx_group_orders_product_id ON group_orders(product_id);
CREATE INDEX IF NOT EXISTS idx_group_order_items_order_id ON group_order_items(group_order_id);
CREATE INDEX IF NOT EXISTS idx_group_order_items_farmer_id ON group_order_items(farmer_id);
CREATE INDEX IF NOT EXISTS idx_ai_recommendations_farmer ON ai_recommendations(farmer_id);
CREATE INDEX IF NOT EXISTS idx_notifications_supplier ON notifications(supplier_id);
