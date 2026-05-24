CREATE TABLE IF NOT EXISTS activity_attendance_corrections (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  activity_id BIGINT NOT NULL,
  registration_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  action VARCHAR(30) NOT NULL,
  before_status VARCHAR(20) NOT NULL,
  after_status VARCHAR(20) NOT NULL,
  before_check_in_at DATETIME NULL,
  after_check_in_at DATETIME NULL,
  before_check_out_at DATETIME NULL,
  after_check_out_at DATETIME NULL,
  reason VARCHAR(500) NOT NULL,
  corrected_by BIGINT NOT NULL,
  corrected_by_name VARCHAR(50) NOT NULL,
  corrected_at DATETIME NOT NULL
);
