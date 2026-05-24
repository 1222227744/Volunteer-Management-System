CREATE TABLE IF NOT EXISTS incident_records (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(120) NOT NULL,
  description VARCHAR(1000) NOT NULL,
  severity VARCHAR(20) NOT NULL,
  status VARCHAR(20) NOT NULL,
  handling_measure VARCHAR(1000) NULL,
  result VARCHAR(1000) NULL,
  created_by BIGINT NOT NULL,
  created_by_name VARCHAR(50) NOT NULL,
  created_at DATETIME NOT NULL,
  resolved_at DATETIME NULL
);
