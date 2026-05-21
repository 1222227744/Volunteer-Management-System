CREATE DATABASE IF NOT EXISTS volunteer_service
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE volunteer_service;

CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL UNIQUE,
  password VARCHAR(100) NOT NULL,
  display_name VARCHAR(50) NOT NULL,
  role VARCHAR(20) NOT NULL,
  points INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS activities (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(120) NOT NULL,
  description VARCHAR(1000) NOT NULL,
  location VARCHAR(200) NOT NULL,
  start_time DATETIME NOT NULL,
  end_time DATETIME NOT NULL,
  max_participants INT NOT NULL,
  status VARCHAR(20) NOT NULL,
  organizer_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS activity_registrations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  activity_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL,
  registered_at DATETIME NOT NULL,
  check_in_at DATETIME NULL,
  check_out_at DATETIME NULL,
  CONSTRAINT uk_registration_activity_user UNIQUE (activity_id, user_id)
);

CREATE TABLE IF NOT EXISTS service_records (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  activity_id BIGINT NOT NULL,
  hours DECIMAL(6,2) NOT NULL,
  achievement VARCHAR(1000) NOT NULL,
  evidence_url VARCHAR(500) NULL,
  created_at DATETIME NOT NULL,
  CONSTRAINT uk_service_record_activity_user UNIQUE (activity_id, user_id)
);

CREATE TABLE IF NOT EXISTS activity_feedbacks (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  activity_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  rating INT NOT NULL,
  comment VARCHAR(1000) NOT NULL,
  created_at DATETIME NOT NULL,
  CONSTRAINT uk_activity_feedback_activity_user UNIQUE (activity_id, user_id)
);

CREATE TABLE IF NOT EXISTS content_posts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  title VARCHAR(120) NOT NULL,
  content VARCHAR(4000) NOT NULL,
  status VARCHAR(20) NOT NULL,
  review_comment VARCHAR(1000) NULL,
  created_at DATETIME NOT NULL,
  reviewed_at DATETIME NULL
);

CREATE TABLE IF NOT EXISTS announcements (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(120) NOT NULL,
  content VARCHAR(2000) NOT NULL,
  publisher_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS donations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  donor_name VARCHAR(50) NOT NULL,
  user_id BIGINT NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  message VARCHAR(500) NULL,
  created_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS feedbacks (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  content VARCHAR(1000) NOT NULL,
  status VARCHAR(20) NOT NULL,
  reply VARCHAR(1000) NULL,
  created_at DATETIME NOT NULL,
  resolved_at DATETIME NULL
);

CREATE TABLE IF NOT EXISTS notifications (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  title VARCHAR(120) NOT NULL,
  content VARCHAR(1000) NOT NULL,
  read_flag BIT(1) NOT NULL,
  created_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS audit_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  operator_id BIGINT NOT NULL,
  operator_name VARCHAR(50) NOT NULL,
  operator_role VARCHAR(20) NOT NULL,
  action VARCHAR(80) NOT NULL,
  target_type VARCHAR(60) NOT NULL,
  target_id VARCHAR(80) NOT NULL,
  detail VARCHAR(2000) NOT NULL,
  ip_address VARCHAR(120) NOT NULL,
  created_at DATETIME NOT NULL
);

CREATE INDEX idx_activities_organizer_status ON activities (organizer_id, status);
CREATE INDEX idx_registrations_activity_status ON activity_registrations (activity_id, status);
CREATE INDEX idx_registrations_user_registered_at ON activity_registrations (user_id, registered_at);
CREATE INDEX idx_service_records_user_created_at ON service_records (user_id, created_at);
CREATE INDEX idx_feedbacks_user_created_at ON feedbacks (user_id, created_at);
CREATE INDEX idx_notifications_user_read_created_at ON notifications (user_id, read_flag, created_at);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at);
CREATE INDEX idx_audit_logs_action_created_at ON audit_logs (action, created_at);
