CREATE INDEX idx_incident_records_status_created_at
ON incident_records (status, created_at);
