ALTER TABLE delta_files
    ADD COLUMN topics text[] DEFAULT ARRAY[]::text[],
    ADD COLUMN transforms text[] DEFAULT ARRAY[]::text[],
    ADD COLUMN data_sinks text[] DEFAULT ARRAY[]::text[];

UPDATE delta_files df SET topics = ARRAY(
                                  SELECT DISTINCT unnest(dff.publish_topics)
                                  FROM delta_file_flows dff
                                  WHERE dff.delta_file_id = df.did),
                          transforms = ARRAY(
                                  SELECT DISTINCT dff.name
                                  FROM delta_file_flows dff
                                  WHERE dff.delta_file_id = df.did
                                    AND dff.type = 'TRANSFORM'),
                          data_sinks = ARRAY(
                                  SELECT DISTINCT dff.name
                                  FROM delta_file_flows dff
                                  WHERE dff.delta_file_id = df.did
                                    AND dff.type = 'DATA_SINK');

CREATE INDEX idx_delta_files_transforms ON delta_files USING gin (transforms);
CREATE INDEX idx_delta_files_data_sinks ON delta_files USING gin (data_sinks);
CREATE INDEX idx_delta_files_topics ON delta_files USING gin (topics);