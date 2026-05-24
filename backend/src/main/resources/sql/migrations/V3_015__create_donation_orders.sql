CREATE TABLE IF NOT EXISTS donation_orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  donor_name VARCHAR(50) NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  message VARCHAR(500) NULL,
  status VARCHAR(20) NOT NULL,
  callback_token VARCHAR(64) NOT NULL,
  payment_note VARCHAR(500) NULL,
  created_at DATETIME NOT NULL,
  paid_at DATETIME NULL
);
