CREATE INDEX idx_file_assets_uploader_created_at
ON file_assets (uploader_id, created_at);
