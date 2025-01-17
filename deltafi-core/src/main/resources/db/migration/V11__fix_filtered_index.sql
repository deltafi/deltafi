DROP INDEX idx_delta_files_filtered;
CREATE INDEX idx_delta_files_filtered ON delta_files (filtered) WHERE (filtered = true);
