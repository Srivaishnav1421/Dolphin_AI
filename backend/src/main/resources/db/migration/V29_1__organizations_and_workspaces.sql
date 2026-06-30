CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS organizations (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    plan VARCHAR(50) NOT NULL DEFAULT 'AGENCY',
    billing_email VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS workspaces (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    organization_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_workspaces_organization FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_workspaces_organization_id
    ON workspaces(organization_id);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);

INSERT INTO organizations (id, name, plan, billing_email, active, created_at, updated_at)
SELECT 'default-dolphin-organization',
       'DolphinAI Default Organization',
       'AGENCY',
       MIN(email),
       TRUE,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM users
WHERE NOT EXISTS (
    SELECT 1 FROM organizations WHERE id = 'default-dolphin-organization'
)
HAVING COUNT(*) > 0;

UPDATE users
SET organization_id = 'default-dolphin-organization'
WHERE organization_id IS NULL
  AND EXISTS (SELECT 1 FROM organizations WHERE id = 'default-dolphin-organization');

INSERT INTO workspaces (id, name, organization_id, created_at, updated_at)
SELECT DISTINCT u.account_id,
       'Default Workspace',
       u.organization_id,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM users u
WHERE u.account_id IS NOT NULL
  AND u.organization_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM workspaces w WHERE w.id = u.account_id
  );
