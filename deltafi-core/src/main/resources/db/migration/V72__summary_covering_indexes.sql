-- Replace error summary indexes from V67/V68 with covering versions that include modified
-- to support time-range filters without heap fetches.
DROP INDEX IF EXISTS idx_delta_file_flows_error_summary;
DROP INDEX IF EXISTS idx_delta_file_flows_error_by_flow;
DROP INDEX IF EXISTS idx_delta_file_flows_error_msg_by_flow;

CREATE INDEX IF NOT EXISTS idx_delta_file_flows_error_summary
    ON delta_file_flows (error_or_filter_cause, delta_file_id, modified)
    WHERE state = 'ERROR' AND error_acknowledged IS NULL AND error_or_filter_cause IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_delta_file_flows_error_by_flow
    ON delta_file_flows (flow_definition_id, delta_file_id, modified)
    WHERE state = 'ERROR' AND error_acknowledged IS NULL;

CREATE INDEX IF NOT EXISTS idx_delta_file_flows_error_msg_by_flow
    ON delta_file_flows (error_or_filter_cause, flow_definition_id, delta_file_id, modified)
    WHERE state = 'ERROR' AND error_acknowledged IS NULL AND error_or_filter_cause IS NOT NULL;

-- Covering indexes for filtered summary queries.
CREATE INDEX IF NOT EXISTS idx_delta_file_flows_filtered_summary
    ON delta_file_flows (error_or_filter_cause, delta_file_id, modified)
    WHERE state = 'FILTERED' AND error_or_filter_cause IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_delta_file_flows_filtered_by_flow
    ON delta_file_flows (flow_definition_id, delta_file_id, modified)
    WHERE state = 'FILTERED';

CREATE INDEX IF NOT EXISTS idx_delta_file_flows_filtered_msg_by_flow
    ON delta_file_flows (error_or_filter_cause, flow_definition_id, delta_file_id, modified)
    WHERE state = 'FILTERED' AND error_or_filter_cause IS NOT NULL;
