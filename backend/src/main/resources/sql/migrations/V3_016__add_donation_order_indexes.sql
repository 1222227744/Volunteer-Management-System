CREATE INDEX idx_donation_orders_user_created_at
ON donation_orders (user_id, created_at);
