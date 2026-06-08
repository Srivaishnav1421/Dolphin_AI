-- V21: Credential Encryption and Audit Trail Update

-- 1. Refresh Token changes
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS token_hash VARCHAR(256);
CREATE UNIQUE INDEX IF NOT EXISTS idx_refresh_token_hash ON refresh_tokens(token_hash);
DROP INDEX IF EXISTS idx_refresh_token_value;
UPDATE refresh_tokens SET revoked = TRUE;

-- 2. Audit Log changes
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS event_type VARCHAR(100);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(36);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(256);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS actor_id VARCHAR(256);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS actor_type VARCHAR(50);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS entity_type VARCHAR(100);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS entity_id VARCHAR(256);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS old_value TEXT;
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS new_value TEXT;

-- Create indexes on audit_logs for workspace and actor
CREATE INDEX IF NOT EXISTS idx_audit_logs_workspace ON audit_logs(workspace_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor ON audit_logs(actor_type, actor_id);
