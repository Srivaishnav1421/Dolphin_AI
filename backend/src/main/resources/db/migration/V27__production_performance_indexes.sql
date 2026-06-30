DO $$
BEGIN
    IF to_regclass('public.leads') IS NOT NULL
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'leads' AND column_name = 'account_id')
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'leads' AND column_name = 'status')
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'leads' AND column_name = 'created_at') THEN
        CREATE INDEX IF NOT EXISTS idx_leads_account_status_created
            ON leads (account_id, status, created_at DESC);
    END IF;

    IF to_regclass('public.campaigns') IS NOT NULL
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'campaigns' AND column_name = 'account_id')
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'campaigns' AND column_name = 'status')
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'campaigns' AND column_name = 'created_at') THEN
        CREATE INDEX IF NOT EXISTS idx_campaigns_account_status_created
            ON campaigns (account_id, status, created_at DESC);
    END IF;

    IF to_regclass('public.brain_events') IS NOT NULL
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'brain_events' AND column_name = 'account_id')
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'brain_events' AND column_name = 'created_at') THEN
        CREATE INDEX IF NOT EXISTS idx_brain_events_account_created_desc
            ON brain_events (account_id, created_at DESC);
    END IF;

    IF to_regclass('public.brain_decisions') IS NOT NULL
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'brain_decisions' AND column_name = 'account_id')
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'brain_decisions' AND column_name = 'status')
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'brain_decisions' AND column_name = 'created_at') THEN
        CREATE INDEX IF NOT EXISTS idx_brain_decisions_account_status_created
            ON brain_decisions (account_id, status, created_at DESC);
    END IF;

    IF to_regclass('public.wallet_transactions') IS NOT NULL
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'wallet_transactions' AND column_name = 'account_id')
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'wallet_transactions' AND column_name = 'created_at') THEN
        CREATE INDEX IF NOT EXISTS idx_wallet_transactions_account_created_desc
            ON wallet_transactions (account_id, created_at DESC);
    END IF;

    IF to_regclass('public.invoices') IS NOT NULL
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'invoices' AND column_name = 'workspace_id')
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'invoices' AND column_name = 'status')
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'invoices' AND column_name = 'created_at') THEN
        CREATE INDEX IF NOT EXISTS idx_invoices_workspace_status_created
            ON invoices (workspace_id, status, created_at DESC);
    END IF;

    IF to_regclass('public.payment_events') IS NOT NULL
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'payment_events' AND column_name = 'status') THEN
        CREATE INDEX IF NOT EXISTS idx_payment_events_status
            ON payment_events (status);
    END IF;

    IF to_regclass('public.subscriptions') IS NOT NULL
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'subscriptions' AND column_name = 'status') THEN
        CREATE INDEX IF NOT EXISTS idx_subscriptions_status
            ON subscriptions (status);
    END IF;

    IF to_regclass('public.workflow_executions') IS NOT NULL
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'workflow_executions' AND column_name = 'tenant_id')
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'workflow_executions' AND column_name = 'status') THEN
        CREATE INDEX IF NOT EXISTS idx_workflow_executions_tenant_status
            ON workflow_executions (tenant_id, status);
    END IF;

    IF to_regclass('public.audit_logs') IS NOT NULL
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'audit_logs' AND column_name = 'workspace_id')
       AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'audit_logs' AND column_name = 'timestamp') THEN
        CREATE INDEX IF NOT EXISTS idx_audit_logs_workspace_timestamp_desc
            ON audit_logs (workspace_id, "timestamp" DESC);
    END IF;
END $$;
