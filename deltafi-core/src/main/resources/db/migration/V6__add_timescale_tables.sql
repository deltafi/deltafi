CREATE EXTENSION IF NOT EXISTS timescaledb;

CREATE TABLE ts_errors (
                        "timestamp" timestamp(6) with time zone NOT NULL,
                        data_source text NOT NULL,
                        id uuid NOT NULL,
                        annotations jsonb,
                        cause text,
                        flow text,
                        action text
);

ALTER TABLE ONLY ts_errors
    ADD CONSTRAINT ts_errors_pkey PRIMARY KEY ("timestamp", data_source, id);

SELECT create_hypertable('ts_errors', 'timestamp', number_partitions => 4, chunk_time_interval => INTERVAL '1 day', if_not_exists => TRUE);
ALTER TABLE ts_errors SET (
    timescaledb.compress,
    timescaledb.compress_orderby = 'timestamp DESC',
    timescaledb.compress_chunk_time_interval = '7 days');
SELECT add_compression_policy('ts_errors', INTERVAL '7 days');
SELECT add_retention_policy('ts_errors', INTERVAL '30 days', schedule_interval => INTERVAL '1 hour');

CREATE INDEX ON ts_errors (timestamp DESC, data_source);
CREATE INDEX ON ts_errors (cause, timestamp DESC);
CREATE INDEX ON ts_errors (flow, timestamp DESC);
CREATE INDEX ON ts_errors (action, timestamp DESC);
CREATE INDEX ON ts_errors USING GIN (annotations);

CREATE TABLE ts_filters (
                           "timestamp" timestamp(6) with time zone NOT NULL,
                           data_source text NOT NULL,
                           id uuid NOT NULL,
                           annotations jsonb,
                           message text,
                           flow text,
                           action text
);

ALTER TABLE ONLY ts_filters
    ADD CONSTRAINT ts_filters_pkey PRIMARY KEY ("timestamp", data_source, id);

SELECT create_hypertable('ts_filters', 'timestamp', number_partitions => 4, chunk_time_interval => INTERVAL '1 day', if_not_exists => TRUE);
ALTER TABLE ts_filters SET (
    timescaledb.compress,
    timescaledb.compress_orderby = 'timestamp DESC',
    timescaledb.compress_chunk_time_interval = '7 days');
SELECT add_compression_policy('ts_filters', INTERVAL '7 days');
SELECT add_retention_policy('ts_filters', INTERVAL '30 days', schedule_interval => INTERVAL '1 hour');

CREATE INDEX ON ts_filters (timestamp DESC, data_source);
CREATE INDEX ON ts_filters (message, timestamp DESC);
CREATE INDEX ON ts_filters (flow, timestamp DESC);
CREATE INDEX ON ts_filters (action, timestamp DESC);
CREATE INDEX ON ts_filters USING GIN (annotations);

CREATE TABLE ts_ingresses (
                             "timestamp" timestamp(6) with time zone NOT NULL,
                             data_source text NOT NULL,
                             id uuid NOT NULL,
                             ingress_bytes bigint,
                             annotations jsonb,
                             "count" integer,
                             survey boolean
);

ALTER TABLE ONLY ts_ingresses
    ADD CONSTRAINT ts_ingresses_pkey PRIMARY KEY ("timestamp", data_source, id);

SELECT create_hypertable('ts_ingresses', 'timestamp', number_partitions => 4, chunk_time_interval => INTERVAL '1 day', if_not_exists => TRUE);
ALTER TABLE ts_ingresses SET (
    timescaledb.compress,
    timescaledb.compress_orderby = 'timestamp DESC',
    timescaledb.compress_chunk_time_interval = '7 days');
SELECT add_compression_policy('ts_ingresses', INTERVAL '7 days');
SELECT add_retention_policy('ts_ingresses', INTERVAL '30 days', schedule_interval => INTERVAL '1 hour');

CREATE INDEX ON ts_ingresses (timestamp DESC, data_source);
CREATE INDEX ON ts_ingresses USING GIN (annotations);
CREATE INDEX ON ts_ingresses (id) INCLUDE (annotations);

CREATE TABLE ts_egresses (
                           "timestamp" timestamp(6) with time zone NOT NULL,
                           data_source text NOT NULL,
                           id uuid NOT NULL,
                           egressor text NOT NULL,
                           egress_bytes bigint,
                           annotations jsonb
);

ALTER TABLE ONLY ts_egresses
    ADD CONSTRAINT ts_egresses_pkey PRIMARY KEY ("timestamp", data_source, id);

SELECT create_hypertable('ts_egresses', 'timestamp', number_partitions => 4, chunk_time_interval => INTERVAL '1 day', if_not_exists => TRUE);
ALTER TABLE ts_egresses SET (
    timescaledb.compress,
    timescaledb.compress_orderby = 'timestamp DESC',
    timescaledb.compress_chunk_time_interval = '7 days');
SELECT add_compression_policy('ts_egresses', INTERVAL '7 days');
SELECT add_retention_policy('ts_egresses', INTERVAL '30 days', schedule_interval => INTERVAL '1 hour');

CREATE INDEX ON ts_egresses (timestamp DESC, data_source);
CREATE INDEX ON ts_egresses (egressor, timestamp DESC);
CREATE INDEX ON ts_egresses USING GIN (annotations);

CREATE TABLE ts_cancels (
                             "timestamp" timestamp(6) with time zone NOT NULL,
                             data_source text NOT NULL,
                             id uuid NOT NULL,
                             annotations jsonb
);

ALTER TABLE ONLY ts_cancels
    ADD CONSTRAINT ts_cancels_pkey PRIMARY KEY ("timestamp", data_source, id);

SELECT create_hypertable('ts_cancels', 'timestamp', number_partitions => 4, chunk_time_interval => INTERVAL '1 day', if_not_exists => TRUE);
ALTER TABLE ts_cancels SET (
    timescaledb.compress,
    timescaledb.compress_orderby = 'timestamp DESC',
    timescaledb.compress_chunk_time_interval = '7 days');
SELECT add_compression_policy('ts_cancels', INTERVAL '7 days');
SELECT add_retention_policy('ts_cancels', INTERVAL '30 days', schedule_interval => INTERVAL '1 hour');

CREATE INDEX ON ts_cancels (timestamp DESC, data_source);
CREATE INDEX ON ts_cancels USING GIN (annotations);