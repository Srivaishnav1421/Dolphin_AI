-- V17: Lead Processing Pipeline Validation & Monitoring

CREATE TABLE IF NOT EXISTS lead_pipeline_events (
    id              VARCHAR(36)  PRIMARY KEY,
    workspace_id    VARCHAR(36),
    lead_id         VARCHAR(36),
    event_type      VARCHAR(50)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    details         VARCHAR(2000), -- Using VARCHAR to ensure seamless compatibility across H2 and PostgreSQL JSONB stores
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_lead_pipeline_workspace ON lead_pipeline_events (workspace_id);
CREATE INDEX IF NOT EXISTS idx_lead_pipeline_lead      ON lead_pipeline_events (lead_id);
CREATE INDEX IF NOT EXISTS idx_lead_pipeline_type      ON lead_pipeline_events (event_type);

CREATE TABLE IF NOT EXISTS system_alerts (
    id              VARCHAR(36)  PRIMARY KEY,
    workspace_id    VARCHAR(36),
    alert_type      VARCHAR(50)  NOT NULL,
    message         VARCHAR(1000) NOT NULL,
    severity        VARCHAR(20)  NOT NULL, -- LOW, MEDIUM, HIGH, CRITICAL
    resolved        BOOLEAN      DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_system_alerts_workspace ON system_alerts (workspace_id);
CREATE INDEX IF NOT EXISTS idx_system_alerts_resolved  ON system_alerts (resolved);
