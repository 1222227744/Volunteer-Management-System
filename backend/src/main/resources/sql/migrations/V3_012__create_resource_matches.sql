CREATE TABLE IF NOT EXISTS resource_matches (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  resource_id BIGINT NOT NULL,
  need_id BIGINT NOT NULL,
  allocated_quantity INT NOT NULL,
  progress_note VARCHAR(500) NULL,
  status VARCHAR(20) NOT NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NULL
);
