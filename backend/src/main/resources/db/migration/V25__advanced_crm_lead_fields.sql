-- V25: Advanced CRM lead profile fields
-- Adds business-facing CRM fields while preserving the existing HOT/WARM/COLD scoring model.

ALTER TABLE leads ADD COLUMN IF NOT EXISTS campaign_id VARCHAR(36);
ALTER TABLE leads ADD COLUMN IF NOT EXISTS assigned_user_id VARCHAR(36);
ALTER TABLE leads ADD COLUMN IF NOT EXISTS tags VARCHAR(1000);
ALTER TABLE leads ADD COLUMN IF NOT EXISTS notes VARCHAR(4000);
ALTER TABLE leads ADD COLUMN IF NOT EXISTS pipeline_stage VARCHAR(80) DEFAULT 'NEW_LEAD';
ALTER TABLE leads ADD COLUMN IF NOT EXISTS priority VARCHAR(30) DEFAULT 'MEDIUM';
ALTER TABLE leads ADD COLUMN IF NOT EXISTS budget DOUBLE PRECISION;
ALTER TABLE leads ADD COLUMN IF NOT EXISTS interest_category VARCHAR(255);
ALTER TABLE leads ADD COLUMN IF NOT EXISTS location VARCHAR(255);
ALTER TABLE leads ADD COLUMN IF NOT EXISTS last_contacted_at TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE leads ADD COLUMN IF NOT EXISTS next_follow_up_at TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE leads ADD COLUMN IF NOT EXISTS conversion_probability DOUBLE PRECISION;
ALTER TABLE leads ADD COLUMN IF NOT EXISTS expected_revenue DOUBLE PRECISION;
ALTER TABLE leads ADD COLUMN IF NOT EXISTS lost_reason VARCHAR(1000);
ALTER TABLE leads ADD COLUMN IF NOT EXISTS ai_summary VARCHAR(4000);
ALTER TABLE leads ADD COLUMN IF NOT EXISTS next_best_action VARCHAR(1000);

UPDATE leads
SET pipeline_stage = CASE
    WHEN status = 'HOT' THEN 'INTERESTED'
    WHEN status = 'WARM' THEN 'QUALIFIED'
    WHEN status = 'UNQUALIFIABLE' THEN 'LOST'
    ELSE COALESCE(pipeline_stage, 'NEW_LEAD')
END
WHERE pipeline_stage IS NULL OR pipeline_stage = 'NEW_LEAD';

CREATE INDEX IF NOT EXISTS idx_leads_pipeline_stage ON leads(account_id, pipeline_stage);
CREATE INDEX IF NOT EXISTS idx_leads_assigned_user ON leads(account_id, assigned_user_id);
CREATE INDEX IF NOT EXISTS idx_leads_next_follow_up ON leads(account_id, next_follow_up_at);
CREATE INDEX IF NOT EXISTS idx_leads_source ON leads(account_id, source);
