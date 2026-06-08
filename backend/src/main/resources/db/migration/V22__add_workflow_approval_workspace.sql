-- V22: Add Workspace ID to Workflow Approvals
ALTER TABLE workflow_approvals ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_wf_appr_workspace ON workflow_approvals(workspace_id);
