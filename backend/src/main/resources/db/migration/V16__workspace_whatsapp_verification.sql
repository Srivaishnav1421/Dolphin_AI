-- V16: Dynamic Multi-Tenant WhatsApp Webhook Verification

-- 1. Increase the size of whatsapp_verify_token to VARCHAR(255)
ALTER TABLE workspace_configs ALTER COLUMN whatsapp_verify_token TYPE VARCHAR(255);

-- 2. Add whatsapp_webhook_enabled column to workspace_configs
ALTER TABLE workspace_configs ADD COLUMN IF NOT EXISTS whatsapp_webhook_enabled BOOLEAN DEFAULT FALSE;

-- 3. Add whatsapp_verify_token_hash for high-performance secure cryptographic database lookup
ALTER TABLE workspace_configs ADD COLUMN IF NOT EXISTS whatsapp_verify_token_hash VARCHAR(64);

-- 4. Create indexes for fast lookups
CREATE INDEX IF NOT EXISTS idx_workspace_configs_whatsapp_verify_token ON workspace_configs (whatsapp_verify_token);
CREATE INDEX IF NOT EXISTS idx_workspace_configs_whatsapp_verify_token_hash ON workspace_configs (whatsapp_verify_token_hash);
