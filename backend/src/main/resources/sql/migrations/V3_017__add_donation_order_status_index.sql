CREATE INDEX idx_donation_orders_status_created_at
ON donation_orders (status, created_at);
