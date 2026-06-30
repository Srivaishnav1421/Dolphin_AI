ALTER TABLE users
    ADD COLUMN IF NOT EXISTS two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS two_factor_secret VARCHAR(128),
    ADD COLUMN IF NOT EXISTS two_factor_enabled_at TIMESTAMP WITHOUT TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_users_two_factor_enabled
    ON users(two_factor_enabled)
    WHERE two_factor_enabled = TRUE;
