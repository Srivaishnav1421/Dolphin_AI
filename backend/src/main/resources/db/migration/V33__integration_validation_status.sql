CREATE TABLE IF NOT EXISTS integration_settings (
    id VARCHAR(36) PRIMARY KEY,
    workspace_id VARCHAR(255) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    credentials_json TEXT,
    validation_status VARCHAR(30) NOT NULL DEFAULT 'PENDING_VALIDATION',
    last_validated_at TIMESTAMP,
    last_validation_message VARCHAR(1000),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

ALTER TABLE integration_settings ADD COLUMN IF NOT EXISTS validation_status VARCHAR(30) NOT NULL DEFAULT 'PENDING_VALIDATION';
ALTER TABLE integration_settings ADD COLUMN IF NOT EXISTS last_validated_at TIMESTAMP;
ALTER TABLE integration_settings ADD COLUMN IF NOT EXISTS last_validation_message VARCHAR(1000);

UPDATE integration_settings
SET validation_status = 'PENDING_VALIDATION'
WHERE validation_status IS NULL OR validation_status = '';

CREATE INDEX IF NOT EXISTS idx_integration_settings_workspace_provider
    ON integration_settings(workspace_id, provider_id);
