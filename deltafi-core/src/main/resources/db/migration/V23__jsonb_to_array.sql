ALTER TABLE delta_files
    ADD COLUMN parent_dids_array uuid[],
    ADD COLUMN child_dids_array uuid[],
    ADD COLUMN content_object_ids_array uuid[];

UPDATE delta_files
SET parent_dids_array = ARRAY(SELECT (jsonb_array_elements_text(parent_dids::jsonb))::uuid),
    child_dids_array = ARRAY(SELECT (jsonb_array_elements_text(child_dids::jsonb))::uuid),
    content_object_ids_array = ARRAY(SELECT (jsonb_array_elements_text(content_object_ids::jsonb))::uuid);

ALTER TABLE delta_files
    DROP COLUMN parent_dids,
    DROP COLUMN child_dids,
    DROP COLUMN content_object_ids;

ALTER TABLE delta_files
    RENAME COLUMN parent_dids_array TO parent_dids;
ALTER TABLE delta_files
    RENAME COLUMN child_dids_array TO child_dids;
ALTER TABLE delta_files
    RENAME COLUMN content_object_ids_array TO content_object_ids;

ALTER TABLE delta_file_flows
    ADD COLUMN publish_topics_array text[],
    ADD COLUMN pending_annotations_array text[],
    ADD COLUMN pending_actions_array text[];

UPDATE delta_file_flows
SET publish_topics_array = ARRAY(SELECT jsonb_array_elements_text(publish_topics::jsonb)),
    pending_annotations_array = ARRAY(SELECT jsonb_array_elements_text(pending_annotations::jsonb)),
    pending_actions_array = ARRAY(SELECT jsonb_array_elements_text(pending_actions::jsonb));

DROP INDEX idx_flow;
CREATE INDEX idx_flow ON delta_file_flows (delta_file_id, state, name, test_mode);

ALTER TABLE delta_file_flows
    DROP COLUMN publish_topics,
    DROP COLUMN pending_annotations,
    DROP COLUMN pending_actions;

ALTER TABLE delta_file_flows RENAME COLUMN publish_topics_array TO publish_topics;
ALTER TABLE delta_file_flows RENAME COLUMN pending_annotations_array TO pending_annotations;
ALTER TABLE delta_file_flows RENAME COLUMN pending_actions_array TO pending_actions;
