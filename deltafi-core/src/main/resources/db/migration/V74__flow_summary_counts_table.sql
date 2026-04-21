-- ABOUTME: Trigger-maintained flow_summary_counts table for fast error/filter summary queries.
-- ABOUTME: Tracks COUNT(*) per (state, acknowledged, flow_definition_id, error_or_filter_cause).

CREATE TABLE IF NOT EXISTS flow_summary_counts (
    state                 dff_state_enum NOT NULL,
    acknowledged          boolean        NOT NULL,
    flow_definition_id    integer        NOT NULL REFERENCES flow_definitions(id) ON DELETE CASCADE,
    error_or_filter_cause text,
    error_count           bigint         NOT NULL,
    CONSTRAINT flow_summary_counts_state_check CHECK (state IN ('ERROR', 'FILTERED')),
    CONSTRAINT flow_summary_counts_ack_filter_check CHECK (state = 'ERROR' OR acknowledged = false),
    CONSTRAINT flow_summary_counts_positive_count CHECK (error_count >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_flow_summary_counts_key
    ON flow_summary_counts (state, acknowledged, flow_definition_id, error_or_filter_cause)
    NULLS NOT DISTINCT;

CREATE INDEX IF NOT EXISTS idx_flow_summary_counts_by_cause
    ON flow_summary_counts (state, acknowledged, error_or_filter_cause);

-- Returns true if a delta_file_flows row should be counted, and sets the summary key via OUT params.
CREATE OR REPLACE FUNCTION tracked_summary_state(
    p_state              dff_state_enum,
    p_error_acknowledged timestamp(6) with time zone,
    OUT tracked          boolean,
    OUT out_state        dff_state_enum,
    OUT out_acknowledged boolean
) AS $$
BEGIN
    IF p_state = 'ERROR' THEN
        tracked := true;
        out_state := 'ERROR';
        out_acknowledged := p_error_acknowledged IS NOT NULL;
    ELSIF p_state = 'FILTERED' THEN
        tracked := true;
        out_state := 'FILTERED';
        out_acknowledged := false;
    ELSE
        tracked := false;
    END IF;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

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
            UPDATE flow_summary_counts
               SET error_count = error_count - 1
             WHERE state = old_state
               AND acknowledged = old_acknowledged
               AND flow_definition_id = OLD.flow_definition_id
               AND error_or_filter_cause IS NOT DISTINCT FROM OLD.error_or_filter_cause;
            DELETE FROM flow_summary_counts
             WHERE state = old_state
               AND acknowledged = old_acknowledged
               AND flow_definition_id = OLD.flow_definition_id
               AND error_or_filter_cause IS NOT DISTINCT FROM OLD.error_or_filter_cause
               AND error_count = 0;
        END IF;
        RETURN OLD;
    END IF;

    SELECT tracked, out_state, out_acknowledged
      INTO new_tracked, new_state, new_acknowledged
      FROM tracked_summary_state(NEW.state, NEW.error_acknowledged);

    IF TG_OP = 'INSERT' THEN
        IF new_tracked THEN
            INSERT INTO flow_summary_counts (state, acknowledged, flow_definition_id, error_or_filter_cause, error_count)
            VALUES (new_state, new_acknowledged, NEW.flow_definition_id, NEW.error_or_filter_cause, 1)
            ON CONFLICT (state, acknowledged, flow_definition_id, error_or_filter_cause) DO UPDATE
            SET error_count = flow_summary_counts.error_count + 1;
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
        UPDATE flow_summary_counts
           SET error_count = error_count - 1
         WHERE state = old_state
           AND acknowledged = old_acknowledged
           AND flow_definition_id = OLD.flow_definition_id
           AND error_or_filter_cause IS NOT DISTINCT FROM OLD.error_or_filter_cause;
        DELETE FROM flow_summary_counts
         WHERE state = old_state
           AND acknowledged = old_acknowledged
           AND flow_definition_id = OLD.flow_definition_id
           AND error_or_filter_cause IS NOT DISTINCT FROM OLD.error_or_filter_cause
           AND error_count = 0;
    END IF;

    IF new_tracked THEN
        INSERT INTO flow_summary_counts (state, acknowledged, flow_definition_id, error_or_filter_cause, error_count)
        VALUES (new_state, new_acknowledged, NEW.flow_definition_id, NEW.error_or_filter_cause, 1)
        ON CONFLICT (state, acknowledged, flow_definition_id, error_or_filter_cause) DO UPDATE
        SET error_count = flow_summary_counts.error_count + 1;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_flow_summary_count ON delta_file_flows;
DROP TRIGGER IF EXISTS trg_flow_summary_count_insert ON delta_file_flows;
DROP TRIGGER IF EXISTS trg_flow_summary_count_update ON delta_file_flows;
DROP TRIGGER IF EXISTS trg_flow_summary_count_delete ON delta_file_flows;

-- Split per-operation with WHEN clauses so writes that don't touch a tracking dimension
-- (most action/timestamp updates on a non-ERROR/FILTERED flow) don't pay the function dispatch cost.
CREATE TRIGGER trg_flow_summary_count_insert
    AFTER INSERT ON delta_file_flows
    FOR EACH ROW
    WHEN (NEW.state IN ('ERROR', 'FILTERED'))
    EXECUTE FUNCTION manage_flow_summary_count();

CREATE TRIGGER trg_flow_summary_count_update
    AFTER UPDATE ON delta_file_flows
    FOR EACH ROW
    WHEN (
        (OLD.state IN ('ERROR', 'FILTERED') OR NEW.state IN ('ERROR', 'FILTERED'))
        AND (
            OLD.state                   IS DISTINCT FROM NEW.state
            OR OLD.error_acknowledged   IS DISTINCT FROM NEW.error_acknowledged
            OR OLD.error_or_filter_cause IS DISTINCT FROM NEW.error_or_filter_cause
            OR OLD.flow_definition_id   IS DISTINCT FROM NEW.flow_definition_id
        )
    )
    EXECUTE FUNCTION manage_flow_summary_count();

CREATE TRIGGER trg_flow_summary_count_delete
    AFTER DELETE ON delta_file_flows
    FOR EACH ROW
    WHEN (OLD.state IN ('ERROR', 'FILTERED'))
    EXECUTE FUNCTION manage_flow_summary_count();

-- Backfill after trigger creation: the trigger's SHARE ROW EXCLUSIVE lock on delta_file_flows
-- blocks concurrent writes until COMMIT, so the snapshot these aggregates see is consistent
-- with the state the trigger will maintain going forward.
INSERT INTO flow_summary_counts (state, acknowledged, flow_definition_id, error_or_filter_cause, error_count)
SELECT 'ERROR', error_acknowledged IS NOT NULL, flow_definition_id, error_or_filter_cause, COUNT(*)
  FROM delta_file_flows
 WHERE state = 'ERROR'
 GROUP BY error_acknowledged IS NOT NULL, flow_definition_id, error_or_filter_cause
ON CONFLICT (state, acknowledged, flow_definition_id, error_or_filter_cause) DO NOTHING;

INSERT INTO flow_summary_counts (state, acknowledged, flow_definition_id, error_or_filter_cause, error_count)
SELECT 'FILTERED', false, flow_definition_id, error_or_filter_cause, COUNT(*)
  FROM delta_file_flows
 WHERE state = 'FILTERED'
 GROUP BY flow_definition_id, error_or_filter_cause
ON CONFLICT (state, acknowledged, flow_definition_id, error_or_filter_cause) DO NOTHING;

-- Idempotent rebuild procedure for drift recovery. Truncates and re-aggregates in one transaction.
CREATE OR REPLACE FUNCTION rebuild_flow_summary_counts() RETURNS void AS $$
BEGIN
    LOCK TABLE delta_file_flows IN SHARE ROW EXCLUSIVE MODE;
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

-- Drop the summary-purpose partial indexes from V67/V72. Their only callers (errorSummaryByFlow,
-- errorSummaryByMessage, filteredSummaryByFlow, filteredSummaryByMessage, and their drill-downs)
-- now read from flow_summary_counts instead. Other queries that touch delta_file_flows with state
-- = 'ERROR' anchor on delta_file_id and use idx_delta_file_flows_error_count or the general FK index.
DROP INDEX IF EXISTS idx_delta_file_flows_error_by_flow;
DROP INDEX IF EXISTS idx_delta_file_flows_error_summary;
DROP INDEX IF EXISTS idx_delta_file_flows_error_msg_by_flow;
DROP INDEX IF EXISTS idx_delta_file_flows_filtered_by_flow;
DROP INDEX IF EXISTS idx_delta_file_flows_filtered_summary;
DROP INDEX IF EXISTS idx_delta_file_flows_filtered_msg_by_flow;
