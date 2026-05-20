CREATE INDEX idx_service_records_user_created_at
ON service_records (user_id, created_at);
