CREATE INDEX IF NOT EXISTS idx_delta_file_flows_cold_queued
    ON delta_file_flows ((actions -> (jsonb_array_length(actions) - 1) ->> 'ac'))
    WHERE state = 'IN_FLIGHT'
    AND cold_queued = TRUE;