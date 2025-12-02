-- Timestamp index (sorting + range queries)
CREATE INDEX IF NOT EXISTS idx_audit_timestamp
    ON audit_events (timestamp);

-- Category filter
CREATE INDEX IF NOT EXISTS idx_audit_category
    ON audit_events (category);

-- Event name filter
CREATE INDEX IF NOT EXISTS idx_audit_event_name
    ON audit_events (event_name);

-- Subject ID filter (most frequently queried)
CREATE INDEX IF NOT EXISTS idx_audit_subject_id
    ON audit_events (subject_id);

-- Outcome filter
CREATE INDEX IF NOT EXISTS idx_audit_outcome
    ON audit_events (outcome);
