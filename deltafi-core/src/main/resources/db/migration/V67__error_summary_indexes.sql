-- Covering index for errorSummaryByMessage queries.
-- Enables index-only scans for grouping by error_or_filter_cause with COUNT(DISTINCT delta_file_id).
CREATE INDEX IF NOT EXISTS idx_delta_file_flows_error_summary
    ON delta_file_flows (error_or_filter_cause, delta_file_id)
    WHERE state = 'ERROR' AND error_acknowledged IS NULL AND error_or_filter_cause IS NOT NULL;

-- Covering index for errorSummaryByFlow queries.
-- Enables index-only scans for grouping by flow_definition_id with COUNT(DISTINCT delta_file_id).
CREATE INDEX IF NOT EXISTS idx_delta_file_flows_error_by_flow
    ON delta_file_flows (flow_definition_id, delta_file_id)
    WHERE state = 'ERROR' AND error_acknowledged IS NULL;
