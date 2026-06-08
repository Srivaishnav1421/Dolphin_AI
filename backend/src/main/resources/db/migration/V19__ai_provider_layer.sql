-- V19: Enterprise AI Provider Layer Schemas (Phase 3 & 4 Standardized)

-- 1. AI Usage Logs
CREATE TABLE IF NOT EXISTS ai_usage_logs (
    id VARCHAR(36) PRIMARY KEY,
    workspace_id VARCHAR(255) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    model VARCHAR(100) NOT NULL,
    prompt_tokens INTEGER NOT NULL DEFAULT 0,
    completion_tokens INTEGER NOT NULL DEFAULT 0,
    total_tokens INTEGER NOT NULL DEFAULT 0,
    cost_usd DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    purpose VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_usage_workspace ON ai_usage_logs(workspace_id, created_at);

-- 2. AI Response Caches
CREATE TABLE IF NOT EXISTS ai_response_caches (
    prompt_hash VARCHAR(64) PRIMARY KEY,
    provider VARCHAR(50) NOT NULL,
    model VARCHAR(100) NOT NULL,
    cached_response TEXT NOT NULL,
    prompt_tokens INTEGER NOT NULL DEFAULT 0,
    completion_tokens INTEGER NOT NULL DEFAULT 0,
    total_tokens INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
);

-- 3. Workspace AI Configuration
CREATE TABLE IF NOT EXISTS workspace_ai_configs (
    workspace_id VARCHAR(255) PRIMARY KEY,
    active_provider VARCHAR(50) NOT NULL DEFAULT 'OLLAMA',
    temperature DOUBLE PRECISION NOT NULL DEFAULT 0.3,
    max_tokens INTEGER NOT NULL DEFAULT 1024,
    enable_caching BOOLEAN NOT NULL DEFAULT TRUE,
    enable_fallback_routing BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. Workspace AI Budgets
CREATE TABLE IF NOT EXISTS ai_workspace_budgets (
    workspace_id VARCHAR(255) PRIMARY KEY,
    monthly_usd_budget DOUBLE PRECISION NOT NULL DEFAULT 200.0,
    monthly_tokens_budget INTEGER NOT NULL DEFAULT 20000000,
    warning_threshold_percent DOUBLE PRECISION NOT NULL DEFAULT 80.0,
    hard_stop_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
