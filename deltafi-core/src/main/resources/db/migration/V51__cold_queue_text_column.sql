-- add a new column that stores the cold_queued_action class name
ALTER TABLE delta_file_flows ADD COLUMN cold_queued_action TEXT DEFAULT NULL;

-- find all cold-queued deltaFiles and pull up the cold queued action class name
UPDATE delta_file_flows
SET cold_queued_action = (
    SELECT action->>'ac'
    FROM jsonb_array_elements(actions) AS action
    WHERE action->>'s' = 'COLD_QUEUED'
)
WHERE state = 'IN_FLIGHT'
  AND cold_queued = TRUE;

-- remove the old index
DROP INDEX IF EXISTS idx_delta_file_flows_cold_queued;

-- create a new index that, cold_queued_action first to support finding
-- the distinct cold queued actions and searching by those values
-- include the delta_file_id so cold queue queries are performed against the index only
CREATE INDEX IF NOT EXISTS idx_delta_file_flows_cold_queued
    ON delta_file_flows (cold_queued_action, delta_file_id)
    WHERE state = 'IN_FLIGHT'
    AND cold_queued_action IS NOT NULL;