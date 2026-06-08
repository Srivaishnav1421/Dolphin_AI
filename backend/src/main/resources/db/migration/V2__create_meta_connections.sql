-- 🐬 Flyway Migration V2: Create Meta Connections Table
-- Stores user's connection details to Meta (Facebook/Instagram) Ad Account with AES-256 encrypted access tokens.

CREATE TABLE meta_connections (
    id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(255) NOT NULL,
    meta_user_id VARCHAR(255),
    meta_ad_account_id VARCHAR(255) NOT NULL,
    meta_page_id VARCHAR(255),
    meta_page_name VARCHAR(255),
    meta_business_id VARCHAR(255),
    access_token VARCHAR(1000) NOT NULL,
    token_status VARCHAR(50) DEFAULT 'VALID',
    token_expires_at TIMESTAMP WITHOUT TIME ZONE,
    last_sync_at TIMESTAMP WITHOUT TIME ZONE,
    auto_manage_enabled BOOLEAN DEFAULT FALSE,
    max_daily_spend DOUBLE PRECISION DEFAULT 10000.0,
    pause_roas_threshold DOUBLE PRECISION DEFAULT 1.5,
    scale_up_roas_threshold DOUBLE PRECISION DEFAULT 3.0,
    max_budget_change_percent DOUBLE PRECISION DEFAULT 30.0,
    ad_account_name VARCHAR(255),
    currency VARCHAR(50) DEFAULT 'INR',
    timezone VARCHAR(100) DEFAULT 'Asia/Kolkata',
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_meta_conn_account ON meta_connections(account_id);
CREATE INDEX idx_meta_conn_status ON meta_connections(token_status);
