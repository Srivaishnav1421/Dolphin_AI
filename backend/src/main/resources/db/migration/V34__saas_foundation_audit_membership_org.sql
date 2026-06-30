ALTER TABLE audit_logs
    ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);

CREATE INDEX IF NOT EXISTS idx_audit_logs_organization_id
    ON audit_logs(organization_id);

CREATE INDEX IF NOT EXISTS idx_audit_logs_org_workspace_timestamp
    ON audit_logs(organization_id, workspace_id, timestamp DESC);

ALTER TABLE user_workspace_roles
    ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);

UPDATE user_workspace_roles r
SET organization_id = w.organization_id
FROM workspaces w
WHERE r.workspace_id = w.id
  AND r.organization_id IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_user_workspace_roles_organization'
          AND table_name = 'user_workspace_roles'
    ) THEN
        ALTER TABLE user_workspace_roles
            ADD CONSTRAINT fk_user_workspace_roles_organization
            FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_user_workspace_roles_organization
    ON user_workspace_roles(organization_id);

CREATE INDEX IF NOT EXISTS idx_user_workspace_roles_org_workspace
    ON user_workspace_roles(organization_id, workspace_id);
