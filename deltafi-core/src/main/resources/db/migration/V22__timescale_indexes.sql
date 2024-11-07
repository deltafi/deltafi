DROP INDEX IF EXISTS idx_ts_ingresses_time_ds;
DROP INDEX IF EXISTS idx_ts_egresses_time_ds;
DROP INDEX IF EXISTS idx_ts_errors_time_ds;
DROP INDEX IF EXISTS idx_ts_filters_time_ds;

CREATE INDEX idx_ts_ingresses_time_ds ON ts_ingresses (timestamp DESC) INCLUDE (data_source);
CREATE INDEX idx_ts_egresses_time_ds ON ts_egresses (timestamp DESC) INCLUDE (data_source);
CREATE INDEX idx_ts_errors_time_ds ON ts_errors (timestamp DESC) INCLUDE (data_source);
CREATE INDEX idx_ts_filters_time_ds ON ts_filters (timestamp DESC) INCLUDE (data_source);

CREATE TABLE ts_annotations (
   entity_timestamp timestamp(6) with time zone NOT NULL,
   entity_id uuid NOT NULL,
   data_source text NOT NULL,
   key text NOT NULL,
   value text NOT NULL,
   PRIMARY KEY (entity_timestamp, entity_id, key)
);

SELECT create_hypertable('ts_annotations', 'entity_timestamp',
                         chunk_time_interval => INTERVAL '1 day');

CREATE INDEX ON ts_annotations (entity_timestamp DESC, data_source);
CREATE INDEX ON ts_annotations (entity_id, key, value);

ALTER TABLE ts_annotations SET (
    timescaledb.compress,
    timescaledb.compress_orderby = 'entity_timestamp DESC',
    timescaledb.compress_chunk_time_interval = '7 days'
    );
SELECT add_compression_policy('ts_annotations', INTERVAL '7 days');
SELECT add_retention_policy('ts_annotations', INTERVAL '30 days',
                            schedule_interval => INTERVAL '1 hour');

DROP INDEX IF EXISTS ts_ingresses_annotations_idx;
DROP INDEX IF EXISTS ts_egresses_annotations_idx;
DROP INDEX IF EXISTS ts_errors_annotations_idx;
DROP INDEX IF EXISTS ts_filters_annotations_idx;
DROP INDEX IF EXISTS ts_cancels_annotations_idx;
DROP INDEX IF EXISTS ts_ingresses_id_annotations_idx;

ALTER TABLE ts_ingresses DROP COLUMN annotations;
ALTER TABLE ts_egresses DROP COLUMN annotations;
ALTER TABLE ts_errors DROP COLUMN annotations;
ALTER TABLE ts_filters DROP COLUMN annotations;
ALTER TABLE ts_cancels DROP COLUMN annotations;
