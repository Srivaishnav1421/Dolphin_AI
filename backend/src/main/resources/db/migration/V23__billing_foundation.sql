-- V23: India Billing Foundation and Compliance Tables (INR Only)

-- 1. Subscription Plans
CREATE TABLE IF NOT EXISTS subscription_plans (
    id              VARCHAR(36)   PRIMARY KEY,
    name            VARCHAR(100)  NOT NULL,
    base_price_inr  NUMERIC(12,2) NOT NULL,
    included_seats  INTEGER       NOT NULL DEFAULT 1,
    seat_price_inr  NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    is_active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- 2. Subscription Entitlements
CREATE TABLE IF NOT EXISTS subscription_entitlements (
    id              VARCHAR(36)   PRIMARY KEY,
    plan_id         VARCHAR(36)   NOT NULL REFERENCES subscription_plans(id) ON DELETE CASCADE,
    feature_key     VARCHAR(50)   NOT NULL,
    limit_type      VARCHAR(20)   NOT NULL,  -- 'HARD', 'SOFT', 'BOOLEAN'
    limit_value     INTEGER,
    is_enabled      BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_plan_feature UNIQUE (plan_id, feature_key)
);

-- 3. Plan Overrides (DA-036)
CREATE TABLE IF NOT EXISTS plan_overrides (
    id              VARCHAR(36)   PRIMARY KEY,
    workspace_id    VARCHAR(36)   NOT NULL,
    feature_key     VARCHAR(100)  NOT NULL,
    limit_value     BIGINT,
    enabled         BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_workspace_feature UNIQUE (workspace_id, feature_key)
);

-- 4. Subscriptions
CREATE TABLE IF NOT EXISTS subscriptions (
    id                       VARCHAR(36)   PRIMARY KEY,
    workspace_id             VARCHAR(36)   NOT NULL UNIQUE,
    plan_id                  VARCHAR(36)   NOT NULL REFERENCES subscription_plans(id),
    status                   VARCHAR(30)   NOT NULL, -- 'ACTIVE', 'PAST_DUE', 'PAUSED', 'SUSPENDED'
    stripe_subscription_id   VARCHAR(100),
    allocated_seats          INTEGER       NOT NULL DEFAULT 1,
    current_period_start     TIMESTAMP     NOT NULL,
    current_period_end       TIMESTAMP     NOT NULL,
    created_at               TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- 5. Billing Cycles (DA-040)
CREATE TABLE IF NOT EXISTS billing_cycles (
    id              VARCHAR(36)   PRIMARY KEY,
    workspace_id    VARCHAR(36)   NOT NULL,
    period_start    TIMESTAMP     NOT NULL,
    period_end      TIMESTAMP     NOT NULL,
    status          VARCHAR(30)   NOT NULL, -- 'CURRENT', 'CLOSED'
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- 6. Wallet Cache (DA-032)
CREATE TABLE IF NOT EXISTS wallet (
    id              VARCHAR(36)   PRIMARY KEY,
    account_id      VARCHAR(255)  UNIQUE,
    balance         NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    promo_balance   NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    updated_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- 7. Wallet Transactions (Immutable Ledger, DA-032)
CREATE TABLE IF NOT EXISTS wallet_transactions (
    id               VARCHAR(36)   PRIMARY KEY,
    workspace_id     VARCHAR(36)   NOT NULL,
    wallet_id        VARCHAR(36)   NOT NULL REFERENCES wallet(id),
    balance_before   NUMERIC(12,2) NOT NULL,
    amount           NUMERIC(12,2) NOT NULL,
    balance_after    NUMERIC(12,2) NOT NULL,
    transaction_type VARCHAR(30)   NOT NULL,
    reference_id     VARCHAR(100),
    reference_type   VARCHAR(50),
    created_at       TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- 8. Usage Events (DA-045)
CREATE TABLE IF NOT EXISTS usage_events (
    id                  VARCHAR(36)   PRIMARY KEY,
    workspace_id        VARCHAR(36)   NOT NULL,
    billing_cycle_id    VARCHAR(36)   NOT NULL REFERENCES billing_cycles(id),
    metric_name         VARCHAR(50)   NOT NULL,
    resource_type       VARCHAR(50),
    resource_id         VARCHAR(100),
    event_source        VARCHAR(50)   NOT NULL,
    units               INTEGER       NOT NULL,
    cost_basis          NUMERIC(12,4) NOT NULL DEFAULT 0.0000,
    billable            BOOLEAN       NOT NULL DEFAULT TRUE,
    provider            VARCHAR(50),
    model               VARCHAR(50),
    credits_consumed    NUMERIC(12,4) DEFAULT 0.0000,
    estimated_cost_inr  NUMERIC(12,4) DEFAULT 0.0000,
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- 9. GST Invoices (DA-043)
CREATE TABLE IF NOT EXISTS invoices (
    id                       VARCHAR(36)   PRIMARY KEY,
    workspace_id             VARCHAR(36)   NOT NULL,
    invoice_number           VARCHAR(50)   NOT NULL UNIQUE,
    status                   VARCHAR(30)   NOT NULL,
    amount_subtotal          NUMERIC(12,2) NOT NULL,
    gst_rate                 NUMERIC(5,2)  NOT NULL DEFAULT 18.00,
    cgst                     NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    sgst                     NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    igst                     NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    amount_total             NUMERIC(12,2) NOT NULL,
    seller_gstin             VARCHAR(15)   NOT NULL,
    buyer_gstin              VARCHAR(15),
    customer_gstin_verified  BOOLEAN       NOT NULL DEFAULT FALSE,
    sac_code                 VARCHAR(20)   NOT NULL DEFAULT '997331',
    invoice_date             TIMESTAMP     NOT NULL,
    due_date                 TIMESTAMP     NOT NULL,
    invoice_version          INTEGER       NOT NULL DEFAULT 1,
    pdf_url                  VARCHAR(500),
    currency                 VARCHAR(3)    NOT NULL DEFAULT 'INR',
    place_of_supply          VARCHAR(50)   NOT NULL,
    created_at               TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- 10. Financial Audit Events (DA-044)
CREATE TABLE IF NOT EXISTS financial_events (
    id              VARCHAR(36)   PRIMARY KEY,
    workspace_id    VARCHAR(36)   NOT NULL,
    event_type      VARCHAR(50)   NOT NULL, -- 'REVENUE', 'REFUND', 'TAX_DEPOSIT'
    amount          NUMERIC(12,2) NOT NULL,
    currency        VARCHAR(3)    NOT NULL DEFAULT 'INR',
    reference_id    VARCHAR(100)  NOT NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- 11. Payment Events (Idempotency Audit, DA-034)
CREATE TABLE IF NOT EXISTS payment_events (
    payment_event_id VARCHAR(100)  PRIMARY KEY,
    provider         VARCHAR(50)   NOT NULL, -- 'RAZORPAY', 'STRIPE'
    event_type       VARCHAR(100)  NOT NULL,
    payload_hash     VARCHAR(64)   NOT NULL,
    processed_at     TIMESTAMP,
    status           VARCHAR(20)   NOT NULL
);
-- Alter existing wallet table from V1
ALTER TABLE wallet ADD COLUMN IF NOT EXISTS promo_balance NUMERIC(12,2) DEFAULT 0.00;

-- Alter existing wallet_transactions table from V1
ALTER TABLE wallet_transactions ADD COLUMN IF NOT EXISTS wallet_id VARCHAR(36);
ALTER TABLE wallet_transactions ADD COLUMN IF NOT EXISTS balance_before NUMERIC(12,2) DEFAULT 0.00;
ALTER TABLE wallet_transactions ADD COLUMN IF NOT EXISTS transaction_type VARCHAR(30) DEFAULT 'DEBIT';
ALTER TABLE wallet_transactions ADD COLUMN IF NOT EXISTS reference_type VARCHAR(50);

-- Alter existing invoices table from V7
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS status VARCHAR(30) DEFAULT 'UNPAID';
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS amount_subtotal NUMERIC(12,2) DEFAULT 0.00;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS cgst NUMERIC(12,2) DEFAULT 0.00;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS sgst NUMERIC(12,2) DEFAULT 0.00;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS igst NUMERIC(12,2) DEFAULT 0.00;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS amount_total NUMERIC(12,2) DEFAULT 0.00;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS seller_gstin VARCHAR(15) DEFAULT '27AAACC4111D1Z5';
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS buyer_gstin VARCHAR(15);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS customer_gstin_verified BOOLEAN DEFAULT FALSE;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS sac_code VARCHAR(20) DEFAULT '997331';
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS invoice_date TIMESTAMP DEFAULT NOW();
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS due_date TIMESTAMP DEFAULT NOW();
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS invoice_version INTEGER DEFAULT 1;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS pdf_url VARCHAR(500);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS currency VARCHAR(3) DEFAULT 'INR';
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS place_of_supply VARCHAR(50) DEFAULT 'MH';

-- ── Database Indices for Scalability ───────────────────────────────
CREATE INDEX IF NOT EXISTS idx_subscriptions_workspace ON subscriptions(workspace_id);
CREATE INDEX IF NOT EXISTS idx_billing_cycles_workspace ON billing_cycles(workspace_id);
CREATE INDEX IF NOT EXISTS idx_wallet_workspace ON wallet(account_id);
CREATE INDEX IF NOT EXISTS idx_wallet_tx_wallet ON wallet_transactions(wallet_id);
CREATE INDEX IF NOT EXISTS idx_usage_events_cycle ON usage_events(billing_cycle_id);
CREATE INDEX IF NOT EXISTS idx_invoices_workspace ON invoices(workspace_id);
CREATE INDEX IF NOT EXISTS idx_financial_events_workspace ON financial_events(workspace_id);


