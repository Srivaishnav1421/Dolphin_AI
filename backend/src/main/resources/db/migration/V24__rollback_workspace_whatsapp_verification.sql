-- Migration V24: Safety Rollback for Whatsapp Webhook Verification Columns (Neutralized to prevent data deletion)
-- The columns whatsapp_webhook_enabled and whatsapp_verify_token_hash are active in WorkspaceConfig.java
-- Therefore, we do not drop them to avoid runtime entity mapping crashes.

-- SELECT 1;
