-- add did to the index so the query doesn't have to hit the table
DROP INDEX idx_delta_files_content_deleted_created_data_source;
CREATE INDEX idx_delta_files_content_deleted_created_data_source ON delta_files (created, data_source, did) WHERE (content_deleted IS NOT NULL);

-- indexes were not hitting because of explicit state enum cast
DROP INDEX idx_delta_file_flows_requeue;
CREATE INDEX idx_delta_file_flows_requeue ON delta_file_flows (modified, delta_file_id) WHERE state = 'IN_FLIGHT' AND cold_queued = FALSE;
DROP INDEX idx_delta_file_flows_error_count;
CREATE INDEX idx_delta_file_flows_error_count ON delta_file_flows (delta_file_id) WHERE state = 'ERROR' AND error_acknowledged IS NULL;