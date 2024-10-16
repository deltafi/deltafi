DROP INDEX IF EXISTS idx_delta_files_content_deleted_created;

CREATE INDEX idx_delta_files_content_deleted_created_data_source
    ON delta_files (created, data_source)
    WHERE content_deleted IS NOT NULL;