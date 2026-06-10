ALTER TABLE activities
ADD COLUMN check_code VARCHAR(32) NULL;

UPDATE activities
SET check_code = UPPER(SUBSTRING(REPLACE(UUID(), '-', ''), 1, 8))
WHERE check_code IS NULL OR check_code = '';

ALTER TABLE activities
MODIFY COLUMN check_code VARCHAR(32) NOT NULL;
