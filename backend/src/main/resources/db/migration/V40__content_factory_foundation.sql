CREATE TABLE IF NOT EXISTS content_factory_items (
    id UUID PRIMARY KEY,
    organization_id UUID NULL,
    workspace_id UUID NOT NULL,
    account_id UUID NOT NULL,
    created_by UUID NULL,
    content_type VARCHAR(60) NOT NULL,
    business_name VARCHAR(255) NOT NULL,
    product_service VARCHAR(500) NOT NULL,
    target_audience VARCHAR(500) NOT NULL,
    location VARCHAR(255) NULL,
    offer VARCHAR(500) NULL,
    tone VARCHAR(40) NOT NULL,
    language VARCHAR(80) NULL,
    channel VARCHAR(80) NOT NULL,
    goal VARCHAR(255) NULL,
    cta_style VARCHAR(80) NULL,
    generation_mode VARCHAR(40) NOT NULL,
    input_request_json TEXT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_content_factory_items_workspace_created
    ON content_factory_items(workspace_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_content_factory_items_account_created
    ON content_factory_items(account_id, created_at DESC);

CREATE TABLE IF NOT EXISTS content_factory_variants (
    id UUID PRIMARY KEY,
    item_id UUID NOT NULL REFERENCES content_factory_items(id) ON DELETE CASCADE,
    organization_id UUID NULL,
    workspace_id UUID NOT NULL,
    account_id UUID NOT NULL,
    created_by UUID NULL,
    variant_index INTEGER NOT NULL,
    headline VARCHAR(120) NULL,
    description VARCHAR(255) NULL,
    cta VARCHAR(80) NULL,
    content_text TEXT NOT NULL,
    generation_mode VARCHAR(40) NOT NULL,
    score INTEGER NOT NULL,
    score_breakdown_json TEXT NOT NULL,
    approval_status VARCHAR(40) NOT NULL,
    approval_item_id UUID NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    approved_at TIMESTAMP NULL,
    rejected_at TIMESTAMP NULL
);

CREATE INDEX IF NOT EXISTS idx_content_factory_variants_item
    ON content_factory_variants(item_id, variant_index);

CREATE INDEX IF NOT EXISTS idx_content_factory_variants_workspace_status
    ON content_factory_variants(workspace_id, approval_status);

CREATE INDEX IF NOT EXISTS idx_content_factory_variants_approval_item
    ON content_factory_variants(approval_item_id);
