CREATE OR REPLACE PROCEDURE clean_unused_annotations(p_limit int DEFAULT 50)
    LANGUAGE plpgsql AS $$
DECLARE
    _rows int;
BEGIN
    LOOP
        DELETE FROM event_annotations
        WHERE ctid IN (SELECT ctid
                       FROM event_annotations ea
                       WHERE NOT EXISTS (SELECT 1
                                         FROM analytics a
                                         WHERE a.did = ea.did)
                       LIMIT p_limit);

        GET DIAGNOSTICS _rows = ROW_COUNT;
        EXIT WHEN _rows = 0;
        COMMIT;
    END LOOP;
END
$$;