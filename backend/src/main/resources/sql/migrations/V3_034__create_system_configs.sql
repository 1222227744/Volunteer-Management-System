CREATE TABLE IF NOT EXISTS system_configs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  config_key VARCHAR(80) NOT NULL,
  config_value VARCHAR(500) NOT NULL,
  config_name VARCHAR(120) NOT NULL,
  description VARCHAR(500) NOT NULL,
  editable BIT(1) NOT NULL,
  updated_at DATETIME NOT NULL,
  updated_by BIGINT NOT NULL,
  updated_by_name VARCHAR(50) NOT NULL,
  CONSTRAINT uk_system_configs_key UNIQUE (config_key)
);
