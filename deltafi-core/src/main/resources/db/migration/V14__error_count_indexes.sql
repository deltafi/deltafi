DROP INDEX IF EXISTS idx_flow_state;
CREATE INDEX idx_flow_state ON delta_file_flows (state, error_acknowledged, delta_file_id);

CREATE INDEX IF NOT EXISTS idx_delta_file_flows_error_count
ON delta_file_flows (delta_file_id)
WHERE ((state = 'ERROR'::dff_state_enum) AND (error_acknowledged IS NULL));

CREATE INDEX IF NOT EXISTS delta_files_did_data_source
ON delta_files (did, data_source);
