-- V41: Add temperature column to separate lead scoring from business status
-- temperature = HOT/WARM/COLD/UNKNOWN (scoring result)
-- status = NEW/CONTACTED/QUALIFIED/WON/LOST (business status)

ALTER TABLE leads ADD COLUMN IF NOT EXISTS temperature VARCHAR(50);

-- Backfill temperature from status where status is a temperature value
UPDATE leads
SET temperature = status
WHERE status IN ('HOT', 'WARM', 'COLD', 'UNKNOWN', 'UNQUALIFIABLE');

-- For new leads without a status, default to UNKNOWN
UPDATE leads
SET temperature = 'UNKNOWN'
WHERE temperature IS NULL;

-- Default status to NEW for leads without explicit status
UPDATE leads
SET status = 'NEW'
WHERE status IN ('HOT', 'WARM', 'COLD', 'UNKNOWN', 'UNQUALIFIABLE');

CREATE INDEX IF NOT EXISTS idx_leads_temperature ON leads(account_id, temperature);
