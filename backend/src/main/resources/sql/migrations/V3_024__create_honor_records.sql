CREATE TABLE IF NOT EXISTS honor_records (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  honor_type VARCHAR(40) NOT NULL,
  title VARCHAR(120) NOT NULL,
  reason VARCHAR(1000) NOT NULL,
  showcase_text VARCHAR(2000) NULL,
  related_activity_id BIGINT NULL,
  points_awarded INT NOT NULL,
  awarded_by BIGINT NOT NULL,
  awarded_at DATETIME NOT NULL,
  public_visible BIT(1) NOT NULL
);
