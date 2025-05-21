CREATE INDEX IF NOT EXISTS idx_delta_file_flows_auto_resume
    ON delta_file_flows (next_auto_resume, delta_file_id)
    WHERE state = 'ERROR' and error_acknowledged IS NULL;
