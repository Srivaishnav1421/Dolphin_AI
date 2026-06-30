ALTER TABLE campaign_math_evaluations
    ADD COLUMN IF NOT EXISTS run_id UUID NULL;

CREATE INDEX IF NOT EXISTS idx_campaign_math_eval_run_created
    ON campaign_math_evaluations(run_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_campaign_math_eval_workspace_run_created
    ON campaign_math_evaluations(workspace_id, run_id, created_at DESC);
