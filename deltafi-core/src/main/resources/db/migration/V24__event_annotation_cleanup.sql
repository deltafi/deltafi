DO $wrapper$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') THEN
        RETURN;
    END IF;

    EXECUTE $func$
    CREATE OR REPLACE FUNCTION check_and_remove_annotations()
        RETURNS TRIGGER AS $$
    BEGIN
        -- Delete annotations where the did no longer exists in analytics
        DELETE FROM event_annotations
        WHERE did = OLD.did
          AND NOT EXISTS (
            SELECT 1
            FROM analytics
            WHERE did = OLD.did
        );

        RETURN OLD;
    END;
    $$ LANGUAGE plpgsql;
    $func$;

    DROP TRIGGER IF EXISTS cleanup_orphaned_annotations ON analytics;

    CREATE TRIGGER cleanup_orphaned_annotations
        AFTER DELETE ON analytics
        FOR EACH ROW
    EXECUTE FUNCTION check_and_remove_annotations();

    DELETE FROM event_annotations ea
           WHERE NOT EXISTS (SELECT 1 FROM analytics a WHERE a.did = ea.did);
END $wrapper$;
