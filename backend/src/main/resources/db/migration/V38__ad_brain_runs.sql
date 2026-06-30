CREATE TABLE IF NOT EXISTS ad_brain_runs (
    id UUID PRIMARY KEY,
    organization_id UUID NULL,
    workspace_id UUID NULL,
    account_id UUID NULL,
    status VARCHAR(40) NOT NULL,
    campaigns_evaluated INT NOT NULL DEFAULT 0,
    evaluations_created INT NOT NULL DEFAULT 0,
    approval_items_created INT NOT NULL DEFAULT 0,
    duplicate_approvals_skipped INT NOT NULL DEFAULT 0,
    risks_created INT NOT NULL DEFAULT 0,
    opportunities_created INT NOT NULL DEFAULT 0,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP NULL,
    error_message TEXT NULL,
    created_by UUID NULL
);

CREATE INDEX IF NOT EXISTS idx_ad_brain_runs_workspace_started
    ON ad_brain_runs(workspace_id, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_ad_brain_runs_account_started
    ON ad_brain_runs(account_id, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_ad_brain_runs_status
    ON ad_brain_runs(status);

CREATE INDEX IF NOT EXISTS idx_ad_brain_runs_started
    ON ad_brain_runs(started_at DESC);
