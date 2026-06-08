-- V26: Marketing landing pages and form builder foundation
-- Phase 1: simple industry templates, public landing pages, custom forms, and CRM lead capture.

CREATE TABLE IF NOT EXISTS marketing_forms (
    id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(36) NOT NULL,
    name VARCHAR(180) NOT NULL,
    slug VARCHAR(180) NOT NULL,
    industry_type VARCHAR(80),
    campaign_id VARCHAR(36),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    fields_json TEXT,
    settings_json TEXT,
    spam_protection_enabled BOOLEAN DEFAULT TRUE,
    trigger_automation BOOLEAN DEFAULT TRUE,
    submissions_count BIGINT DEFAULT 0,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_marketing_forms_workspace ON marketing_forms(account_id);
CREATE INDEX IF NOT EXISTS idx_marketing_forms_slug ON marketing_forms(account_id, slug);

CREATE TABLE IF NOT EXISTS landing_pages (
    id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(36) NOT NULL,
    title VARCHAR(180) NOT NULL,
    slug VARCHAR(180) NOT NULL,
    industry_type VARCHAR(80),
    template_key VARCHAR(80),
    campaign_id VARCHAR(36),
    form_id VARCHAR(36),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    sections_json TEXT,
    seo_json TEXT,
    custom_domain VARCHAR(255),
    public_path VARCHAR(255),
    visits BIGINT DEFAULT 0,
    submissions BIGINT DEFAULT 0,
    published_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_landing_pages_workspace ON landing_pages(account_id);
CREATE INDEX IF NOT EXISTS idx_landing_pages_slug ON landing_pages(account_id, slug);
CREATE INDEX IF NOT EXISTS idx_landing_pages_status ON landing_pages(account_id, status);

CREATE TABLE IF NOT EXISTS form_submissions (
    id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(36) NOT NULL,
    form_id VARCHAR(36) NOT NULL,
    landing_page_id VARCHAR(36),
    campaign_id VARCHAR(36),
    lead_id VARCHAR(36),
    source VARCHAR(80) DEFAULT 'LANDING_PAGE',
    status VARCHAR(30) NOT NULL DEFAULT 'ACCEPTED',
    payload_json TEXT,
    ip_address VARCHAR(255),
    user_agent VARCHAR(1000),
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_form_submissions_workspace ON form_submissions(account_id, created_at);
CREATE INDEX IF NOT EXISTS idx_form_submissions_form ON form_submissions(form_id);
CREATE INDEX IF NOT EXISTS idx_form_submissions_lead ON form_submissions(lead_id);
CREATE INDEX IF NOT EXISTS idx_form_submissions_campaign ON form_submissions(account_id, campaign_id);
