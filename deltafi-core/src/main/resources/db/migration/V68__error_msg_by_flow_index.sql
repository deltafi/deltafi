-- Covering index for errorSummaryByMessage per-flow breakdown queries.
-- Enables index-only scans for grouping by (error_or_filter_cause, flow_definition_id).
CREATE INDEX IF NOT EXISTS idx_delta_file_flows_error_msg_by_flow
    ON delta_file_flows (error_or_filter_cause, flow_definition_id, delta_file_id)
    WHERE state = 'ERROR' AND error_acknowledged IS NULL AND error_or_filter_cause IS NOT NULL;
