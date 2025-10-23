CREATE INDEX IF NOT EXISTS idx_delta_file_flows_cold_queued_cnt
    ON delta_file_flows (cold_queued_action)
    WHERE state = 'IN_FLIGHT'
    AND cold_queued_action IS NOT NULL;
