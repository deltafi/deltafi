-- Add created and modified columns
ALTER TABLE lookup_tables
    ADD COLUMN created timestamp(6) with time zone DEFAULT now(),
    ADD COLUMN modified timestamp(6) with time zone DEFAULT now();

-- Backfill existing rows (optional: use last_refresh as a reasonable estimate, or just now())
UPDATE lookup_tables
SET created = COALESCE(last_refresh, now()),
    modified = COALESCE(last_refresh, now());

-- Generic trigger function for child tables to update their parent row in lookup_tables
CREATE OR REPLACE FUNCTION update_lookup_table_modified_on_child_change()
    RETURNS TRIGGER AS $$
BEGIN
    UPDATE lookup_tables
    SET modified = now()
    WHERE name = TG_TABLE_NAME;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Create triggers for all existing lookup tables
DO $$
    DECLARE
        table_record RECORD;
        trigger_name TEXT;
    BEGIN
        FOR table_record IN SELECT name FROM lookup_tables LOOP
                trigger_name := table_record.name || '_notify_parent_modified';

                -- Check if the table actually exists before creating trigger
                IF EXISTS (
                    SELECT 1 FROM information_schema.tables
                    WHERE table_schema = 'public'
                      AND table_name = table_record.name
                ) THEN
                    -- Check if trigger already exists
                    IF NOT EXISTS (
                        SELECT 1 FROM pg_trigger WHERE tgname = trigger_name
                    ) THEN
                        EXECUTE format(
                                'CREATE TRIGGER %I
                                    AFTER INSERT OR UPDATE OR DELETE ON %I
                                    FOR EACH STATEMENT
                                    EXECUTE FUNCTION update_lookup_table_modified_on_child_change()',
                                trigger_name,
                                table_record.name
                                );
                    END IF;
                END IF;
            END LOOP;
    END $$;