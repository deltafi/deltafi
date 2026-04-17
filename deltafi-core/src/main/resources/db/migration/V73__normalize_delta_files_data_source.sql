-- ABOUTME: Normalizes delta_files.data_source from text to integer FK referencing flow_definitions

-- Insert any data_source values that don't already exist in flow_definitions.
-- Most will already exist from their ingress flows. Any that don't (e.g., "joined:X"
-- synthetic names) are inserted as REST_DATA_SOURCE by convention.
-- After migration, new DeltaFiles will always store the correct flow_definition_id.
INSERT INTO flow_definitions (name, type)
SELECT DISTINCT df.data_source, 'REST_DATA_SOURCE'::dff_type_enum
FROM delta_files df
WHERE df.data_source IS NOT NULL
AND NOT EXISTS (
    SELECT 1 FROM flow_definitions fd WHERE fd.name = df.data_source
)
ON CONFLICT DO NOTHING;

-- Add the new column
ALTER TABLE delta_files ADD COLUMN data_source_id INTEGER;

-- Backfill from flow_definitions. LIMIT 1 is needed because flow_definitions has a
-- unique constraint on (name, type), not name alone — the same name may appear with
-- different types. The arbitrary pick is acceptable for the migration; after this,
-- new DeltaFiles will always store the correct flow_definition_id from the ingress flow.
UPDATE delta_files df
SET data_source_id = (
    SELECT fd.id FROM flow_definitions fd WHERE fd.name = df.data_source LIMIT 1
)
WHERE df.data_source IS NOT NULL;

-- Set NOT NULL now that all rows are populated
ALTER TABLE delta_files ALTER COLUMN data_source_id SET NOT NULL;

-- Drop old indexes that reference the text data_source column
DROP INDEX IF EXISTS idx_delta_files_data_source_created;
DROP INDEX IF EXISTS idx_delta_files_content_deleted_created_data_source;
DROP INDEX IF EXISTS idx_delta_files_content_deletable;
DROP INDEX IF EXISTS idx_created;
DROP INDEX IF EXISTS idx_modified;

-- Recreate indexes with data_source_id instead of data_source
CREATE INDEX idx_delta_files_data_source_created ON delta_files (data_source_id, created);
CREATE INDEX idx_delta_files_content_deleted_created_data_source ON delta_files (created, data_source_id, did) WHERE (content_deleted IS NOT NULL);
CREATE INDEX idx_delta_files_content_deletable ON delta_files (modified, data_source_id) WHERE (content_deletable = true);
CREATE INDEX idx_created ON delta_files (created, stage, data_source_id, name, egressed, filtered, terminal, ingress_bytes);
CREATE INDEX idx_modified ON delta_files (modified, stage, data_source_id, name, egressed, filtered, terminal, ingress_bytes);

-- Drop the old text column
ALTER TABLE delta_files DROP COLUMN data_source;
