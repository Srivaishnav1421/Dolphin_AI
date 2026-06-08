-- 🐬 Flyway Migration V1: Initial Schema Setup

-- 1. Create Users Table
CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    role VARCHAR(50),
    account_id VARCHAR(255),
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP WITHOUT TIME ZONE
);

-- 2. Create Campaigns Table
CREATE TABLE campaigns (
    id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50),
    objective VARCHAR(50),
    budget DOUBLE PRECISION,
    spent DOUBLE PRECISION,
    ctr DOUBLE PRECISION,
    cpl DOUBLE PRECISION,
    roas DOUBLE PRECISION,
    performance_score DOUBLE PRECISION,
    meta_campaign_id VARCHAR(255),
    pause_on_weekends BOOLEAN DEFAULT FALSE,
    scheduled_end_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_campaigns_account_id ON campaigns(account_id);
CREATE INDEX idx_campaigns_status ON campaigns(account_id, status);

-- 3. Create Leads Table
CREATE TABLE leads (
    id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    source VARCHAR(50),
    message VARCHAR(2000),
    status VARCHAR(50),
    score DOUBLE PRECISION,
    budget_signal VARCHAR(255),
    timeline_signal VARCHAR(255),
    intent_signal VARCHAR(255),
    location_signal VARCHAR(255),
    gemini_analysis VARCHAR(4000),
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_leads_account_id ON leads(account_id);
CREATE INDEX idx_leads_status ON leads(account_id, status);
CREATE INDEX idx_leads_created ON leads(account_id, created_at);

-- 4. Create Wallet Table
CREATE TABLE wallet (
    id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(255),
    balance DOUBLE PRECISION,
    total_spent DOUBLE PRECISION,
    daily_budget_limit DOUBLE PRECISION,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 5. Create Wallet Transactions Table
CREATE TABLE wallet_transactions (
    id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(255) NOT NULL,
    type VARCHAR(50),
    amount DOUBLE PRECISION,
    balance_after DOUBLE PRECISION,
    description VARCHAR(512),
    reference_id VARCHAR(255),
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_wtx_account ON wallet_transactions(account_id, created_at);
CREATE INDEX idx_wtx_type ON wallet_transactions(account_id, type);

-- 6. Create Brain Events Table
CREATE TABLE brain_events (
    id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255),
    message VARCHAR(2000),
    severity VARCHAR(50),
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_brain_events_account ON brain_events(account_id, created_at);
CREATE INDEX idx_brain_events_type ON brain_events(account_id, event_type);

-- 7. Create Audit Logs Table
CREATE TABLE audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    user_email VARCHAR(255),
    action VARCHAR(255),
    resource_type VARCHAR(255),
    resource_id VARCHAR(255),
    details VARCHAR(1000),
    ip_address VARCHAR(255),
    timestamp TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 8. Create Refresh Tokens Table
CREATE TABLE refresh_tokens (
    id VARCHAR(36) PRIMARY KEY,
    token VARCHAR(512) UNIQUE NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    revoked BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITHOUT TIME ZONE
);
CREATE INDEX idx_refresh_token_value ON refresh_tokens(token);
CREATE INDEX idx_refresh_token_user_id ON refresh_tokens(user_id);
