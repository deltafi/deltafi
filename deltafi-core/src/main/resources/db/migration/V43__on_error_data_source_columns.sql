-- Add ON_ERROR_DATA_SOURCE to the enum type
ALTER TYPE dff_type_enum ADD VALUE 'ON_ERROR_DATA_SOURCE';

ALTER TABLE flows ADD COLUMN error_message_regex text;
ALTER TABLE flows ADD COLUMN source_filters jsonb;
ALTER TABLE flows ADD COLUMN metadata_filters jsonb;
ALTER TABLE flows ADD COLUMN annotation_filters jsonb;
ALTER TABLE flows ADD COLUMN include_source_metadata_regex jsonb;
ALTER TABLE flows ADD COLUMN include_source_annotations_regex jsonb;

-- Update the type constraint to include ON_ERROR_DATA_SOURCE
ALTER TABLE flows DROP CONSTRAINT flows_type_check;
ALTER TABLE flows ADD CONSTRAINT flows_type_check 
  CHECK (type in ('REST_DATA_SOURCE','TIMED_DATA_SOURCE','ON_ERROR_DATA_SOURCE','TRANSFORM','DATA_SINK'));