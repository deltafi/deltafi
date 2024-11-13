CREATE EXTENSION IF NOT EXISTS timescaledb;
SET default_toast_compression=lz4;

-- annotations
DROP TABLE IF EXISTS annotations;
CREATE TABLE annotations (
    id uuid NOT NULL,
    delta_file_id uuid,
    key text,
    value text
);
ALTER TABLE ONLY annotations ADD CONSTRAINT annotations_pkey PRIMARY KEY (id);
CREATE INDEX idx_annotations ON annotations (key, value);
CREATE INDEX idx_annotations_did ON annotations (delta_file_id);

-- delete_policies
DROP TABLE IF EXISTS delete_policies;
CREATE TABLE delete_policies (
    id uuid NOT NULL,
    enabled boolean NOT NULL,
    delete_metadata boolean,
    max_percent integer,
    min_bytes bigint,
    policy_type character varying(31) NOT NULL,
    name text NOT NULL,
    after_complete text,
    after_create text,
    flow text
);
ALTER TABLE ONLY delete_policies ADD CONSTRAINT delete_policies_name_key UNIQUE (name);
ALTER TABLE ONLY delete_policies ADD CONSTRAINT delete_policies_pkey PRIMARY KEY (id);
INSERT INTO delete_policies (id, enabled, max_percent, policy_type, name)
    SELECT gen_random_uuid(), true, 80, 'DISK_SPACE', '80% Disk'
    WHERE NOT EXISTS (SELECT * FROM delete_policies);

-- delta_file_flows
DROP TABLE IF EXISTS delta_file_flows;
DROP TYPE IF EXISTS dff_state_enum;
DROP TYPE IF EXISTS dff_type_enum;
CREATE TYPE dff_state_enum AS ENUM ('IN_FLIGHT', 'COMPLETE', 'ERROR', 'CANCELLED', 'PENDING_ANNOTATIONS', 'FILTERED');
CREATE TYPE dff_type_enum AS ENUM ('REST_DATA_SOURCE', 'TIMED_DATA_SOURCE', 'TRANSFORM', 'DATA_SINK');
CREATE TABLE delta_file_flows (
    id uuid NOT NULL,
    delta_file_id uuid,
    join_id uuid,
    depth integer NOT NULL,
    number integer NOT NULL,
    version bigint NOT NULL,
    test_mode boolean NOT NULL,
    cold_queued boolean DEFAULT false,
    created timestamp(6) with time zone,
    modified timestamp(6) with time zone,
    error_acknowledged timestamp(6) with time zone,
    next_auto_resume timestamp(6) with time zone,
    name text,
    state dff_state_enum,
    type dff_type_enum,
    test_mode_reason text,
    error_acknowledged_reason text,
    error_or_filter_cause text,
    input jsonb,
    actions jsonb DEFAULT '[]'::jsonb,
    publish_topics text[],
    pending_annotations text[],
    pending_actions text[]
);
ALTER TABLE ONLY delta_file_flows ADD CONSTRAINT delta_file_flows_pkey PRIMARY KEY (id);
CREATE INDEX idx_delta_file_flow_cold_queued ON delta_file_flows (type, (((actions -> (jsonb_array_length(actions) - 1)) ->> 'name'::text))) WHERE ((state = 'IN_FLIGHT'::dff_state_enum) AND (cold_queued = true));
CREATE INDEX idx_delta_file_flow_error_count ON delta_file_flows (name) WHERE (state = 'ERROR'::dff_state_enum AND error_acknowledged IS NULL);
CREATE INDEX idx_delta_file_flows_delta_file_id ON delta_file_flows (delta_file_id);
CREATE INDEX idx_delta_file_flows_requeue ON delta_file_flows (modified, delta_file_id) WHERE ((state = 'IN_FLIGHT'::dff_state_enum) AND (cold_queued = false));
CREATE INDEX idx_flow ON delta_file_flows (delta_file_id, state, name, test_mode);
CREATE INDEX idx_flow_state ON delta_file_flows (state, delta_file_id);

-- delta_files
DROP TABLE IF EXISTS delta_files;
DROP TYPE IF EXISTS df_stage_enum;
CREATE TYPE df_stage_enum AS ENUM ('IN_FLIGHT', 'COMPLETE', 'ERROR', 'CANCELLED');
CREATE TABLE delta_files (
    did uuid NOT NULL,
    join_id uuid,
    replay_did uuid,
    requeue_count integer NOT NULL,
    ingress_bytes bigint NOT NULL,
    referenced_bytes bigint NOT NULL,
    total_bytes bigint NOT NULL,
    version bigint NOT NULL,
    content_deletable boolean NOT NULL,
    terminal boolean NOT NULL,
    egressed boolean,
    filtered boolean,
    created timestamp(6) with time zone,
    modified timestamp(6) with time zone,
    content_deleted timestamp(6) with time zone,
    replayed timestamp(6) with time zone,
    content_deleted_reason text,
    data_source text,
    name text,
    stage df_stage_enum NOT NULL,
    parent_dids uuid[],
    child_dids uuid[],
    content_object_ids uuid[],
    topics text[] DEFAULT ARRAY[]::text[],
    transforms text[] DEFAULT ARRAY[]::text[],
    data_sinks text[] DEFAULT ARRAY[]::text[]
);
ALTER TABLE ONLY delta_files ADD CONSTRAINT delta_files_pkey PRIMARY KEY (did);
CREATE INDEX idx_created ON delta_files (created, stage, data_source, name, egressed, filtered, terminal, ingress_bytes);
CREATE INDEX idx_delta_files_content_deletable ON delta_files (modified, data_source) WHERE (content_deletable = true);
CREATE INDEX idx_delta_files_content_deleted_created_data_source ON delta_files (created, data_source) WHERE (content_deleted IS NOT NULL);
CREATE INDEX idx_delta_files_data_sinks ON delta_files USING gin (data_sinks);
CREATE INDEX idx_delta_files_data_source_created ON delta_files (data_source, created);
CREATE INDEX idx_delta_files_stage_error ON delta_files ((stage = 'ERROR'::df_stage_enum));
CREATE INDEX idx_delta_files_stage_in_flight ON delta_files ((stage = 'IN_FLIGHT'::df_stage_enum));
CREATE INDEX idx_delta_files_topics ON delta_files USING gin (topics);
CREATE INDEX idx_delta_files_transforms ON delta_files USING gin (transforms);
CREATE INDEX idx_modified ON delta_files (modified, stage, data_source, name, egressed, filtered, terminal, ingress_bytes);

-- events
DROP TABLE IF EXISTS events;
CREATE TABLE events (
    id uuid NOT NULL,
    acknowledged boolean NOT NULL,
    notification boolean NOT NULL,
    "timestamp" timestamp(6) with time zone,
    severity text,
    source text,
    summary text,
    content text
);
ALTER TABLE ONLY events ADD CONSTRAINT events_pkey PRIMARY KEY (id);

-- flows
DROP TABLE IF EXISTS flows;
CREATE TABLE flows (
    id uuid NOT NULL,
    current_did uuid,
    max_errors integer,
    execute_immediate boolean,
    last_run timestamp(6) with time zone,
    next_run timestamp(6) with time zone,
    discriminator text NOT NULL,
    type text NOT NULL check (type in ('REST_DATA_SOURCE','TIMED_DATA_SOURCE','TRANSFORM','DATA_SINK')),
    ingress_status text check (ingress_status in ('HEALTHY','DEGRADED','UNHEALTHY')),
    cron_schedule text,
    description text,
    ingress_status_message text,
    memo text,
    name text,
    topic text,
    egress_action jsonb,
    expected_annotations jsonb,
    flow_status jsonb,
    publish jsonb,
    source_plugin jsonb,
    subscribe jsonb,
    timed_ingress_action jsonb,
    transform_actions jsonb,
    variables jsonb
);
ALTER TABLE ONLY flows ADD CONSTRAINT flows_name_type_key UNIQUE (name, type);
ALTER TABLE ONLY flows ADD CONSTRAINT flows_pkey PRIMARY KEY (id);

-- integration_tests
DROP TABLE IF EXISTS integration_tests;
CREATE TABLE integration_tests (
    name text NOT NULL,
    timeout text,
    description text,
    plugins jsonb,
    data_sources jsonb,
    transformation_flows jsonb,
    data_sinks jsonb,
    inputs jsonb,
    expected_delta_files jsonb
);
ALTER TABLE ONLY integration_tests ADD CONSTRAINT integration_tests_pkey PRIMARY KEY (name);

-- join_entries
DROP TABLE IF EXISTS join_entries;
CREATE TABLE join_entries (
    id uuid NOT NULL,
    count integer NOT NULL,
    max_flow_depth integer NOT NULL,
    max_num integer,
    min_num integer,
    locked boolean NOT NULL,
    join_date timestamp(6) with time zone,
    locked_time timestamp(6) with time zone,
    join_definition jsonb
);
ALTER TABLE ONLY join_entries ADD CONSTRAINT join_entries_pkey PRIMARY KEY (id);
ALTER TABLE ONLY join_entries ADD CONSTRAINT unique_join_definition UNIQUE (join_definition);

-- join_entry_dids
DROP TABLE IF EXISTS join_entry_dids;
CREATE TABLE join_entry_dids (
    id uuid NOT NULL,
    did uuid NOT NULL,
    join_entry_id uuid,
    orphan boolean,
    action_name text,
    error_reason text
);
ALTER TABLE ONLY join_entry_dids ADD CONSTRAINT join_entry_dids_pkey PRIMARY KEY (id);
CREATE INDEX idx_orphan ON join_entry_dids (orphan);

-- links
DROP TABLE IF EXISTS links;
CREATE TABLE links (
    id uuid NOT NULL,
    link_type text check (link_type in ('EXTERNAL','DELTAFILE_LINK')),
    name text,
    url text,
    description text
);
ALTER TABLE ONLY links ADD CONSTRAINT links_name_link_type_key UNIQUE (name, link_type);
ALTER TABLE ONLY links ADD CONSTRAINT links_pkey PRIMARY KEY (id);

-- plugin_variables
DROP TABLE IF EXISTS plugin_variables;
CREATE TABLE plugin_variables (
    id uuid NOT NULL,
    artifact_id text,
    group_id text,
    version text,
    variables jsonb
);
ALTER TABLE ONLY plugin_variables ADD CONSTRAINT plugin_variables_group_id_artifact_id_version_key UNIQUE (group_id, artifact_id, version);
ALTER TABLE ONLY plugin_variables ADD CONSTRAINT plugin_variables_pkey PRIMARY KEY (id);

-- plugins
DROP TABLE IF EXISTS plugins;
CREATE TABLE plugins (
    artifact_id text NOT NULL,
    group_id text NOT NULL,
    version text NOT NULL,
    action_kit_version text,
    display_name text,
    description text,
    image_pull_secret text,
    image_name text,
    image_tag text,
    actions jsonb,
    dependencies jsonb,
    flow_plans jsonb
);
ALTER TABLE ONLY plugins ADD CONSTRAINT plugins_pkey PRIMARY KEY (artifact_id, group_id);

-- properties
DROP TABLE IF EXISTS properties;
CREATE TABLE properties (
    refreshable boolean NOT NULL,
    key text NOT NULL,
    custom_value text,
    default_value text,
    description text
);
ALTER TABLE ONLY properties ADD CONSTRAINT properties_pkey PRIMARY KEY (key);

-- queued_annotations
DROP TABLE IF EXISTS queued_annotations;
CREATE TABLE queued_annotations (
    id uuid NOT NULL,
    did uuid NOT NULL,
    allow_overwrites boolean NOT NULL,
    "time" timestamp(6) with time zone NOT NULL,
    annotations jsonb
);
ALTER TABLE ONLY queued_annotations ADD CONSTRAINT queued_annotations_pkey PRIMARY KEY (id);

-- resume_policies
DROP TABLE IF EXISTS resume_policies;
CREATE TABLE resume_policies (
    id uuid NOT NULL,
    action_type smallint,
    max_attempts integer NOT NULL,
    priority integer,
    name text NOT NULL,
    action text,
    data_source text,
    error_substring text,
    back_off jsonb,
    CONSTRAINT resume_policies_action_type_check CHECK (((action_type >= 0) AND (action_type <= 5)))
);
ALTER TABLE ONLY resume_policies ADD CONSTRAINT resume_policies_error_substring_action_action_type_key UNIQUE (error_substring, action, action_type);
ALTER TABLE ONLY resume_policies ADD CONSTRAINT resume_policies_name_key UNIQUE (name);
ALTER TABLE ONLY resume_policies ADD CONSTRAINT resume_policies_pkey PRIMARY KEY (id);

-- system_snapshot
DROP TABLE IF EXISTS system_snapshot;
CREATE TABLE system_snapshot (
    id uuid NOT NULL,
    created timestamp(6) with time zone,
    reason text,
    delete_policies jsonb,
    delta_fi_properties jsonb,
    data_sinks jsonb,
    installed_plugins jsonb,
    links jsonb,
    plugin_variables jsonb,
    rest_data_sources jsonb,
    resume_policies jsonb,
    timed_data_sources jsonb,
    transform_flows jsonb
);
ALTER TABLE ONLY system_snapshot ADD CONSTRAINT system_snapshot_pkey PRIMARY KEY (id);

-- test_results
DROP TABLE IF EXISTS test_results;
CREATE TABLE test_results (
    id text NOT NULL,
    start timestamp(6) with time zone,
    stop timestamp(6) with time zone,
    test_name text,
    status text check (status in ('INVALID', 'STARTED', 'SUCCESSFUL', 'FAILED')),
    errors jsonb
);
ALTER TABLE ONLY test_results ADD CONSTRAINT test_results_pkey PRIMARY KEY (id);

-- ts_annotations
DROP TABLE IF EXISTS ts_annotations;
CREATE TABLE ts_annotations (
    entity_id uuid NOT NULL,
    entity_timestamp timestamp(6) with time zone NOT NULL,
    data_source text NOT NULL,
    key text NOT NULL,
    value text NOT NULL
);
ALTER TABLE ONLY ts_annotations ADD CONSTRAINT ts_annotations_pkey PRIMARY KEY (entity_timestamp, entity_id, key);
SELECT create_hypertable('ts_annotations', 'entity_timestamp', chunk_time_interval => INTERVAL '1 day');
CREATE INDEX ON ts_annotations (entity_timestamp DESC, data_source);
CREATE INDEX ON ts_annotations (entity_id, key, value);
ALTER TABLE ts_annotations SET (
    timescaledb.compress,
    timescaledb.compress_orderby = 'entity_timestamp DESC',
    timescaledb.compress_chunk_time_interval = '7 days'
    );
SELECT add_compression_policy('ts_annotations', INTERVAL '7 days');
SELECT add_retention_policy('ts_annotations', INTERVAL '30 days', schedule_interval => INTERVAL '1 hour');

-- ts_cancels
DROP TABLE IF EXISTS ts_cancels;
CREATE TABLE ts_cancels (
    id uuid NOT NULL,
    "timestamp" timestamp(6) with time zone NOT NULL,
    data_source text NOT NULL
);
ALTER TABLE ONLY ts_cancels ADD CONSTRAINT ts_cancels_pkey PRIMARY KEY ("timestamp", data_source, id);
SELECT create_hypertable('ts_cancels', 'timestamp', number_partitions => 4, chunk_time_interval => INTERVAL '1 day', if_not_exists => TRUE);
ALTER TABLE ts_cancels SET (
    timescaledb.compress,
    timescaledb.compress_orderby = 'timestamp DESC',
    timescaledb.compress_chunk_time_interval = '7 days');
SELECT add_compression_policy('ts_cancels', INTERVAL '7 days');
SELECT add_retention_policy('ts_cancels', INTERVAL '30 days', schedule_interval => INTERVAL '1 hour');
CREATE INDEX ON ts_cancels (timestamp DESC, data_source);
CREATE INDEX idx_ts_cancels_time_ds ON ts_cancels (timestamp DESC) INCLUDE (data_source);

-- ts_egresses
DROP TABLE IF EXISTS ts_egresses;
CREATE TABLE ts_egresses (
    id uuid NOT NULL,
    "timestamp" timestamp(6) with time zone NOT NULL,
    egress_bytes bigint,
    data_source text NOT NULL,
    egressor text NOT NULL
);
ALTER TABLE ONLY ts_egresses ADD CONSTRAINT ts_egresses_pkey PRIMARY KEY ("timestamp", data_source, id);
SELECT create_hypertable('ts_egresses', 'timestamp', number_partitions => 4, chunk_time_interval => INTERVAL '1 day', if_not_exists => TRUE);
ALTER TABLE ts_egresses SET (
    timescaledb.compress,
    timescaledb.compress_orderby = 'timestamp DESC',
    timescaledb.compress_chunk_time_interval = '7 days');
SELECT add_compression_policy('ts_egresses', INTERVAL '7 days');
SELECT add_retention_policy('ts_egresses', INTERVAL '30 days', schedule_interval => INTERVAL '1 hour');
CREATE INDEX ON ts_egresses (timestamp DESC, data_source);
CREATE INDEX ON ts_egresses (egressor, timestamp DESC);
CREATE INDEX idx_ts_egresses_time_ds ON ts_egresses (timestamp DESC) INCLUDE (data_source);

-- ts_errors
DROP TABLE IF EXISTS ts_errors;
CREATE TABLE ts_errors (
    id uuid NOT NULL,
    "timestamp" timestamp(6) with time zone NOT NULL,
    data_source text NOT NULL,
    cause text,
    flow text,
    action text
);
ALTER TABLE ONLY ts_errors ADD CONSTRAINT ts_errors_pkey PRIMARY KEY ("timestamp", data_source, id);
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
CREATE INDEX idx_ts_errors_time_ds ON ts_errors (timestamp DESC) INCLUDE (data_source);

-- ts_filters
DROP TABLE IF EXISTS ts_filters;
CREATE TABLE ts_filters (
    id uuid NOT NULL,
    "timestamp" timestamp(6) with time zone NOT NULL,
    data_source text NOT NULL,
    message text,
    flow text,
    action text
);
ALTER TABLE ONLY ts_filters ADD CONSTRAINT ts_filters_pkey PRIMARY KEY ("timestamp", data_source, id);
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
CREATE INDEX idx_ts_filters_time_ds ON ts_filters (timestamp DESC) INCLUDE (data_source);

-- ts_ingresses
DROP TABLE IF EXISTS ts_ingresses;
CREATE TABLE ts_ingresses (
    id uuid NOT NULL,
    "timestamp" timestamp(6) with time zone NOT NULL,
    ingress_bytes bigint,
    count integer,
    survey boolean,
    data_source text NOT NULL
);
ALTER TABLE ONLY ts_ingresses ADD CONSTRAINT ts_ingresses_pkey PRIMARY KEY ("timestamp", data_source, id);
SELECT create_hypertable('ts_ingresses', 'timestamp', number_partitions => 4, chunk_time_interval => INTERVAL '1 day', if_not_exists => TRUE);
ALTER TABLE ts_ingresses SET (
    timescaledb.compress,
    timescaledb.compress_orderby = 'timestamp DESC',
    timescaledb.compress_chunk_time_interval = '7 days');
SELECT add_compression_policy('ts_ingresses', INTERVAL '7 days');
SELECT add_retention_policy('ts_ingresses', INTERVAL '30 days', schedule_interval => INTERVAL '1 hour');
CREATE INDEX ON ts_ingresses (timestamp DESC, data_source);
CREATE INDEX idx_ts_ingresses_time_ds ON ts_ingresses (timestamp DESC) INCLUDE (data_source);

-- user_roles
DROP TABLE IF EXISTS user_roles;
CREATE TABLE user_roles (
                            user_id uuid NOT NULL,
                            role_id uuid NOT NULL
);
ALTER TABLE ONLY user_roles ADD CONSTRAINT user_roles_pkey PRIMARY KEY (user_id, role_id);

-- roles
DROP TABLE IF EXISTS roles;
CREATE TABLE roles (
                       id uuid NOT NULL,
                       created_at timestamp(6) with time zone,
                       updated_at timestamp(6) with time zone,
                       name text,
                       permissions jsonb
);
ALTER TABLE ONLY roles ADD CONSTRAINT roles_name_key UNIQUE (name);
ALTER TABLE ONLY roles ADD CONSTRAINT roles_pkey PRIMARY KEY (id);
ALTER TABLE ONLY user_roles ADD CONSTRAINT user_roles_role_id_fkey FOREIGN KEY (role_id) REFERENCES roles(id);

-- users
DROP TABLE IF EXISTS users;
CREATE TABLE users (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    name text,
    dn text,
    username text,
    password text
);
ALTER TABLE ONLY users ADD CONSTRAINT users_dn_key UNIQUE (dn);
ALTER TABLE ONLY users ADD CONSTRAINT users_name_key UNIQUE (name);
ALTER TABLE ONLY users ADD CONSTRAINT users_pkey PRIMARY KEY (id);
ALTER TABLE ONLY users ADD CONSTRAINT users_username_key UNIQUE (username);
ALTER TABLE ONLY user_roles ADD CONSTRAINT user_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id);
