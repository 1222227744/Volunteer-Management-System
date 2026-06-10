CREATE TABLE IF NOT EXISTS file_assets (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  original_name VARCHAR(255) NOT NULL,
  stored_name VARCHAR(255) NOT NULL,
  storage_path VARCHAR(1000) NOT NULL,
  content_type VARCHAR(120) NOT NULL,
  file_size BIGINT NOT NULL,
  category VARCHAR(20) NOT NULL,
  business_type VARCHAR(60) NULL,
  business_id BIGINT NULL,
  uploader_id BIGINT NOT NULL,
  uploader_name VARCHAR(50) NOT NULL,
  ip_address VARCHAR(120) NOT NULL,
  created_at DATETIME NOT NULL
);
