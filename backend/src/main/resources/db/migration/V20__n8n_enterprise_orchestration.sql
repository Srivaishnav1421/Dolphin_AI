-- ═══════════════════════════════════════════════════════════════════
--  DolphinAI — V20 n8n Enterprise Orchestration Tables
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE workflow_executions (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    workflow_id VARCHAR(100),
    workflow_name VARCHAR(255),
    workflow_version VARCHAR(50),
    workflow_snapshot TEXT,
    execution_id VARCHAR(100),
    trace_id VARCHAR(100),
    parent_trace_id VARCHAR(100),
    tenant_id VARCHAR(100),
    workspace_id VARCHAR(100),
    project_id VARCHAR(100),
    user_id VARCHAR(100),
    agent_used VARCHAR(100),
    status VARCHAR(50) DEFAULT 'RUNNING',
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    execution_duration BIGINT,
    user_request TEXT,
    final_response TEXT,
    error_logs TEXT
);

CREATE INDEX idx_wf_exec_tenant ON workflow_executions(tenant_id);
CREATE INDEX idx_wf_exec_trace ON workflow_executions(trace_id);
CREATE INDEX idx_wf_exec_id ON workflow_executions(execution_id);
CREATE INDEX idx_wf_exec_workflow ON workflow_executions(workflow_id);
CREATE INDEX idx_wf_exec_status ON workflow_executions(status);

CREATE TABLE workflow_approvals (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    execution_id VARCHAR(36) NOT NULL,
    trace_id VARCHAR(100),
    status VARCHAR(50) DEFAULT 'PENDING',
    assigned_to VARCHAR(100),
    decision_reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_wf_appr_exec ON workflow_approvals(execution_id);
CREATE INDEX idx_wf_appr_status ON workflow_approvals(status);

CREATE TABLE workflow_templates (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(100),
    workflow_snapshot TEXT
);
