DO $$
    BEGIN
        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'fk_delta_file_flows_delta_files'
        ) THEN
            ALTER TABLE delta_file_flows
                ADD CONSTRAINT fk_delta_file_flows_delta_files
                    FOREIGN KEY (delta_file_id)
                        REFERENCES delta_files(did)
                        ON DELETE CASCADE;
        END IF;
    END $$;

DO $$
    BEGIN
        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'fk_annotations_delta_files'
        ) THEN
            ALTER TABLE annotations
                ADD CONSTRAINT fk_annotations_delta_files
                    FOREIGN KEY (delta_file_id)
                        REFERENCES delta_files(did)
                        ON DELETE CASCADE;
        END IF;
    END $$;