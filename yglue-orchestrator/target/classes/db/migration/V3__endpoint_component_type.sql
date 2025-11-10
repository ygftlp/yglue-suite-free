ALTER TABLE yglue_project_endpoint
    ADD COLUMN component_type VARCHAR(32) NOT NULL DEFAULT 'BUSINESS' AFTER endpoint_type;

CREATE INDEX idx_endpoint_component_type ON yglue_project_endpoint (component_type);

UPDATE yglue_project_endpoint
SET component_type = 'SYSTEM'
WHERE endpoint_type = 'REST';

UPDATE yglue_project_endpoint
SET component_type = 'BUSINESS'
WHERE component_type IS NULL OR component_type = '';
