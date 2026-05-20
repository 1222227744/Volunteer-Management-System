ALTER TABLE service_records
ADD CONSTRAINT uk_service_record_activity_user UNIQUE (activity_id, user_id);
