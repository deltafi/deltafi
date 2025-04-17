INSERT INTO squeeze.tables (tabschema, tabname, schedule, free_space_extra)
VALUES
    ('public', 'analytics', ('{0,30}', NULL, NULL, NULL, NULL), 30),
    ('public', 'event_annotations', ('{0,30}', NULL, NULL, NULL, NULL), 30);

-- drop unused indexes
DROP INDEX analytics_event_time_idx;
DROP INDEX idx_ea_distinct_anno_key;
DROP INDEX idx_ea_distinct_anno_val;

-- create new index for faster error cause lookup cleanup
DROP INDEX IF EXISTS errors_filters_5m_error_causes;
CREATE INDEX errors_filters_5m_error_causes ON errors_filters_5m_noanno(cause_id);
