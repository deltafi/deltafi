DROP INDEX IF EXISTS idx_created;
DROP INDEX IF EXISTS idx_modified;

CREATE INDEX idx_created ON delta_files (created, stage, data_source, name, egressed, filtered, terminal, ingress_bytes);
CREATE INDEX idx_modified ON delta_files (modified, stage, data_source, name, egressed, filtered, terminal, ingress_bytes);

ALTER TABLE delta_files DROP COLUMN normalized_name;