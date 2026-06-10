CREATE INDEX idx_public_resources_status_created_at
ON public_resources (status, created_at);

CREATE INDEX idx_help_needs_status_created_at
ON help_needs (status, created_at);

CREATE INDEX idx_resource_matches_status_created_at
ON resource_matches (status, created_at);
