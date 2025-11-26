DROP MATERIALIZED VIEW IF EXISTS annotation_value_ids_in_use;
CREATE MATERIALIZED VIEW annotation_value_ids_in_use AS
SELECT DISTINCT annotation_value_id FROM analytics_5m_anno;
CREATE UNIQUE INDEX ON annotation_value_ids_in_use(annotation_value_id);

DROP MATERIALIZED VIEW IF EXISTS annotation_key_ids_in_use;
CREATE MATERIALIZED VIEW annotation_key_ids_in_use AS
SELECT DISTINCT annotation_key_id FROM analytics_5m_anno;
CREATE UNIQUE INDEX ON annotation_key_ids_in_use(annotation_key_id);

DROP MATERIALIZED VIEW IF EXISTS error_cause_ids_in_use;
CREATE MATERIALIZED VIEW error_cause_ids_in_use AS
SELECT DISTINCT cause_id FROM errors_filters_5m_noanno;
CREATE UNIQUE INDEX ON error_cause_ids_in_use(cause_id);

DROP MATERIALIZED VIEW IF EXISTS action_name_ids_in_use;
CREATE MATERIALIZED VIEW action_name_ids_in_use AS
SELECT DISTINCT action_id FROM errors_filters_5m_noanno;
CREATE UNIQUE INDEX ON action_name_ids_in_use(action_id);

DROP MATERIALIZED VIEW IF EXISTS event_group_ids_in_use;
CREATE MATERIALIZED VIEW event_group_ids_in_use AS
SELECT DISTINCT event_group_id FROM analytics_5m_noanno;
CREATE UNIQUE INDEX ON event_group_ids_in_use(event_group_id);
