-- Migration for ts_annotations
DROP TABLE IF EXISTS ts_annotations_new;
CREATE TABLE ts_annotations_new (
                                    entity_id uuid NOT NULL,
                                    entity_timestamp timestamp(6) with time zone NOT NULL,
                                    data_source text NOT NULL,
                                    key text NOT NULL,
                                    value text NOT NULL
);
SELECT create_hypertable(
               'ts_annotations_new',
               'entity_timestamp',
               chunk_time_interval => INTERVAL '7 days'
       );
ALTER TABLE ts_annotations_new
    ADD CONSTRAINT ts_annotations_new_pkey PRIMARY KEY (entity_timestamp, entity_id, key);
CREATE INDEX ON ts_annotations_new (entity_id, key);
ALTER TABLE ts_annotations_new SET (
    timescaledb.compress,
    timescaledb.compress_orderby = 'entity_timestamp DESC',
    timescaledb.compress_segmentby = 'entity_id, key',
    timescaledb.compress_chunk_time_interval = '7 days'
    );
SELECT add_compression_policy('ts_annotations_new', INTERVAL '7 days');
SELECT add_retention_policy('ts_annotations_new', INTERVAL '30 days', schedule_interval => INTERVAL '1 hour');
INSERT INTO ts_annotations_new SELECT * FROM ts_annotations;
DROP TABLE IF EXISTS ts_annotations_old;
ALTER TABLE ts_annotations RENAME TO ts_annotations_old;
ALTER TABLE ts_annotations_new RENAME TO ts_annotations;
DROP TABLE ts_annotations_old;
ALTER TABLE ts_annotations RENAME CONSTRAINT ts_annotations_new_pkey TO ts_annotations_pkey;

-- Migration for ts_cancels
DROP TABLE IF EXISTS ts_cancels_new;
CREATE TABLE ts_cancels_new (
                                id uuid NOT NULL,
                                "timestamp" timestamp(6) with time zone NOT NULL,
                                data_source text NOT NULL
);
ALTER TABLE ts_cancels_new
    ADD CONSTRAINT ts_cancels_new_pkey PRIMARY KEY (timestamp, id);
SELECT create_hypertable(
               'ts_cancels_new', by_range('timestamp', INTERVAL '7 days')
       );
ALTER TABLE ts_cancels_new SET (
    timescaledb.compress,
    timescaledb.compress_orderby = 'timestamp DESC',
    timescaledb.compress_segmentby = 'id',
    timescaledb.compress_chunk_time_interval = '7 days'
    );
INSERT INTO ts_cancels_new SELECT * FROM ts_cancels;
DROP TABLE ts_cancels;
ALTER TABLE ts_cancels_new RENAME TO ts_cancels;
ALTER TABLE ts_cancels RENAME CONSTRAINT ts_cancels_new_pkey TO ts_cancels_pkey;
CREATE INDEX idx_ts_cancels_time_ds ON ts_cancels ("timestamp" DESC, data_source);
SELECT add_compression_policy('ts_cancels', INTERVAL '7 days');
SELECT add_retention_policy('ts_cancels', INTERVAL '30 days', schedule_interval => INTERVAL '1 hour');

-- Migration for ts_egresses
DROP TABLE IF EXISTS ts_egresses_new;
CREATE TABLE ts_egresses_new (
                                 id uuid NOT NULL,
                                 "timestamp" timestamp(6) with time zone NOT NULL,
                                 egress_bytes bigint,
                                 data_source text NOT NULL,
                                 egressor text NOT NULL
);
ALTER TABLE ts_egresses_new
    ADD CONSTRAINT ts_egresses_new_pkey PRIMARY KEY (timestamp, egressor, id);
SELECT create_hypertable(
               'ts_egresses_new', by_range('timestamp', INTERVAL '7 days')
       );
ALTER TABLE ts_egresses_new SET (
    timescaledb.compress,
    timescaledb.compress_orderby = 'timestamp DESC',
    timescaledb.compress_segmentby = 'id',
    timescaledb.compress_chunk_time_interval = '7 days'
    );
INSERT INTO ts_egresses_new SELECT * FROM ts_egresses;
DROP TABLE ts_egresses;
ALTER TABLE ts_egresses_new RENAME TO ts_egresses;
ALTER TABLE ts_egresses RENAME CONSTRAINT ts_egresses_new_pkey TO ts_egresses_pkey;
CREATE INDEX idx_ts_egresses_time_ds ON ts_egresses (timestamp DESC, data_source);
SELECT add_compression_policy('ts_egresses', INTERVAL '7 days');
SELECT add_retention_policy('ts_egresses', INTERVAL '30 days', schedule_interval => INTERVAL '1 hour');


-- Migration for ts_errors
DROP TABLE IF EXISTS ts_errors_new;
CREATE TABLE ts_errors_new (
                               id uuid NOT NULL,
                               "timestamp" timestamp(6) with time zone NOT NULL,
                               data_source text NOT NULL,
                               cause text,
                               flow text,
                               action text
);
SELECT create_hypertable(
               'ts_errors_new',
               'timestamp',
               chunk_time_interval => INTERVAL '1 day',
               number_partitions => 16
       );
ALTER TABLE ts_errors_new
    ADD CONSTRAINT ts_errors_new_pkey PRIMARY KEY (timestamp, id);
CREATE INDEX ON ts_errors_new (timestamp DESC, data_source);
CREATE INDEX ON ts_errors_new (cause, timestamp DESC);
CREATE INDEX ON ts_errors_new (flow, timestamp DESC);
CREATE INDEX ON ts_errors_new (action, timestamp DESC);
CREATE INDEX idx_ts_errors_new_time_ds ON ts_errors_new (timestamp DESC, data_source);
ALTER TABLE ts_errors_new SET (
    timescaledb.compress,
    timescaledb.compress_orderby = 'timestamp DESC',
    timescaledb.compress_segmentby = 'id',
    timescaledb.compress_chunk_time_interval = '7 days'
    );
SELECT add_compression_policy('ts_errors_new', INTERVAL '7 days');
SELECT add_retention_policy('ts_errors_new', INTERVAL '30 days', schedule_interval => INTERVAL '1 hour');
INSERT INTO ts_errors_new SELECT * FROM ts_errors;
DROP TABLE ts_errors;
ALTER TABLE ts_errors_new RENAME TO ts_errors;
ALTER TABLE ts_errors RENAME CONSTRAINT ts_errors_new_pkey TO ts_errors_pkey;
ALTER INDEX idx_ts_errors_new_time_ds RENAME TO idx_ts_errors_time_ds;


-- Migration for ts_filters
DROP TABLE IF EXISTS ts_filters_new;
CREATE TABLE ts_filters_new (
                                id uuid NOT NULL,
                                "timestamp" timestamp(6) with time zone NOT NULL,
                                data_source text NOT NULL,
                                message text,
                                flow text,
                                action text
);
SELECT create_hypertable(
               'ts_filters_new',
               'timestamp',
               chunk_time_interval => INTERVAL '1 day',
               number_partitions => 16
       );
ALTER TABLE ts_filters_new
    ADD CONSTRAINT ts_filters_new_pkey PRIMARY KEY (timestamp, id);
CREATE INDEX ON ts_filters_new (message, timestamp DESC);
CREATE INDEX ON ts_filters_new (flow, timestamp DESC);
CREATE INDEX ON ts_filters_new (action, timestamp DESC);
CREATE INDEX idx_ts_filters_new_time_ds ON ts_filters_new (timestamp DESC) INCLUDE (data_source);
ALTER TABLE ts_filters_new SET (
    timescaledb.compress,
    timescaledb.compress_orderby = 'timestamp DESC',
    timescaledb.compress_segmentby = 'id',
    timescaledb.compress_chunk_time_interval = '7 days'
    );
SELECT add_compression_policy('ts_filters_new', INTERVAL '7 days');
SELECT add_retention_policy('ts_filters_new', INTERVAL '30 days', schedule_interval => INTERVAL '1 hour');
INSERT INTO ts_filters_new SELECT * FROM ts_filters;
DROP TABLE ts_filters;
ALTER TABLE ts_filters_new RENAME TO ts_filters;
ALTER TABLE ts_filters RENAME CONSTRAINT ts_filters_new_pkey TO ts_filters_pkey;
ALTER INDEX idx_ts_filters_new_time_ds RENAME TO idx_ts_filters_time_ds;

-- Migration for ts_ingresses
DROP TABLE IF EXISTS ts_ingresses_new;
CREATE TABLE ts_ingresses_new (
                                  id uuid NOT NULL,
                                  "timestamp" timestamp(6) with time zone NOT NULL,
                                  ingress_bytes bigint,
                                  count integer,
                                  survey boolean,
                                  data_source text NOT NULL
);
ALTER TABLE ONLY ts_ingresses_new
    ADD CONSTRAINT ts_ingresses_new_pkey PRIMARY KEY (timestamp, id);
SELECT create_hypertable(
               'ts_ingresses_new', by_range('timestamp', INTERVAL '7 days')
       );
ALTER TABLE ts_ingresses_new SET (
    timescaledb.compress,
    timescaledb.compress_orderby = 'timestamp DESC',
    timescaledb.compress_segmentby = 'id',
    timescaledb.compress_chunk_time_interval = '7 days'
    );
INSERT INTO ts_ingresses_new SELECT * FROM ts_ingresses;
DROP TABLE ts_ingresses;
ALTER TABLE ts_ingresses_new RENAME TO ts_ingresses;
ALTER TABLE ts_ingresses RENAME CONSTRAINT ts_ingresses_new_pkey TO ts_ingresses_pkey;
CREATE INDEX idx_ts_ingresses_time_ds ON ts_ingresses (timestamp DESC, data_source);
SELECT add_compression_policy('ts_ingresses', INTERVAL '7 days');
SELECT add_retention_policy('ts_ingresses', INTERVAL '30 days', schedule_interval => INTERVAL '1 hour');