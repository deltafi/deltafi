DO $wrapper$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') THEN
        RETURN;
    END IF;

    EXECUTE $func$
    CREATE OR REPLACE PROCEDURE clean_unused_annotations(p_limit int DEFAULT 20000)
        LANGUAGE plpgsql AS $$
    DECLARE
        _rows int;
    BEGIN
        LOOP
            SET LOCAL work_mem = '128MB';
            WITH doomed AS (
                SELECT did
                FROM event_annotations ea
                         LEFT JOIN analytics a USING (did)
                WHERE a.did IS NULL
                LIMIT p_limit
            )
            DELETE FROM event_annotations ea
                USING doomed d
            WHERE ea.did = d.did;

            GET DIAGNOSTICS _rows = ROW_COUNT;
            EXIT WHEN _rows = 0;
        END LOOP;
    END
    $$;
    $func$;
END $wrapper$;
