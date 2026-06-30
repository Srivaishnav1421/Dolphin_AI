CREATE TABLE IF NOT EXISTS campaign_math_evaluations (
    id UUID PRIMARY KEY,
    organization_id UUID NULL,
    workspace_id UUID NULL,
    account_id UUID NULL,
    campaign_id UUID NULL,
    evaluation_type VARCHAR(80) NOT NULL,
    status VARCHAR(40) NOT NULL,
    severity VARCHAR(40) NOT NULL,
    action_type VARCHAR(80) NOT NULL,
    score NUMERIC NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    input_snapshot_json TEXT NULL,
    formula_version VARCHAR(100) NOT NULL,
    requires_approval BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_campaign_math_eval_workspace_campaign_created
    ON campaign_math_evaluations(workspace_id, campaign_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_campaign_math_eval_account_campaign_created
    ON campaign_math_evaluations(account_id, campaign_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_campaign_math_eval_workspace_type_created
    ON campaign_math_evaluations(workspace_id, evaluation_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_campaign_math_eval_status_severity
    ON campaign_math_evaluations(status, severity);

CREATE INDEX IF NOT EXISTS idx_campaign_math_eval_action_type
    ON campaign_math_evaluations(action_type);

CREATE INDEX IF NOT EXISTS idx_campaign_math_eval_created_at
    ON campaign_math_evaluations(created_at DESC);
