DROP INDEX IF EXISTS idx_delta_file_flows_delta_file_id;

CREATE INDEX idx_delta_file_flows_delta_file_id ON delta_file_flows (delta_file_id);