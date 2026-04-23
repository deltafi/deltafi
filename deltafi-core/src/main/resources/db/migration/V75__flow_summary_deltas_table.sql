-- ABOUTME: Decouples flow_summary_counts maintenance from the trigger's outer transaction.
-- ABOUTME: Triggers append to flow_summary_deltas; a background worker rolls deltas into summary every second.

-- Append-only log of count changes. Writers are ordinary INSERTs with no ON CONFLICT, so no
-- row-level contention across concurrent bulk operations that happen to touch the same (flow, cause)
-- summary key — which was the source of the lock convoys we saw after V74.
CREATE TABLE IF NOT EXISTS flow_summary_deltas (
    id                    bigserial PRIMARY KEY,
    state                 dff_state_enum NOT NULL,
    acknowledged          boolean        NOT NULL,
    flow_definition_id    integer        NOT NULL,
    error_or_filter_cause text,
    delta                 integer        NOT NULL CHECK (delta IN (1, -1))
);

-- Trigger now writes to flow_summary_deltas (append-only) instead of updating flow_summary_counts
-- directly. The row lock on the summary table is no longer held for the duration of the outer
-- transaction, so bulk operations (auto-resume batches, mass acknowledge, etc.) don't block incoming
-- ingest that targets the same (flow, cause).
CREATE OR REPLACE FUNCTION manage_flow_summary_count()
RETURNS TRIGGER AS $$
DECLARE
    old_tracked      boolean;
    old_state        dff_state_enum;
    old_acknowledged boolean;
    new_tracked      boolean;
    new_state        dff_state_enum;
    new_acknowledged boolean;
BEGIN
    IF TG_OP = 'DELETE' THEN
        SELECT tracked, out_state, out_acknowledged
          INTO old_tracked, old_state, old_acknowledged
          FROM tracked_summary_state(OLD.state, OLD.error_acknowledged);
        IF old_tracked THEN
            INSERT INTO flow_summary_deltas (state, acknowledged, flow_definition_id, error_or_filter_cause, delta)
            VALUES (old_state, old_acknowledged, OLD.flow_definition_id, OLD.error_or_filter_cause, -1);
        END IF;
        RETURN OLD;
    END IF;

    SELECT tracked, out_state, out_acknowledged
      INTO new_tracked, new_state, new_acknowledged
      FROM tracked_summary_state(NEW.state, NEW.error_acknowledged);

    IF TG_OP = 'INSERT' THEN
        IF new_tracked THEN
            INSERT INTO flow_summary_deltas (state, acknowledged, flow_definition_id, error_or_filter_cause, delta)
            VALUES (new_state, new_acknowledged, NEW.flow_definition_id, NEW.error_or_filter_cause, 1);
        END IF;
        RETURN NEW;
    END IF;

    -- UPDATE
    SELECT tracked, out_state, out_acknowledged
      INTO old_tracked, old_state, old_acknowledged
      FROM tracked_summary_state(OLD.state, OLD.error_acknowledged);

    IF old_tracked = new_tracked
        AND old_state IS NOT DISTINCT FROM new_state
        AND old_acknowledged IS NOT DISTINCT FROM new_acknowledged
        AND OLD.flow_definition_id    IS NOT DISTINCT FROM NEW.flow_definition_id
        AND OLD.error_or_filter_cause IS NOT DISTINCT FROM NEW.error_or_filter_cause THEN
        RETURN NEW;
    END IF;

    IF old_tracked THEN
        INSERT INTO flow_summary_deltas (state, acknowledged, flow_definition_id, error_or_filter_cause, delta)
        VALUES (old_state, old_acknowledged, OLD.flow_definition_id, OLD.error_or_filter_cause, -1);
    END IF;

    IF new_tracked THEN
        INSERT INTO flow_summary_deltas (state, acknowledged, flow_definition_id, error_or_filter_cause, delta)
        VALUES (new_state, new_acknowledged, NEW.flow_definition_id, NEW.error_or_filter_cause, 1);
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Consumer function. Drains a bounded batch of deltas and merges them into flow_summary_counts.
-- FOR UPDATE SKIP LOCKED lets multiple consumer instances run safely without blocking each other.
-- Summary row locks are only held for the short duration of this function, not the app's transaction.
CREATE OR REPLACE FUNCTION consume_flow_summary_deltas(batch_size integer DEFAULT 10000)
RETURNS integer AS $$
DECLARE
    processed integer;
BEGIN
    -- Stage the per-key net delta for this batch in a temp table. We need two passes against it
    -- (UPDATE existing rows, then INSERT new rows for positive nets) because the non-negative
    -- CHECK constraint on flow_summary_counts.error_count fires on the proposed INSERT row before
    -- ON CONFLICT can turn it into a DO UPDATE — so we can't simply INSERT ... ON CONFLICT with
    -- a negative value.
    CREATE TEMP TABLE IF NOT EXISTS flow_summary_deltas_batch (
        state                 dff_state_enum NOT NULL,
        acknowledged          boolean        NOT NULL,
        flow_definition_id    integer        NOT NULL,
        error_or_filter_cause text,
        net                   bigint         NOT NULL
    ) ON COMMIT DROP;
    TRUNCATE flow_summary_deltas_batch;

    WITH to_consume AS (
        SELECT id FROM flow_summary_deltas
        ORDER BY id
        LIMIT batch_size
        FOR UPDATE SKIP LOCKED
    ),
    consumed AS (
        DELETE FROM flow_summary_deltas
        WHERE id IN (SELECT id FROM to_consume)
        RETURNING state, acknowledged, flow_definition_id, error_or_filter_cause, delta
    )
    INSERT INTO flow_summary_deltas_batch (state, acknowledged, flow_definition_id, error_or_filter_cause, net)
    SELECT state, acknowledged, flow_definition_id, error_or_filter_cause, SUM(delta)
    FROM consumed
    GROUP BY state, acknowledged, flow_definition_id, error_or_filter_cause
    HAVING SUM(delta) <> 0;

    GET DIAGNOSTICS processed = ROW_COUNT;

    IF processed = 0 THEN
        RETURN 0;
    END IF;

    -- Apply net to rows that already exist. GREATEST clips to 0 if a decrement would go negative.
    UPDATE flow_summary_counts fsc
       SET error_count = GREATEST(fsc.error_count + b.net, 0)
      FROM flow_summary_deltas_batch b
     WHERE fsc.state               = b.state
       AND fsc.acknowledged         = b.acknowledged
       AND fsc.flow_definition_id   = b.flow_definition_id
       AND fsc.error_or_filter_cause IS NOT DISTINCT FROM b.error_or_filter_cause;

    -- Insert rows for positive nets that had no existing row. ON CONFLICT DO NOTHING handles races
    -- where a concurrent worker (or the UPDATE above) just created the row.
    INSERT INTO flow_summary_counts (state, acknowledged, flow_definition_id, error_or_filter_cause, error_count)
    SELECT b.state, b.acknowledged, b.flow_definition_id, b.error_or_filter_cause, b.net
      FROM flow_summary_deltas_batch b
     WHERE b.net > 0
    ON CONFLICT (state, acknowledged, flow_definition_id, error_or_filter_cause) DO NOTHING;

    DELETE FROM flow_summary_counts WHERE error_count = 0;

    RETURN processed;
END;
$$ LANGUAGE plpgsql;

-- Rebuild must also drain pending deltas, otherwise they'd be re-applied on top of the
-- freshly rebuilt snapshot and double-count.
CREATE OR REPLACE FUNCTION rebuild_flow_summary_counts() RETURNS void AS $$
BEGIN
    LOCK TABLE delta_file_flows IN SHARE ROW EXCLUSIVE MODE;
    DELETE FROM flow_summary_deltas;
    TRUNCATE TABLE flow_summary_counts;

    INSERT INTO flow_summary_counts (state, acknowledged, flow_definition_id, error_or_filter_cause, error_count)
    SELECT 'ERROR', error_acknowledged IS NOT NULL, flow_definition_id, error_or_filter_cause, COUNT(*)
      FROM delta_file_flows
     WHERE state = 'ERROR'
     GROUP BY error_acknowledged IS NOT NULL, flow_definition_id, error_or_filter_cause;

    INSERT INTO flow_summary_counts (state, acknowledged, flow_definition_id, error_or_filter_cause, error_count)
    SELECT 'FILTERED', false, flow_definition_id, error_or_filter_cause, COUNT(*)
      FROM delta_file_flows
     WHERE state = 'FILTERED'
     GROUP BY flow_definition_id, error_or_filter_cause;
END;
$$ LANGUAGE plpgsql;
