-- Add source connector ID (who generated the audit)
ALTER TABLE audit_events
    ADD COLUMN IF NOT EXISTS source VARCHAR(255);

-- Add created_at fallback auto timestamp
ALTER TABLE audit_events
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT NOW();
