ALTER TABLE resume_policies
    ADD COLUMN IF NOT EXISTS enabled boolean NOT NULL DEFAULT true;

-- Denormalized so the scheduler anti-join and the disable-time bulk clear can
-- filter by policy name without extracting from the action JSONB on the hot path.
ALTER TABLE delta_file_flows
    ADD COLUMN IF NOT EXISTS next_auto_resume_reason text;

-- Backfill bounded by the existing partial index (only currently-scheduled rows).
UPDATE delta_file_flows
SET next_auto_resume_reason = (actions -> -1 ->> 'nrr')
WHERE next_auto_resume IS NOT NULL
  AND next_auto_resume_reason IS NULL;

DROP INDEX IF EXISTS idx_delta_file_flows_auto_resume;

-- INCLUDE keeps next_auto_resume_reason in the index leaves so the anti-join
-- filter resolves without heap fetches.
CREATE INDEX IF NOT EXISTS idx_delta_file_flows_auto_resume
    ON delta_file_flows (next_auto_resume, delta_file_id)
    INCLUDE (next_auto_resume_reason)
    WHERE state = 'ERROR' AND error_acknowledged IS NULL;
