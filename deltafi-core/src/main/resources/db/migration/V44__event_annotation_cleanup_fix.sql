DROP PROCEDURE clean_unused_annotations(integer);
CREATE PROCEDURE clean_unused_annotations(
    IN p_limit int,
    OUT p_deleted_rows int
)
    LANGUAGE plpgsql AS $$
BEGIN
    DELETE FROM event_annotations
    WHERE ctid IN (SELECT ctid
                   FROM event_annotations ea
                   WHERE NOT EXISTS (SELECT 1
                                     FROM analytics a
                                     WHERE a.did = ea.did)
                   LIMIT p_limit);

    GET DIAGNOSTICS p_deleted_rows = ROW_COUNT;
END
$$;