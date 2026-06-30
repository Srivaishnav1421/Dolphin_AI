-- Phase 1 Sprint 3: normalize organization scope for remaining business tables.
-- Safe strategy: nullable add, best-effort backfill from workspaces, indexes only.

ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);
UPDATE campaigns t SET organization_id = w.organization_id
FROM workspaces w
WHERE t.account_id = w.id AND t.organization_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_campaigns_org_workspace ON campaigns(organization_id, account_id);
CREATE INDEX IF NOT EXISTS idx_campaigns_organization ON campaigns(organization_id);

ALTER TABLE leads ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);
UPDATE leads t SET organization_id = w.organization_id
FROM workspaces w
WHERE t.account_id = w.id AND t.organization_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_leads_org_workspace ON leads(organization_id, account_id);
CREATE INDEX IF NOT EXISTS idx_leads_organization ON leads(organization_id);

ALTER TABLE ad_creatives ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);
UPDATE ad_creatives t SET organization_id = w.organization_id
FROM workspaces w
WHERE t.account_id = w.id AND t.organization_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_ad_creatives_org_workspace ON ad_creatives(organization_id, account_id);
CREATE INDEX IF NOT EXISTS idx_ad_creatives_organization ON ad_creatives(organization_id);

ALTER TABLE advantage_experiments ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);
UPDATE advantage_experiments t SET organization_id = w.organization_id
FROM workspaces w
WHERE t.workspace_id = w.id AND t.organization_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_advantage_experiments_org_workspace ON advantage_experiments(organization_id, workspace_id);
CREATE INDEX IF NOT EXISTS idx_advantage_experiments_organization ON advantage_experiments(organization_id);

ALTER TABLE workflow_executions ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);
UPDATE workflow_executions t SET organization_id = w.organization_id
FROM workspaces w
WHERE t.workspace_id = w.id AND t.organization_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_workflow_exec_org_workspace ON workflow_executions(organization_id, workspace_id);
CREATE INDEX IF NOT EXISTS idx_workflow_exec_organization ON workflow_executions(organization_id);

ALTER TABLE workflow_approvals ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);
UPDATE workflow_approvals t SET organization_id = w.organization_id
FROM workspaces w
WHERE t.workspace_id = w.id AND t.organization_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_workflow_approvals_org_workspace ON workflow_approvals(organization_id, workspace_id);
CREATE INDEX IF NOT EXISTS idx_workflow_approvals_organization ON workflow_approvals(organization_id);

ALTER TABLE brain_events ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);
UPDATE brain_events t SET organization_id = w.organization_id
FROM workspaces w
WHERE t.account_id = w.id AND t.organization_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_brain_events_org_workspace ON brain_events(organization_id, account_id);
CREATE INDEX IF NOT EXISTS idx_brain_events_organization ON brain_events(organization_id);

ALTER TABLE brain_decisions ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);
UPDATE brain_decisions t SET organization_id = w.organization_id
FROM workspaces w
WHERE t.account_id = w.id AND t.organization_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_brain_decisions_org_workspace ON brain_decisions(organization_id, account_id);
CREATE INDEX IF NOT EXISTS idx_brain_decisions_organization ON brain_decisions(organization_id);

ALTER TABLE brain_decision_history ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);
UPDATE brain_decision_history t SET organization_id = w.organization_id
FROM workspaces w
WHERE t.account_id = w.id AND t.organization_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_brain_history_org_workspace ON brain_decision_history(organization_id, account_id);
CREATE INDEX IF NOT EXISTS idx_brain_history_organization ON brain_decision_history(organization_id);

DO $$
BEGIN
    IF to_regclass('public.metric_snapshots') IS NOT NULL THEN
        ALTER TABLE metric_snapshots ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);
        UPDATE metric_snapshots t SET organization_id = w.organization_id
        FROM workspaces w
        WHERE t.account_id = w.id AND t.organization_id IS NULL;
        CREATE INDEX IF NOT EXISTS idx_metric_snapshots_org_workspace ON metric_snapshots(organization_id, account_id);
        CREATE INDEX IF NOT EXISTS idx_metric_snapshots_organization ON metric_snapshots(organization_id);
    END IF;
END $$;

ALTER TABLE meta_connections ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);
UPDATE meta_connections t SET organization_id = w.organization_id
FROM workspaces w
WHERE t.account_id = w.id AND t.organization_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_meta_connections_org_workspace ON meta_connections(organization_id, account_id);
CREATE INDEX IF NOT EXISTS idx_meta_connections_organization ON meta_connections(organization_id);

ALTER TABLE meta_audiences ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);
UPDATE meta_audiences t SET organization_id = w.organization_id
FROM workspaces w
WHERE t.workspace_id = w.id AND t.organization_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_meta_audiences_org_workspace ON meta_audiences(organization_id, workspace_id);
CREATE INDEX IF NOT EXISTS idx_meta_audiences_organization ON meta_audiences(organization_id);

ALTER TABLE competitor_insights ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);
UPDATE competitor_insights t SET organization_id = w.organization_id
FROM workspaces w
WHERE t.account_id = w.id AND t.organization_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_competitor_insights_org_workspace ON competitor_insights(organization_id, account_id);
CREATE INDEX IF NOT EXISTS idx_competitor_insights_organization ON competitor_insights(organization_id);

ALTER TABLE competitor_ads ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);
UPDATE competitor_ads t SET organization_id = w.organization_id
FROM workspaces w
WHERE t.workspace_id = w.id AND t.organization_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_competitor_ads_org_workspace ON competitor_ads(organization_id, workspace_id);
CREATE INDEX IF NOT EXISTS idx_competitor_ads_organization ON competitor_ads(organization_id);

ALTER TABLE marketing_forms ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);
UPDATE marketing_forms t SET organization_id = w.organization_id
FROM workspaces w
WHERE t.account_id = w.id AND t.organization_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_marketing_forms_org_workspace ON marketing_forms(organization_id, account_id);
CREATE INDEX IF NOT EXISTS idx_marketing_forms_organization ON marketing_forms(organization_id);

ALTER TABLE landing_pages ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);
UPDATE landing_pages t SET organization_id = w.organization_id
FROM workspaces w
WHERE t.account_id = w.id AND t.organization_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_landing_pages_org_workspace ON landing_pages(organization_id, account_id);
CREATE INDEX IF NOT EXISTS idx_landing_pages_organization ON landing_pages(organization_id);

ALTER TABLE form_submissions ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);
UPDATE form_submissions t SET organization_id = w.organization_id
FROM workspaces w
WHERE t.account_id = w.id AND t.organization_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_form_submissions_org_workspace ON form_submissions(organization_id, account_id);
CREATE INDEX IF NOT EXISTS idx_form_submissions_organization ON form_submissions(organization_id);

ALTER TABLE whatsapp_messages ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);
UPDATE whatsapp_messages t SET organization_id = w.organization_id
FROM workspaces w
WHERE t.workspace_id = w.id AND t.organization_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_whatsapp_messages_org_workspace ON whatsapp_messages(organization_id, workspace_id);
CREATE INDEX IF NOT EXISTS idx_whatsapp_messages_organization ON whatsapp_messages(organization_id);

ALTER TABLE whatsapp_templates ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);
UPDATE whatsapp_templates t SET organization_id = w.organization_id
FROM workspaces w
WHERE t.workspace_id = w.id AND t.organization_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_whatsapp_templates_org_workspace ON whatsapp_templates(organization_id, workspace_id);
CREATE INDEX IF NOT EXISTS idx_whatsapp_templates_organization ON whatsapp_templates(organization_id);

ALTER TABLE ai_usage_logs ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);
UPDATE ai_usage_logs t SET organization_id = w.organization_id
FROM workspaces w
WHERE t.workspace_id = w.id AND t.organization_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_ai_usage_org_workspace ON ai_usage_logs(organization_id, workspace_id);
CREATE INDEX IF NOT EXISTS idx_ai_usage_organization ON ai_usage_logs(organization_id);

ALTER TABLE ai_response_caches ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);
CREATE INDEX IF NOT EXISTS idx_ai_response_caches_organization ON ai_response_caches(organization_id);

ALTER TABLE invoices ADD COLUMN IF NOT EXISTS organization_id VARCHAR(36);
UPDATE invoices t SET organization_id = w.organization_id
FROM workspaces w
WHERE t.workspace_id = w.id AND t.organization_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_invoices_org_workspace ON invoices(organization_id, workspace_id);
CREATE INDEX IF NOT EXISTS idx_invoices_organization ON invoices(organization_id);
