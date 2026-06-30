CREATE TABLE IF NOT EXISTS approval_items (
    id UUID PRIMARY KEY,
    organization_id UUID NULL,
    workspace_id UUID NULL,
    account_id UUID NULL,
    source_module VARCHAR(50) NOT NULL,
    source_entity_type VARCHAR(100) NULL,
    source_entity_id UUID NULL,
    action_type VARCHAR(60) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    recommendation_json TEXT NULL,
    math_snapshot_json TEXT NULL,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    requires_execution BOOLEAN NOT NULL DEFAULT TRUE,
    execution_status VARCHAR(40) NULL,
    execution_result_json TEXT NULL,
    created_by UUID NULL,
    approved_by UUID NULL,
    rejected_by UUID NULL,
    executed_by UUID NULL,
    rejection_reason TEXT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    approved_at TIMESTAMP NULL,
    rejected_at TIMESTAMP NULL,
    executed_at TIMESTAMP NULL,
    expires_at TIMESTAMP NULL
);

CREATE INDEX IF NOT EXISTS idx_approval_items_workspace_status
    ON approval_items(workspace_id, status);

CREATE INDEX IF NOT EXISTS idx_approval_items_account_status
    ON approval_items(account_id, status);

CREATE INDEX IF NOT EXISTS idx_approval_items_organization_status
    ON approval_items(organization_id, status);

CREATE INDEX IF NOT EXISTS idx_approval_items_source_module_status
    ON approval_items(source_module, status);

CREATE INDEX IF NOT EXISTS idx_approval_items_created_at
    ON approval_items(created_at);

CREATE INDEX IF NOT EXISTS idx_approval_items_status_severity
    ON approval_items(status, severity);
