-- V7: CRM, Audience, Competitor Spy, Advantage Experiments

-- ── CRM Configs ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS crm_configs (
    id                  VARCHAR(36)   PRIMARY KEY,
    workspace_id        VARCHAR(36)   NOT NULL,
    crm_type            VARCHAR(20)   NOT NULL,   -- ZOHO, HUBSPOT
    access_token        VARCHAR(1000) NOT NULL,   -- encrypted
    refresh_token       VARCHAR(1000),            -- encrypted
    instance_url        VARCHAR(255),
    score_threshold     INTEGER       NOT NULL DEFAULT 70,
    auto_sync_enabled   BOOLEAN       NOT NULL DEFAULT TRUE,
    token_expires_at    TIMESTAMP,
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_crm_workspace_type ON crm_configs (workspace_id, crm_type);

-- ── Meta Audiences ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS meta_audiences (
    id                  VARCHAR(36)   PRIMARY KEY,
    workspace_id        VARCHAR(36)   NOT NULL,
    meta_audience_id    VARCHAR(50),
    name                VARCHAR(255)  NOT NULL,
    audience_type       VARCHAR(30)   NOT NULL,  -- CUSTOM, LOOKALIKE, SUPER_LOOKALIKE
    subtype             VARCHAR(30),             -- CUSTOMER_FILE, WEBSITE, ENGAGEMENT, LOOKALIKE
    size_estimate       BIGINT,
    source_audience_id  VARCHAR(50),
    lookalike_ratio     DOUBLE PRECISION,
    lookalike_country   VARCHAR(5) DEFAULT 'IN',
    status              VARCHAR(20) DEFAULT 'ACTIVE',
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audiences_workspace ON meta_audiences (workspace_id);

-- ── Competitor Ads (Ad Library spy) ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS competitor_ads (
    id                  VARCHAR(36)   PRIMARY KEY,
    workspace_id        VARCHAR(36),
    keyword             VARCHAR(255),
    page_name           VARCHAR(255),
    page_id             VARCHAR(50),
    ad_text             TEXT,
    snapshot_url        VARCHAR(500),
    format              VARCHAR(30),   -- VIDEO, IMAGE, CAROUSEL
    hook_type           VARCHAR(50),   -- QUESTION, STATEMENT, OFFER, URGENCY, PAIN_POINT
    offer_type          VARCHAR(50),   -- DISCOUNT, FREE_TRIAL, LEAD_GEN, DEMO, INFORMATION
    emotion             VARCHAR(30),   -- FEAR, JOY, CURIOSITY, TRUST, URGENCY
    quality_score       INTEGER,       -- 1-10
    delivery_start_date DATE,
    fetched_at          TIMESTAMP      DEFAULT NOW(),
    created_at          TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_competitor_ads_workspace ON competitor_ads (workspace_id);
CREATE INDEX IF NOT EXISTS idx_competitor_ads_keyword   ON competitor_ads (keyword);

-- ── Advantage+ Experiments ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS advantage_experiments (
    id                  VARCHAR(36)   PRIMARY KEY,
    workspace_id        VARCHAR(36),
    campaign_id         VARCHAR(36)   NOT NULL,
    meta_campaign_id    VARCHAR(50),
    switched_at         TIMESTAMP     NOT NULL,
    roas_before         DOUBLE PRECISION,
    roas_after_14d      DOUBLE PRECISION,
    roas_after_30d      DOUBLE PRECISION,
    net_roas_delta      DOUBLE PRECISION,
    status              VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',  -- SUGGESTED, ACTIVE, REVERTED, SUCCESS
    reverted_at         TIMESTAMP,
    revert_reason       TEXT,
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_adv_exp_campaign   ON advantage_experiments (campaign_id);
CREATE INDEX IF NOT EXISTS idx_adv_exp_workspace  ON advantage_experiments (workspace_id);
CREATE INDEX IF NOT EXISTS idx_adv_exp_status     ON advantage_experiments (status);

-- ── Ad Creatives Table ────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ad_creatives (
    id                  VARCHAR(36)   PRIMARY KEY,
    account_id          VARCHAR(255)  NOT NULL,
    campaign_id         VARCHAR(36),
    meta_ad_id          VARCHAR(255),
    headline            VARCHAR(500),
    body                VARCHAR(2000),
    call_to_action      VARCHAR(255),
    image_url           VARCHAR(255),
    video_url           VARCHAR(255),
    platform            VARCHAR(255),
    status              VARCHAR(255)  DEFAULT 'DRAFT',
    generated_by        VARCHAR(255)  DEFAULT 'MANUAL',
    generation_prompt   VARCHAR(2000),
    predicted_ctr       DOUBLE PRECISION,
    actual_ctr          DOUBLE PRECISION,
    actual_cpc          DOUBLE PRECISION,
    impressions         BIGINT        DEFAULT 0,
    clicks              BIGINT        DEFAULT 0,
    conversions         BIGINT        DEFAULT 0,
    spend               DOUBLE PRECISION DEFAULT 0.0,
    ab_test_group       VARCHAR(255),
    ab_test_id          VARCHAR(255),
    created_at          TIMESTAMP     DEFAULT NOW(),
    updated_at          TIMESTAMP     DEFAULT NOW(),
    language            VARCHAR(5)    DEFAULT 'en',
    workspace_id        VARCHAR(36)
);

CREATE INDEX IF NOT EXISTS idx_creative_campaign ON ad_creatives (campaign_id);
CREATE INDEX IF NOT EXISTS idx_creative_account  ON ad_creatives (account_id);
CREATE INDEX IF NOT EXISTS idx_creative_status   ON ad_creatives (status);

-- ── Workspace Config (WhatsApp phone number, CAPI pixel, etc.) ───────────────
CREATE TABLE IF NOT EXISTS workspace_configs (
    id                      VARCHAR(36)   PRIMARY KEY,
    workspace_id            VARCHAR(36)   UNIQUE NOT NULL,
    whatsapp_phone_id       VARCHAR(50),
    whatsapp_token          VARCHAR(1000),  -- encrypted
    whatsapp_verify_token   VARCHAR(100),
    brand_name              VARCHAR(255),
    brand_logo_url          VARCHAR(500),
    billing_email           VARCHAR(255),
    gstin                   VARCHAR(20),
    legal_name              VARCHAR(255),
    billing_address         TEXT,
    state_code              VARCHAR(5),
    pan_number              VARCHAR(20),
    bank_details            TEXT,
    created_at              TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- ── Reports (generated PDF tracking) ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS reports (
    id              VARCHAR(36)   PRIMARY KEY,
    workspace_id    VARCHAR(36)   NOT NULL,
    report_type     VARCHAR(30)   NOT NULL,  -- DAILY, WEEKLY, MONTHLY, CUSTOM
    period_from     DATE          NOT NULL,
    period_to       DATE          NOT NULL,
    pdf_path        VARCHAR(500),
    sent_to         VARCHAR(255),
    sent_at         TIMESTAMP,
    file_size_bytes BIGINT,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_reports_workspace ON reports (workspace_id);

-- ── Invoices ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS invoices (
    id              VARCHAR(36)   PRIMARY KEY,
    workspace_id    VARCHAR(36)   NOT NULL,
    invoice_number  VARCHAR(30)   UNIQUE NOT NULL,
    transaction_id  VARCHAR(36),
    subtotal        DOUBLE PRECISION NOT NULL,
    gst_rate        DOUBLE PRECISION NOT NULL,
    gst_amount      DOUBLE PRECISION NOT NULL,
    gst_type        VARCHAR(10)   NOT NULL,  -- CGST_SGST, IGST
    total           DOUBLE PRECISION NOT NULL,
    pdf_path        VARCHAR(500),
    sent_at         TIMESTAMP,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_invoices_workspace ON invoices (workspace_id);

-- ── Invoice sequence counter (per financial year) ─────────────────────────────
CREATE TABLE IF NOT EXISTS invoice_sequences (
    year_key        VARCHAR(9)    PRIMARY KEY,  -- e.g., '2026-2027'
    last_number     INTEGER       NOT NULL DEFAULT 0
);
