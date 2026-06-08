-- V18: Campaign Optimization and Workspace Safety Limits

-- Create brain_decisions table if not exists (missing from V1-V17 migrations)
CREATE TABLE IF NOT EXISTS brain_decisions (
    id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(255) NOT NULL,
    campaign_id VARCHAR(36),
    campaign_name VARCHAR(255),
    decision_type VARCHAR(50) NOT NULL,
    action VARCHAR(2000),
    confidence DOUBLE PRECISION,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_APPROVAL',
    llm_provider VARCHAR(255),
    raw_ai_response VARCHAR(4000),
    reason VARCHAR(2000),
    budget_before DOUBLE PRECISION,
    budget_after DOUBLE PRECISION,
    roas_at_decision DOUBLE PRECISION,
    ctr_at_decision DOUBLE PRECISION,
    cpl_at_decision DOUBLE PRECISION,
    spent_at_decision DOUBLE PRECISION,
    outcome_positive BOOLEAN,
    roas_after_execution DOUBLE PRECISION,
    approved_by VARCHAR(255),
    approved_at TIMESTAMP WITHOUT TIME ZONE,
    executed_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_brain_dec_account ON brain_decisions(account_id, created_at);
CREATE INDEX IF NOT EXISTS idx_brain_dec_campaign ON brain_decisions(campaign_id);
CREATE INDEX IF NOT EXISTS idx_brain_dec_status ON brain_decisions(status);

-- 1. Add campaign metrics fields to campaigns table
ALTER TABLE campaigns ADD COLUMN conversions INTEGER DEFAULT 0;
ALTER TABLE campaigns ADD COLUMN days_of_data INTEGER DEFAULT 0;
ALTER TABLE campaigns ADD COLUMN conversion_rate DOUBLE PRECISION DEFAULT 0.0;
ALTER TABLE campaigns ADD COLUMN spend_velocity DOUBLE PRECISION DEFAULT 0.0;

-- 2. Add safety threshold and auto-optimization fields to workspace_configs table
ALTER TABLE workspace_configs ADD COLUMN min_roas_threshold DOUBLE PRECISION DEFAULT 2.0;
ALTER TABLE workspace_configs ADD COLUMN max_spend_limit DOUBLE PRECISION DEFAULT 10000.0;
ALTER TABLE workspace_configs ADD COLUMN target_cpl DOUBLE PRECISION DEFAULT 500.0;
ALTER TABLE workspace_configs ADD COLUMN auto_optimization_enabled BOOLEAN DEFAULT FALSE;

-- 3. Add risk, confidence, snapshots and explainability fields to brain_decisions table
ALTER TABLE brain_decisions ADD COLUMN risk_score DOUBLE PRECISION DEFAULT 0.0;
ALTER TABLE brain_decisions ADD COLUMN confidence_score DOUBLE PRECISION DEFAULT 0.0;
ALTER TABLE brain_decisions ADD COLUMN campaign_snapshot_json TEXT;
ALTER TABLE brain_decisions ADD COLUMN trigger_metrics VARCHAR(1000);
ALTER TABLE brain_decisions ADD COLUMN threshold_breached VARCHAR(1000);

-- 4. Create brain_decision_history table for auditing & rollbacks
CREATE TABLE brain_decision_history (
    id VARCHAR(36) PRIMARY KEY,
    decision_id VARCHAR(36) NOT NULL,
    account_id VARCHAR(255) NOT NULL,
    campaign_id VARCHAR(36) NOT NULL,
    action VARCHAR(255) NOT NULL,
    confidence_score DOUBLE PRECISION,
    risk_score DOUBLE PRECISION,
    metrics_at_decision TEXT,
    thresholds_at_decision TEXT,
    campaign_snapshot_json TEXT,
    status VARCHAR(50),
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_bd_history_decision ON brain_decision_history(decision_id);
CREATE INDEX idx_bd_history_campaign ON brain_decision_history(campaign_id);
