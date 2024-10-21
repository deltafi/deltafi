DROP INDEX idx_delta_file_flows_requeue;
CREATE INDEX idx_delta_file_flows_requeue ON delta_file_flows (modified, delta_file_id) WHERE state = 'IN_FLIGHT' AND cold_queued = FALSE;