CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS user_workspace_roles (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    workspace_id VARCHAR(36) NOT NULL,
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_workspace_role UNIQUE (user_id, workspace_id),
    CONSTRAINT fk_user_workspace_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_workspace_roles_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_workspace_roles_user
    ON user_workspace_roles(user_id);

CREATE INDEX IF NOT EXISTS idx_user_workspace_roles_workspace
    ON user_workspace_roles(workspace_id);

INSERT INTO user_workspace_roles (id, user_id, workspace_id, role, created_at)
SELECT gen_random_uuid()::text,
       u.id,
       u.account_id,
       COALESCE(NULLIF(u.role, ''), 'VIEWER'),
       CURRENT_TIMESTAMP
FROM users u
JOIN workspaces w ON w.id = u.account_id
WHERE u.account_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM user_workspace_roles r
      WHERE r.user_id = u.id
        AND r.workspace_id = u.account_id
  );
