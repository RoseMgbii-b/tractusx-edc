-- Enforce immutability on audit_events using PostgreSQL Row-Level Security
-- This makes audit_events INSERT-only.
-- No UPDATE or DELETE operations will be allowed for non-superusers.

-- Enable Row-Level Security (RLS)
ALTER TABLE audit_events ENABLE ROW LEVEL SECURITY;

-- 1. Allow INSERT for all authenticated users (app role)
CREATE POLICY audit_events_insert_only
    ON audit_events
    FOR INSERT
    TO PUBLIC
    WITH CHECK (true);

-- 2. Deny UPDATE operations for everyone
CREATE POLICY audit_events_no_update
    ON audit_events
    FOR UPDATE
    TO PUBLIC
    USING (false)
    WITH CHECK (false);

-- 3. Deny DELETE operations for everyone
CREATE POLICY audit_events_no_delete
    ON audit_events
    FOR DELETE
    TO PUBLIC
    USING (false);

