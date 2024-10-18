DROP INDEX IF EXISTS idx_delta_file_flows_requeue;

CREATE INDEX idx_delta_file_flows_requeue ON delta_file_flows (delta_file_id) WHERE state = 'IN_FLIGHT' and cold_queued = FALSE;

DROP INDEX IF EXISTS idx_delta_files_data_source_created;

CREATE INDEX idx_delta_files_data_source_created ON delta_files (data_source, created);