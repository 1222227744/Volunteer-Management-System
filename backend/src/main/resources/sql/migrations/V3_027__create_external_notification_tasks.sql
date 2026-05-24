CREATE TABLE IF NOT EXISTS external_notification_tasks (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  channel VARCHAR(20) NOT NULL,
  title VARCHAR(120) NOT NULL,
  content VARCHAR(1000) NOT NULL,
  recipient VARCHAR(200) NULL,
  status VARCHAR(20) NOT NULL,
  retry_count INT NOT NULL,
  max_retries INT NOT NULL,
  last_error VARCHAR(1000) NULL,
  created_at DATETIME NOT NULL,
  last_tried_at DATETIME NULL,
  sent_at DATETIME NULL
);
