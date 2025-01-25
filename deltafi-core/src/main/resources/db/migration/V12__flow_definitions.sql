CREATE TABLE flow_definitions (
                                  id SERIAL PRIMARY KEY,
                                  name TEXT NOT NULL,
                                  type dff_type_enum NOT NULL,
                                  UNIQUE(name, type)
);

CREATE INDEX idx_flow_definitions_name_type ON flow_definitions(name, type);

INSERT INTO flow_definitions (name, type)
SELECT DISTINCT name, type
FROM delta_file_flows;

ALTER TABLE delta_file_flows
    ADD COLUMN flow_definition_id INTEGER;

UPDATE delta_file_flows dff
SET flow_definition_id = fd.id
FROM flow_definitions fd
WHERE dff.name = fd.name
  AND dff.type = fd.type;

ALTER TABLE delta_file_flows
    ALTER COLUMN flow_definition_id SET NOT NULL;

ALTER TABLE delta_file_flows
    DROP COLUMN name,
    DROP COLUMN type;
