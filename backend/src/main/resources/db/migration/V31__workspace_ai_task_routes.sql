CREATE TABLE IF NOT EXISTS workspace_ai_task_routes (
    id VARCHAR(36) PRIMARY KEY,
    workspace_id VARCHAR(255) NOT NULL,
    task_key VARCHAR(80) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_workspace_ai_task_route UNIQUE (workspace_id, task_key)
);

CREATE INDEX IF NOT EXISTS idx_workspace_ai_task_route_workspace
    ON workspace_ai_task_routes(workspace_id);
