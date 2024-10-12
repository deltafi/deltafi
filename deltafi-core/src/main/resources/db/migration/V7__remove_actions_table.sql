DROP TABLE actions;
TRUNCATE TABLE delta_file_flows, annotations, delta_files;
ALTER TABLE delta_file_flows
    ADD COLUMN actions jsonb DEFAULT '[]'::jsonb,
    ADD COLUMN error_acknowledged timestamp(6) with time zone,
    ADD COLUMN error_acknowledged_reason varchar(255),
    ADD COLUMN cold_queued boolean DEFAULT FALSE,
    ADD COLUMN error_or_filter_cause varchar(100000),
    ADD COLUMN next_auto_resume timestamp(6) with time zone;
ALTER TABLE delta_file_flows
    DROP CONSTRAINT IF EXISTS delta_file_flows_state_check,
    ADD CONSTRAINT delta_file_flows_state_check
        CHECK (state::text = ANY (ARRAY['IN_FLIGHT'::text, 'COMPLETE'::text, 'ERROR'::text, 'CANCELLED'::text, 'PENDING_ANNOTATIONS'::text, 'FILTERED'::text]));

CREATE INDEX IF NOT EXISTS idx_delta_file_flow_error_count ON delta_file_flows (name) WHERE state = 'ERROR' AND error_acknowledged IS NULL;
CREATE INDEX IF NOT EXISTS idx_delta_file_flow_cold_queued ON delta_file_flows (type, ((actions->(jsonb_array_length(actions) - 1))->>'name')) WHERE state = 'IN_FLIGHT' AND cold_queued = TRUE;