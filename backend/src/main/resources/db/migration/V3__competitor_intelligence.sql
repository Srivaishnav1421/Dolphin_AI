-- V3__competitor_intelligence.sql
-- Create table for storing competitor analysis insights extracted by AI agents

CREATE TABLE competitor_insights (
    id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(255) NOT NULL,
    competitor_url VARCHAR(1000) NOT NULL,
    value_proposition TEXT NOT NULL,
    extracted_hooks VARCHAR(1000) ARRAY NOT NULL,
    target_demographics TEXT NOT NULL,
    pricing_model VARCHAR(255),
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_comp_insight_account ON competitor_insights(account_id);
