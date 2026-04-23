-- ABOUTME: Restricts cold queue tracking triggers to rows that can affect cold_queue_entries.

DROP TRIGGER IF EXISTS trg_cold_queue_entry ON delta_file_flows;
DROP TRIGGER IF EXISTS trg_cold_queue_entry_insert ON delta_file_flows;
DROP TRIGGER IF EXISTS trg_cold_queue_entry_update ON delta_file_flows;
DROP TRIGGER IF EXISTS trg_cold_queue_entry_delete ON delta_file_flows;

CREATE TRIGGER trg_cold_queue_entry_insert
    AFTER INSERT ON delta_file_flows
    FOR EACH ROW
    WHEN (NEW.cold_queued_action IS NOT NULL)
    EXECUTE FUNCTION manage_cold_queue_entry();

CREATE TRIGGER trg_cold_queue_entry_update
    AFTER UPDATE ON delta_file_flows
    FOR EACH ROW
    WHEN (
        (OLD.cold_queued_action IS NOT NULL OR NEW.cold_queued_action IS NOT NULL)
        AND OLD.cold_queued_action IS DISTINCT FROM NEW.cold_queued_action
    )
    EXECUTE FUNCTION manage_cold_queue_entry();

CREATE TRIGGER trg_cold_queue_entry_delete
    AFTER DELETE ON delta_file_flows
    FOR EACH ROW
    WHEN (OLD.cold_queued_action IS NOT NULL)
    EXECUTE FUNCTION manage_cold_queue_entry();
