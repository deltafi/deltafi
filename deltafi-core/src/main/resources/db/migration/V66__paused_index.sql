CREATE INDEX idx_delta_files_paused ON delta_files (did) WHERE paused = TRUE;
