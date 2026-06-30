-- Remove duplicate tenant columns that were superseded by account_id.
-- Lead and creative entities map their workspace/tenant key to account_id.
ALTER TABLE leads DROP COLUMN IF EXISTS workspace_id;
ALTER TABLE ad_creatives DROP COLUMN IF EXISTS workspace_id;
