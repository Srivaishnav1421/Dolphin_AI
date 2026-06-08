-- V6: Meta Execution Fields — adds fields needed for real Meta API integration
-- and fatigue / WhatsApp / CAPI tables

-- ── Campaign: Meta API execution tracking fields ─────────────────────────────
ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS meta_campaign_id    VARCHAR(50);
ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS meta_status         VARCHAR(30);
ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS meta_ad_account_id  VARCHAR(50);
ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS meta_page_id        VARCHAR(50);
ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS last_synced_at      TIMESTAMP;
ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS is_protected        BOOLEAN DEFAULT FALSE;
ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS decision_quality_score DOUBLE PRECISION DEFAULT 0.0;

CREATE UNIQUE INDEX IF NOT EXISTS idx_campaigns_meta_id ON campaigns (meta_campaign_id);

-- ── Brain Events: execution status and Meta error code ──────────────────────
ALTER TABLE brain_events ADD COLUMN IF NOT EXISTS meta_error_code    INTEGER;
ALTER TABLE brain_events ADD COLUMN IF NOT EXISTS execution_status   VARCHAR(20) DEFAULT 'SUCCESS';
ALTER TABLE brain_events ADD COLUMN IF NOT EXISTS executed_by        VARCHAR(50) DEFAULT 'BRAIN';
ALTER TABLE brain_events ADD COLUMN IF NOT EXISTS old_value          VARCHAR(255);
ALTER TABLE brain_events ADD COLUMN IF NOT EXISTS new_value          VARCHAR(255);

-- ── Fatigue Alerts ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS fatigue_alerts (
    id              VARCHAR(36)  PRIMARY KEY,
    workspace_id    VARCHAR(36),
    campaign_id     VARCHAR(36)  NOT NULL,
    creative_id     VARCHAR(36),
    fatigue_type    VARCHAR(30)  NOT NULL,  -- CTR, FREQUENCY, CPM
    baseline_ctr    DOUBLE PRECISION,
    current_ctr     DOUBLE PRECISION,
    baseline_freq   DOUBLE PRECISION,
    current_freq    DOUBLE PRECISION,
    baseline_cpm    DOUBLE PRECISION,
    current_cpm     DOUBLE PRECISION,
    detected_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    resolved_at     TIMESTAMP,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING, ROTATED, ACKNOWLEDGED
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_fatigue_workspace  ON fatigue_alerts (workspace_id);
CREATE INDEX IF NOT EXISTS idx_fatigue_campaign   ON fatigue_alerts (campaign_id);
CREATE INDEX IF NOT EXISTS idx_fatigue_status     ON fatigue_alerts (status);

-- ── WhatsApp Messages ────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS whatsapp_messages (
    id                  VARCHAR(36)   PRIMARY KEY,
    workspace_id        VARCHAR(36),
    lead_id             VARCHAR(36),
    to_number           VARCHAR(20)   NOT NULL,
    template_name       VARCHAR(100)  NOT NULL,
    template_params     TEXT,         -- JSON array of param values
    message_id          VARCHAR(100), -- Meta-assigned message ID
    status              VARCHAR(20)   NOT NULL DEFAULT 'SENT',  -- SENT, DELIVERED, READ, FAILED, REPLIED
    sent_at             TIMESTAMP,
    delivered_at        TIMESTAMP,
    read_at             TIMESTAMP,
    reply_text          TEXT,
    reply_received_at   TIMESTAMP,
    error_message       TEXT,
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_wa_messages_lead      ON whatsapp_messages (lead_id);
CREATE INDEX IF NOT EXISTS idx_wa_messages_workspace ON whatsapp_messages (workspace_id);
CREATE INDEX IF NOT EXISTS idx_wa_messages_status    ON whatsapp_messages (status);

-- ── WhatsApp Templates ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS whatsapp_templates (
    id              VARCHAR(36)   PRIMARY KEY,
    workspace_id    VARCHAR(36),
    name            VARCHAR(100)  NOT NULL,
    language_code   VARCHAR(10)   NOT NULL DEFAULT 'en_IN',
    body_text       TEXT          NOT NULL,
    category        VARCHAR(30)   NOT NULL DEFAULT 'MARKETING',  -- UTILITY, MARKETING, AUTHENTICATION
    industry_type   VARCHAR(50),  -- REAL_ESTATE, EDUCATION, FINTECH, HEALTHCARE, GENERIC
    param_count     INTEGER       NOT NULL DEFAULT 0,
    is_active       BOOLEAN       NOT NULL DEFAULT TRUE,
    is_system       BOOLEAN       NOT NULL DEFAULT FALSE,  -- system seeds cannot be deleted
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_wa_templates_workspace ON whatsapp_templates (workspace_id);

-- ── WhatsApp System Template Seeds ──────────────────────────────────────────
INSERT INTO whatsapp_templates (id, workspace_id, name, language_code, body_text, category, industry_type, param_count, is_active, is_system) VALUES
    ('wt-re-001',  NULL, 'real_estate_lead',  'en_IN', 'Hi {{1}}, thanks for your interest in {{2}}! Our advisor will call you in {{3}} minutes. Reply YES for brochure.', 'MARKETING', 'REAL_ESTATE',  3, TRUE, TRUE),
    ('wt-edu-001', NULL, 'education_lead',    'en_IN', 'Hi {{1}}, thanks for enquiring about {{2}}! Our counsellor will contact you shortly. Reply DETAILS for curriculum.', 'MARKETING', 'EDUCATION',    2, TRUE, TRUE),
    ('wt-gen-001', NULL, 'generic_lead',      'en_IN', 'Hi {{1}}, we received your enquiry for {{2}}! Our team reaches you in {{3}} hours. Reply STOP to unsubscribe.', 'MARKETING', 'GENERIC',      3, TRUE, TRUE),
    ('wt-fu1-001', NULL, 'followup_day1',     'en_IN', 'Hi {{1}}, following up on your enquiry for {{2}}. Still interested? Reply YES and we will prioritise your case.', 'MARKETING', 'GENERIC',      2, TRUE, TRUE),
    ('wt-fu3-001', NULL, 'followup_day3',     'en_IN', 'Hi {{1}}, this is your final follow-up from {{2}}. Reply CALL to schedule a callback at your preferred time.', 'MARKETING', 'GENERIC',      2, TRUE, TRUE);

-- ── Pixel Configs (Meta Conversions API) ─────────────────────────────────────
CREATE TABLE IF NOT EXISTS pixel_configs (
    id                  VARCHAR(36)   PRIMARY KEY,
    workspace_id        VARCHAR(36)   NOT NULL,
    pixel_id            VARCHAR(50)   NOT NULL,
    access_token        VARCHAR(1000) NOT NULL,  -- encrypted
    test_event_code     VARCHAR(50),  -- use for dev/test mode
    is_active           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_pixel_workspace ON pixel_configs (workspace_id);

-- ── Lead: WhatsApp + CAPI fields ─────────────────────────────────────────────
ALTER TABLE leads ADD COLUMN IF NOT EXISTS phone            VARCHAR(20);
ALTER TABLE leads ADD COLUMN IF NOT EXISTS email            VARCHAR(255);
ALTER TABLE leads ADD COLUMN IF NOT EXISTS ip_address       VARCHAR(45);
ALTER TABLE leads ADD COLUMN IF NOT EXISTS user_agent       TEXT;
ALTER TABLE leads ADD COLUMN IF NOT EXISTS source_url       VARCHAR(500);
ALTER TABLE leads ADD COLUMN IF NOT EXISTS opted_out        BOOLEAN       DEFAULT FALSE;
ALTER TABLE leads ADD COLUMN IF NOT EXISTS last_reply       TEXT;
ALTER TABLE leads ADD COLUMN IF NOT EXISTS last_reply_at    TIMESTAMP;
ALTER TABLE leads ADD COLUMN IF NOT EXISTS crm_pushed       BOOLEAN       DEFAULT FALSE;
ALTER TABLE leads ADD COLUMN IF NOT EXISTS crm_lead_id      VARCHAR(100);
ALTER TABLE leads ADD COLUMN IF NOT EXISTS capi_sent        BOOLEAN       DEFAULT FALSE;
ALTER TABLE leads ADD COLUMN IF NOT EXISTS workspace_id     VARCHAR(36);

-- ── Lead Interactions (positive reply tracking) ───────────────────────────────
CREATE TABLE IF NOT EXISTS lead_interactions (
    id              VARCHAR(36)   PRIMARY KEY,
    lead_id         VARCHAR(36)   NOT NULL,
    workspace_id    VARCHAR(36),
    type            VARCHAR(50)   NOT NULL,  -- POSITIVE_REPLY, NEGATIVE_REPLY, CALL_BOOKED, etc.
    channel         VARCHAR(30)   NOT NULL DEFAULT 'WHATSAPP',
    details         TEXT,
    created_at      TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_interactions_lead ON lead_interactions (lead_id);
