CREATE INDEX idx_notifications_user_read_created_at
ON notifications (user_id, read_flag, created_at);
