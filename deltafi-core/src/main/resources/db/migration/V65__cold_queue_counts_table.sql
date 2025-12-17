-- ABOUTME: Creates trigger-maintained cold_queue_entries table for accurate cold queue metrics.
-- ABOUTME: Stores one row per cold-queued item with accurate timestamps for oldest-item queries.

-- Row-per-item table for cold queue entries
CREATE TABLE IF NOT EXISTS cold_queue_entries (
    delta_file_flow_id UUID PRIMARY KEY REFERENCES delta_file_flows(id) ON DELETE CASCADE,
    delta_file_id UUID NOT NULL,
    flow_name TEXT NOT NULL,
    flow_type TEXT,
    action_name TEXT NOT NULL,
    action_class TEXT NOT NULL,
    queued_at TIMESTAMPTZ NOT NULL
);

-- Index for efficient lookups by action_class (for warming queries)
CREATE INDEX IF NOT EXISTS idx_cold_queue_action_class ON cold_queue_entries(action_class);

-- Index for efficient grouping by flow/action
CREATE INDEX IF NOT EXISTS idx_cold_queue_flow_action ON cold_queue_entries(flow_name, action_name, action_class);

-- Function to manage cold queue entries when cold_queued_action changes
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
        INSERT INTO cold_queue_entries (delta_file_flow_id, delta_file_id, flow_name, flow_type, action_name, action_class, queued_at)
        SELECT NEW.id, NEW.delta_file_id, fd.name, fd.type::text, NEW.actions->-1->>'n', NEW.cold_queued_action,
               COALESCE((NEW.actions->-1->>'q')::timestamptz, NOW())
        FROM flow_definitions fd WHERE fd.id = NEW.flow_definition_id
        ON CONFLICT (delta_file_flow_id) DO NOTHING;
    ELSIF NEW.cold_queued_action IS NULL AND OLD.cold_queued_action IS NOT NULL THEN
        -- Leaving cold queue - delete entry
        DELETE FROM cold_queue_entries WHERE delta_file_flow_id = NEW.id;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger on delta_file_flows
DROP TRIGGER IF EXISTS trg_cold_queue_entry ON delta_file_flows;
CREATE TRIGGER trg_cold_queue_entry
    AFTER INSERT OR UPDATE OR DELETE ON delta_file_flows
    FOR EACH ROW
    EXECUTE FUNCTION manage_cold_queue_entry();

-- Backfill existing cold queue entries
INSERT INTO cold_queue_entries (delta_file_flow_id, delta_file_id, flow_name, flow_type, action_name, action_class, queued_at)
SELECT dff.id, dff.delta_file_id, fd.name, fd.type::text, dff.actions->-1->>'n', dff.cold_queued_action,
       COALESCE((dff.actions->-1->>'q')::timestamptz, dff.modified)
FROM delta_file_flows dff
JOIN flow_definitions fd ON fd.id = dff.flow_definition_id
WHERE dff.cold_queued_action IS NOT NULL
  AND dff.actions->-1->>'n' IS NOT NULL
ON CONFLICT (delta_file_flow_id) DO NOTHING;
