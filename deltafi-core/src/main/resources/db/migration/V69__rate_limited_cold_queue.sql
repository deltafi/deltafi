-- ABOUTME: Adds rate_limited column to cold_queue_entries for tracking rate-limited data sink actions.
-- ABOUTME: Updates the trigger to capture rate-limited status from the action JSON.

-- Add rate_limited column
ALTER TABLE cold_queue_entries ADD COLUMN IF NOT EXISTS rate_limited BOOLEAN NOT NULL DEFAULT FALSE;

-- Index for efficient lookups of rate-limited entries by flow_name
CREATE INDEX IF NOT EXISTS idx_cold_queue_entries_rate_limited_flow_name ON cold_queue_entries(flow_name) WHERE rate_limited = TRUE;

-- Update the trigger function to track rate_limited status
CREATE OR REPLACE FUNCTION manage_cold_queue_entry()
RETURNS TRIGGER AS $$
BEGIN
    -- Handle DELETE
    IF TG_OP = 'DELETE' THEN
        DELETE FROM cold_queue_entries WHERE delta_file_flow_id = OLD.id;
        RETURN OLD;
    END IF;

    -- Handle INSERT or UPDATE
    IF NEW.cold_queued_action IS NOT NULL AND
       (TG_OP = 'INSERT' OR OLD.cold_queued_action IS NULL) THEN
        -- Entering cold queue - insert entry
        INSERT INTO cold_queue_entries (delta_file_flow_id, delta_file_id, flow_name, flow_type, action_name, action_class, queued_at, rate_limited)
        SELECT NEW.id, NEW.delta_file_id, fd.name, fd.type::text, NEW.actions->-1->>'n', NEW.cold_queued_action,
               COALESCE((NEW.actions->-1->>'q')::timestamptz, NOW()),
               COALESCE((NEW.actions->-1->>'rl')::boolean, FALSE)
        FROM flow_definitions fd WHERE fd.id = NEW.flow_definition_id
        ON CONFLICT (delta_file_flow_id) DO NOTHING;
    ELSIF NEW.cold_queued_action IS NULL AND OLD.cold_queued_action IS NOT NULL THEN
        -- Leaving cold queue - delete entry
        DELETE FROM cold_queue_entries WHERE delta_file_flow_id = NEW.id;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Backfill rate_limited for existing entries
UPDATE cold_queue_entries cqe
SET rate_limited = COALESCE((dff.actions->-1->>'rl')::boolean, FALSE)
FROM delta_file_flows dff
WHERE cqe.delta_file_flow_id = dff.id;
