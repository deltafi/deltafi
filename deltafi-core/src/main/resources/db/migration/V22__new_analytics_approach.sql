-- Skip this entire migration if TimescaleDB is not installed
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') THEN
        RAISE NOTICE 'TimescaleDB not installed, skipping analytics migration';
        RETURN;
    END IF;

    DROP TABLE IF EXISTS ts_annotations;
    DROP TABLE IF EXISTS ts_cancels;
    DROP TABLE IF EXISTS ts_egresses;
    DROP TABLE IF EXISTS ts_errors;
    DROP TABLE IF EXISTS ts_filters;
    DROP TABLE IF EXISTS ts_ingresses;
END $$;

-- Create supporting tables and types (only if TimescaleDB is installed)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') THEN
        RETURN;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'event_type_enum') THEN
        CREATE TYPE event_type_enum AS ENUM ('INGRESS','EGRESS','ERROR','FILTER','CANCEL');
    END IF;

    CREATE TABLE IF NOT EXISTS action_names (
        id   SERIAL PRIMARY KEY,
        name TEXT NOT NULL UNIQUE,
        created TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
    );
    CREATE INDEX IF NOT EXISTS idx_action_names_created ON action_names(created);
    CREATE INDEX IF NOT EXISTS idx_action_names_name ON action_names(name);

    CREATE TABLE IF NOT EXISTS annotation_keys (
        id       SERIAL PRIMARY KEY,
        key_name TEXT NOT NULL UNIQUE,
        created TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
    );
    CREATE INDEX IF NOT EXISTS idx_annotation_keys_created ON annotation_keys(created);
    CREATE INDEX IF NOT EXISTS idx_annotation_keys_key_name ON annotation_keys(key_name);

    CREATE TABLE IF NOT EXISTS annotation_values (
        id          SERIAL PRIMARY KEY,
        value_text  TEXT NOT NULL,
        created TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
    );
    CREATE INDEX IF NOT EXISTS idx_annotation_values_created ON annotation_values(created);
    CREATE INDEX IF NOT EXISTS idx_annotation_values_value_text ON annotation_values(value_text);

    CREATE TABLE IF NOT EXISTS error_causes (
        id    SERIAL PRIMARY KEY,
        cause TEXT NOT NULL UNIQUE,
        created TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
    );
    CREATE INDEX IF NOT EXISTS idx_error_causes_created ON error_causes(created);
    CREATE INDEX IF NOT EXISTS idx_error_causes_cause ON error_causes(cause);

    CREATE TABLE IF NOT EXISTS event_groups (
        id   SERIAL PRIMARY KEY,
        name TEXT NOT NULL UNIQUE,
        created TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
    );
    CREATE INDEX IF NOT EXISTS idx_event_groups_created ON event_groups(created);
    CREATE INDEX IF NOT EXISTS idx_event_groups_name ON event_groups(name);

    CREATE TABLE IF NOT EXISTS analytics (
        event_time     TIMESTAMPTZ          NOT NULL,
        did            UUID                 NOT NULL,
        flow_id        INT,
        data_source_id INT,
        event_group_id INT,
        action_id      INT,
        cause_id       INT,
        event_type     event_type_enum      NOT NULL,
        bytes_count    BIGINT               DEFAULT 0,
        file_count     INT                  DEFAULT 1,
        survey         BOOLEAN              DEFAULT false,
        updated        TIMESTAMPTZ          NOT NULL
    );

    PERFORM create_hypertable('analytics', 'event_time', if_not_exists => TRUE);

    CREATE INDEX IF NOT EXISTS idx_analytics_did_idx              ON analytics (did);
    CREATE INDEX IF NOT EXISTS idx_analytics_distinct_action      ON analytics(action_id);
    CREATE INDEX IF NOT EXISTS idx_analytics_distinct_event_group ON analytics(event_group_id);

    ALTER TABLE analytics
        SET (
            timescaledb.compress = true,
            timescaledb.compress_segmentby = 'flow_id, data_source_id, event_group_id, action_id, cause_id',
            timescaledb.compress_orderby   = 'event_time DESC'
        );

    PERFORM add_retention_policy('analytics', INTERVAL '3 days', if_not_exists => TRUE);

    CREATE TABLE IF NOT EXISTS event_annotations (
        did                 UUID NOT NULL,
        annotation_key_id   INT,
        annotation_value_id INT,
        PRIMARY KEY (did, annotation_key_id)
    );
    CREATE INDEX IF NOT EXISTS idx_ea_distinct_anno_key ON event_annotations(annotation_key_id);
    CREATE INDEX IF NOT EXISTS idx_ea_distinct_anno_val ON event_annotations(annotation_value_id);
END $$;

-- Create continuous aggregates (only if TimescaleDB is installed)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') THEN
        RETURN;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname = 'analytics_5m_noanno') THEN
        CREATE MATERIALIZED VIEW analytics_5m_noanno
            WITH (timescaledb.continuous) AS
        SELECT
            time_bucket('5 minutes', event_time) AS bucket_start,
            data_source_id,
            event_group_id,
            SUM(CASE WHEN event_type = 'INGRESS' THEN bytes_count ELSE 0 END)::bigint AS ingress_bytes,
            SUM(CASE WHEN event_type = 'INGRESS' THEN file_count ELSE 0 END) AS ingress_files,
            SUM(CASE WHEN event_type = 'EGRESS' THEN bytes_count ELSE 0 END)::bigint AS egress_bytes,
            SUM(CASE WHEN event_type = 'EGRESS' THEN file_count ELSE 0 END) AS egress_files,
            SUM(CASE WHEN event_type = 'ERROR' THEN file_count ELSE 0 END) AS error_files,
            SUM(CASE WHEN event_type = 'FILTER' THEN file_count ELSE 0 END) AS filter_files,
            SUM(CASE WHEN event_type = 'CANCEL' THEN file_count ELSE 0 END) AS cancelled_files,
            MAX(updated) AS ignored
        FROM analytics
        GROUP BY 1,2,3
        WITH NO DATA;

        PERFORM add_continuous_aggregate_policy(
            'analytics_5m_noanno',
            start_offset => INTERVAL '3 days',
            end_offset   => INTERVAL '1 minute',
            schedule_interval => INTERVAL '1 minute'
        );

        CREATE INDEX IF NOT EXISTS idx_noanno ON analytics_5m_noanno(bucket_start, data_source_id, event_group_id);
        PERFORM add_retention_policy('analytics_5m_noanno', INTERVAL '30 days', if_not_exists => TRUE);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname = 'analytics_5m_anno') THEN
        CREATE MATERIALIZED VIEW analytics_5m_anno
            WITH (timescaledb.continuous) AS
        SELECT
            time_bucket('5 minutes', a.event_time) AS bucket_start,
            a.data_source_id,
            a.event_group_id,
            ea.annotation_key_id,
            ea.annotation_value_id,
            SUM(CASE WHEN a.event_type = 'INGRESS' THEN a.bytes_count ELSE 0 END)::bigint AS ingress_bytes,
            SUM(CASE WHEN a.event_type = 'INGRESS' THEN a.file_count ELSE 0 END) AS ingress_files,
            SUM(CASE WHEN a.event_type = 'EGRESS' THEN a.bytes_count ELSE 0 END)::bigint AS egress_bytes,
            SUM(CASE WHEN a.event_type = 'EGRESS' THEN a.file_count ELSE 0 END) AS egress_files,
            SUM(CASE WHEN a.event_type = 'ERROR' THEN a.file_count ELSE 0 END) AS error_files,
            SUM(CASE WHEN a.event_type = 'FILTER' THEN a.file_count ELSE 0 END) AS filter_files,
            SUM(CASE WHEN a.event_type = 'CANCEL' THEN a.file_count ELSE 0 END) AS cancelled_files,
            MAX(updated) AS ignored
        FROM analytics a
        JOIN event_annotations ea ON a.did = ea.did
        GROUP BY 1,2,3,4,5
        WITH NO DATA;

        PERFORM add_continuous_aggregate_policy(
            'analytics_5m_anno',
            start_offset => INTERVAL '3 days',
            end_offset   => INTERVAL '1 minute',
            schedule_interval => INTERVAL '1 minute'
        );

        CREATE INDEX IF NOT EXISTS idx_anno ON analytics_5m_anno(bucket_start, data_source_id, event_group_id, annotation_key_id, annotation_value_id);
        CREATE INDEX IF NOT EXISTS idx_anno_distinct_anno_key ON analytics_5m_anno(annotation_key_id);
        CREATE INDEX IF NOT EXISTS idx_anno_distinct_anno_val ON analytics_5m_anno(annotation_value_id);
        PERFORM add_retention_policy('analytics_5m_anno', INTERVAL '30 days', if_not_exists => TRUE);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname = 'errors_filters_5m_noanno') THEN
        CREATE MATERIALIZED VIEW errors_filters_5m_noanno
            WITH (timescaledb.continuous) AS
        SELECT
            time_bucket('5 minutes', event_time) AS bucket_start,
            data_source_id,
            event_group_id,
            cause_id,
            action_id,
            flow_id,
            SUM(CASE WHEN event_type = 'ERROR' THEN file_count ELSE 0 END) AS error_files,
            SUM(CASE WHEN event_type = 'FILTER' THEN file_count ELSE 0 END) AS filter_files,
            MAX(updated) AS ignored
        FROM analytics
        WHERE event_type IN ('ERROR', 'FILTER')
        GROUP BY 1,2,3,4,5,6
        WITH NO DATA;

        PERFORM add_continuous_aggregate_policy(
            'errors_filters_5m_noanno',
            start_offset => INTERVAL '3 days',
            end_offset   => INTERVAL '1 minute',
            schedule_interval => INTERVAL '1 minute'
        );

        CREATE INDEX IF NOT EXISTS idx_errors_filters_noanno               ON errors_filters_5m_noanno(bucket_start, data_source_id, event_group_id);
        CREATE INDEX IF NOT EXISTS idx_errors_filters_distinct_action      ON errors_filters_5m_noanno(action_id);
        CREATE INDEX IF NOT EXISTS idx_errors_filters_distinct_event_group ON errors_filters_5m_noanno(event_group_id);
        CREATE INDEX IF NOT EXISTS idx_errors_filters_noanno_errors        ON errors_filters_5m_noanno(bucket_start, data_source_id, event_group_id) where error_files > 0;
        CREATE INDEX IF NOT EXISTS idx_errors_filters_noanno_filters       ON errors_filters_5m_noanno(bucket_start, data_source_id, event_group_id) where filter_files > 0;
        PERFORM add_retention_policy('errors_filters_5m_noanno', INTERVAL '30 days', if_not_exists => TRUE);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname = 'errors_filters_5m_anno') THEN
        CREATE MATERIALIZED VIEW errors_filters_5m_anno
            WITH (timescaledb.continuous) AS
        SELECT
            time_bucket('5 minutes', a.event_time) AS bucket_start,
            a.data_source_id,
            a.event_group_id,
            ea.annotation_key_id,
            ea.annotation_value_id,
            a.cause_id,
            a.action_id,
            a.flow_id,
            SUM(CASE WHEN event_type = 'ERROR' THEN file_count ELSE 0 END) AS error_files,
            SUM(CASE WHEN event_type = 'FILTER' THEN file_count ELSE 0 END) AS filter_files,
            MAX(updated) AS ignored
        FROM analytics a
        JOIN event_annotations ea ON a.did = ea.did
        WHERE a.event_type IN ('ERROR','FILTER')
        GROUP BY 1,2,3,4,5,6,7,8
        WITH NO DATA;

        PERFORM add_continuous_aggregate_policy(
            'errors_filters_5m_anno',
            start_offset => INTERVAL '3 days',
            end_offset   => INTERVAL '1 minute',
            schedule_interval => INTERVAL '1 minute'
        );

        CREATE INDEX IF NOT EXISTS idx_errors_anno ON errors_filters_5m_anno(bucket_start, data_source_id, event_group_id, annotation_key_id, annotation_value_id);
        CREATE INDEX IF NOT EXISTS idx_errors_distinct_anno_key ON errors_filters_5m_anno(annotation_key_id);
        CREATE INDEX IF NOT EXISTS idx_errors_distinct_anno_val ON errors_filters_5m_anno(annotation_value_id);
        CREATE INDEX IF NOT EXISTS idx_errors_anno_errors ON errors_filters_5m_anno(bucket_start, data_source_id, event_group_id, annotation_key_id, annotation_value_id) where error_files > 0;
        CREATE INDEX IF NOT EXISTS idx_errors_anno_filters ON errors_filters_5m_anno(bucket_start, data_source_id, event_group_id, annotation_key_id, annotation_value_id) where filter_files > 0;
        PERFORM add_retention_policy('errors_filters_5m_anno', INTERVAL '30 days', if_not_exists => TRUE);
    END IF;
END $$;

-- Create summary functions (only if TimescaleDB is installed)
DO $guard$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') THEN
        RETURN;
    END IF;
END $guard$;

CREATE OR REPLACE FUNCTION get_analytics_data_by_id(
    p_annotation_key int,
    p_annotation_values int[],
    p_datasources int[],
    p_groups int[],
    p_start_time timestamptz,
    p_end_time timestamptz,
    p_interval_str text
)
    RETURNS TABLE (
                      "time" timestamptz,
                      datasource_name text,
                      group_name text,
                      annotation_value text,
                      ingress_bytes bigint,
                      ingress_files int,
                      egress_bytes bigint,
                      egress_files int,
                      error_files int,
                      filter_files int
                  ) AS
$$
DECLARE
    v_anno_all  boolean;
    v_ds_all    boolean;
    v_group_all boolean;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') THEN
        RETURN;
    END IF;

    SELECT EXISTS(SELECT 1 FROM unnest(p_annotation_values) x WHERE x = -1) INTO v_anno_all;
    SELECT EXISTS(SELECT 1 FROM unnest(p_datasources) x WHERE x = -1)        INTO v_ds_all;
    SELECT EXISTS(SELECT 1 FROM unnest(p_groups) x WHERE x = -1)            INTO v_group_all;

    RETURN QUERY
        WITH
            anno_values AS (
                SELECT array_agg(value) AS arr
                FROM unnest(p_annotation_values) AS value
                WHERE value != -1
            ),
            ds_values AS (
                SELECT array_agg(value) AS arr
                FROM unnest(p_datasources) AS value
                WHERE value != -1
            ),
            group_values AS (
                SELECT array_agg(value) AS arr
                FROM unnest(p_groups) AS value
                WHERE value != -1
            ),
            anno AS (
                SELECT
                    time_bucket(p_interval_str::interval, a.bucket_start) AS bucket_time,
                    a.data_source_id,
                    a.event_group_id,
                    a.annotation_value_id,
                    SUM(a.ingress_bytes)::bigint AS anno_ingress_bytes,
                    SUM(a.ingress_files)::int AS anno_ingress_files,
                    SUM(a.egress_bytes)::bigint  AS anno_egress_bytes,
                    SUM(a.egress_files)::int  AS anno_egress_files,
                    SUM(a.error_files)::int   AS anno_error_files,
                    SUM(a.filter_files)::int  AS anno_filter_files
                FROM analytics_5m_anno a
                         LEFT JOIN anno_values av ON true
                         LEFT JOIN ds_values   ds ON true
                         LEFT JOIN group_values g ON true
                WHERE a.annotation_key_id = p_annotation_key
                  AND (v_anno_all
                    OR (av.arr IS NOT NULL AND a.annotation_value_id = ANY(av.arr)))
                  AND (v_group_all
                    OR (g.arr IS NOT NULL AND a.event_group_id = ANY(g.arr)))
                  AND (v_ds_all
                    OR (ds.arr IS NOT NULL AND a.data_source_id = ANY(ds.arr)))
                  AND a.bucket_start BETWEEN p_start_time AND p_end_time
                GROUP BY a.bucket_start, a.data_source_id, a.event_group_id, a.annotation_value_id
            ),
            total AS (
                SELECT
                    time_bucket(p_interval_str::interval, n.bucket_start) AS bucket_time,
                    n.data_source_id,
                    n.event_group_id,
                    SUM(n.ingress_bytes)::bigint AS total_ingress_bytes,
                    SUM(n.ingress_files)::int AS total_ingress_files,
                    SUM(n.egress_bytes)::bigint  AS total_egress_bytes,
                    SUM(n.egress_files)::int  AS total_egress_files,
                    SUM(n.error_files)::int   AS total_error_files,
                    SUM(n.filter_files)::int  AS total_filter_files
                FROM analytics_5m_noanno n
                         LEFT JOIN ds_values ds   ON true
                         LEFT JOIN group_values g ON true
                WHERE (v_group_all
                    OR (g.arr IS NOT NULL AND n.event_group_id = ANY(g.arr)))
                  AND (v_ds_all
                    OR (ds.arr IS NOT NULL AND n.data_source_id = ANY(ds.arr)))
                  AND n.bucket_start BETWEEN p_start_time AND p_end_time
                GROUP BY n.bucket_start, n.data_source_id, n.event_group_id
            ),
            anno_sum AS (
                SELECT
                    a.bucket_time,
                    a.data_source_id,
                    a.event_group_id,
                    SUM(a.anno_ingress_bytes)::bigint AS sum_ingress_bytes,
                    SUM(a.anno_ingress_files)::int AS sum_ingress_files,
                    SUM(a.anno_egress_bytes)::bigint  AS sum_egress_bytes,
                    SUM(a.anno_egress_files)::int  AS sum_egress_files,
                    SUM(a.anno_error_files)::int   AS sum_error_files,
                    SUM(a.anno_filter_files)::int  AS sum_filter_files
                FROM anno a
                GROUP BY a.bucket_time, a.data_source_id, a.event_group_id
            ),
            not_present AS (
                SELECT
                    t.bucket_time,
                    t.data_source_id,
                    t.event_group_id,
                    NULL::integer                 AS annotation_value_id,
                    GREATEST(0, t.total_ingress_bytes - COALESCE(a.sum_ingress_bytes, 0))::bigint AS np_ingress_bytes,
                    GREATEST(0, t.total_ingress_files - COALESCE(a.sum_ingress_files, 0))::int AS np_ingress_files,
                    GREATEST(0, t.total_egress_bytes - COALESCE(a.sum_egress_bytes, 0))::bigint  AS np_egress_bytes,
                    GREATEST(0, t.total_egress_files - COALESCE(a.sum_egress_files, 0))::int   AS np_egress_files,
                    GREATEST(0, t.total_error_files - COALESCE(a.sum_error_files, 0))::int     AS np_error_files,
                    GREATEST(0, t.total_filter_files - COALESCE(a.sum_filter_files, 0))::int   AS np_filter_files
                FROM total t
                         LEFT JOIN anno_sum a
                                   ON  t.bucket_time   = a.bucket_time
                                       AND t.data_source_id = a.data_source_id
                                       AND t.event_group_id = a.event_group_id
                WHERE v_anno_all
            ),
            combined_data AS (
                SELECT
                    a.bucket_time,
                    a.data_source_id,
                    a.event_group_id,
                    a.annotation_value_id,
                    a.anno_ingress_bytes::bigint AS bytes_in,
                    a.anno_ingress_files::int AS files_in,
                    a.anno_egress_bytes::bigint  AS bytes_out,
                    a.anno_egress_files::int  AS files_out,
                    a.anno_error_files::int   AS files_err,
                    a.anno_filter_files::int  AS files_filter
                FROM anno a
                UNION ALL
                SELECT
                    np.bucket_time,
                    np.data_source_id,
                    np.event_group_id,
                    np.annotation_value_id,
                    np.np_ingress_bytes::bigint  AS bytes_in,
                    np.np_ingress_files::int  AS files_in,
                    np.np_egress_bytes::bigint   AS bytes_out,
                    np.np_egress_files::int   AS files_out,
                    np.np_error_files::int    AS files_err,
                    np.np_filter_files::int   AS files_filter
                FROM not_present np
            ),
            aggregated AS (
                SELECT
                    cd.bucket_time,
                    cd.data_source_id,
                    cd.event_group_id,
                    cd.annotation_value_id,
                    SUM(cd.bytes_in)::bigint     AS sum_ingress_bytes,
                    SUM(cd.files_in)::int     AS sum_ingress_files,
                    SUM(cd.bytes_out)::bigint    AS sum_egress_bytes,
                    SUM(cd.files_out)::int    AS sum_egress_files,
                    SUM(cd.files_err)::int    AS sum_error_files,
                    SUM(cd.files_filter)::int AS sum_filter_files
                FROM combined_data cd
                GROUP BY cd.bucket_time, cd.data_source_id, cd.event_group_id, cd.annotation_value_id
            )
        SELECT
            aggregated.bucket_time AS "time",
            fd.name AS datasource_name,
            COALESCE(eg.name, 'No Group') AS group_name,
            CASE
                WHEN aggregated.annotation_value_id IS NULL THEN 'No Annotation'
                ELSE COALESCE(av.value_text, 'Unknown')
                END AS annotation_value,
            COALESCE(SUM(aggregated.sum_ingress_bytes), 0)::bigint AS ingress_bytes,
            COALESCE(SUM(aggregated.sum_ingress_files), 0)::int AS ingress_files,
            COALESCE(SUM(aggregated.sum_egress_bytes),  0)::bigint AS egress_bytes,
            COALESCE(SUM(aggregated.sum_egress_files),  0)::int AS egress_files,
            COALESCE(SUM(aggregated.sum_error_files),   0)::int AS error_files,
            COALESCE(SUM(aggregated.sum_filter_files),  0)::int AS filter_files
        FROM aggregated
                 JOIN flow_definitions fd ON aggregated.data_source_id = fd.id
                 LEFT JOIN event_groups eg ON aggregated.event_group_id = eg.id
                 LEFT JOIN annotation_values av ON aggregated.annotation_value_id = av.id
        GROUP BY
            aggregated.bucket_time,
            fd.name,
            eg.name,
            aggregated.annotation_value_id,
            av.value_text
        ORDER BY "time", datasource_name, group_name, annotation_value;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION get_analytics_data(
    p_annotation_key_name text,
    p_annotation_values_text text[],
    p_datasources_text text[],
    p_groups_text text[],
    p_start_time timestamptz,
    p_end_time timestamptz,
    p_interval_str text
)
    RETURNS TABLE (
                      "time" timestamptz,
                      datasource_name text,
                      group_name text,
                      annotation_value text,
                      ingress_bytes bigint,
                      ingress_files int,
                      egress_bytes bigint,
                      egress_files int,
                      error_files int,
                      filter_files int
                  ) AS $$
DECLARE
    v_annotation_key int;
    v_annotation_values int[];
    v_datasources int[];
    v_groups int[];
    v_include_all_annotation_values boolean := false;
    v_include_all_datasources boolean := false;
    v_include_all_groups boolean := false;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') THEN
        RETURN;
    END IF;

    v_include_all_annotation_values := 'All' = ANY(p_annotation_values_text);
    v_include_all_datasources := 'All' = ANY(p_datasources_text);
    v_include_all_groups := 'All' = ANY(p_groups_text);

    IF p_annotation_key_name = 'All' THEN
        v_annotation_key := -1;
    ELSE
        SELECT id INTO v_annotation_key FROM annotation_keys WHERE key_name = p_annotation_key_name;
        IF NOT FOUND THEN
            RETURN;
        END IF;
    END IF;

    IF v_include_all_annotation_values THEN
        v_annotation_values := ARRAY[-1];
    ELSE
        SELECT array_agg(id) INTO v_annotation_values
        FROM annotation_values
        WHERE value_text = ANY(p_annotation_values_text);
    END IF;

    IF v_include_all_datasources THEN
        v_datasources := ARRAY[-1];
    ELSE
        SELECT array_agg(id) INTO v_datasources
        FROM flow_definitions
        WHERE name = ANY(p_datasources_text);
    END IF;

    IF v_include_all_groups THEN
        v_groups := ARRAY[-1];
    ELSE
        SELECT array_agg(id) INTO v_groups
        FROM event_groups
        WHERE name = ANY(p_groups_text);
    END IF;

    RETURN QUERY
        SELECT * FROM get_analytics_data_by_id(
                v_annotation_key,
                COALESCE(v_annotation_values, ARRAY[-1]::int[]),
                COALESCE(v_datasources, ARRAY[-1]::int[]),
                COALESCE(v_groups, ARRAY[-1]::int[]),
                p_start_time,
                p_end_time,
                p_interval_str
                      );
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION get_errors_filters_data_by_id(
    p_annotation_key int,
    p_annotation_values int[],
    p_datasources int[],
    p_groups int[],
    p_start_time timestamptz,
    p_end_time timestamptz,
    p_interval_str text,
    p_event_type text DEFAULT 'BOTH'
)
    RETURNS TABLE (
                      "time" timestamptz,
                      datasource_name text,
                      group_name text,
                      annotation_value text,
                      flow_name text,
                      action_name text,
                      cause text,
                      error_files int,
                      filter_files int
                  ) AS $$
DECLARE
    v_anno_all  boolean;
    v_ds_all    boolean;
    v_group_all boolean;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') THEN
        RETURN;
    END IF;

    SELECT EXISTS(SELECT 1 FROM unnest(p_annotation_values) x WHERE x = -1) INTO v_anno_all;
    SELECT EXISTS(SELECT 1 FROM unnest(p_datasources) x WHERE x = -1)        INTO v_ds_all;
    SELECT EXISTS(SELECT 1 FROM unnest(p_groups) x WHERE x = -1)            INTO v_group_all;

    RETURN QUERY
        WITH
            anno_values AS (
                SELECT array_agg(value) AS arr
                FROM unnest(p_annotation_values) AS value
                WHERE value != -1
            ),
            ds_values AS (
                SELECT array_agg(value) AS arr
                FROM unnest(p_datasources) AS value
                WHERE value != -1
            ),
            group_values AS (
                SELECT array_agg(value) AS arr
                FROM unnest(p_groups) AS value
                WHERE value != -1
            ),
            anno AS (
                SELECT
                    time_bucket(p_interval_str::interval, a.bucket_start) AS bucket_time,
                    a.data_source_id,
                    a.event_group_id,
                    a.annotation_value_id,
                    a.flow_id,
                    a.action_id,
                    a.cause_id,
                    SUM(a.error_files)::int  AS anno_error_files,
                    SUM(a.filter_files)::int AS anno_filter_files
                FROM errors_filters_5m_anno a
                         LEFT JOIN anno_values av ON true
                         LEFT JOIN ds_values ds   ON true
                         LEFT JOIN group_values g ON true
                WHERE a.annotation_key_id = p_annotation_key
                  AND (v_anno_all
                    OR (av.arr IS NOT NULL AND a.annotation_value_id = ANY(av.arr)))
                  AND (v_group_all
                    OR (g.arr IS NOT NULL AND a.event_group_id = ANY(g.arr)))
                  AND (v_ds_all
                    OR (ds.arr IS NOT NULL AND a.data_source_id = ANY(ds.arr)))
                  AND a.bucket_start BETWEEN p_start_time AND p_end_time
                  AND ((p_event_type = 'BOTH') OR
                       (p_event_type = 'ERRORS' AND a.error_files > 0) OR
                       (p_event_type = 'FILTERS' AND a.filter_files > 0))
                GROUP BY time_bucket(p_interval_str::interval, a.bucket_start),
                         a.data_source_id, a.event_group_id, a.annotation_value_id,
                         a.flow_id, a.action_id, a.cause_id
            ),
            total AS (
                SELECT
                    time_bucket(p_interval_str::interval, n.bucket_start) AS bucket_time,
                    n.data_source_id,
                    n.event_group_id,
                    n.flow_id,
                    n.action_id,
                    n.cause_id,
                    SUM(n.error_files)::int  AS total_error_files,
                    SUM(n.filter_files)::int AS total_filter_files
                FROM errors_filters_5m_noanno n
                         LEFT JOIN ds_values ds   ON true
                         LEFT JOIN group_values g ON true
                WHERE (v_group_all
                    OR (g.arr IS NOT NULL AND n.event_group_id = ANY(g.arr)))
                  AND (v_ds_all
                    OR (ds.arr IS NOT NULL AND n.data_source_id = ANY(ds.arr)))
                  AND n.bucket_start BETWEEN p_start_time AND p_end_time
                  AND ((p_event_type = 'BOTH') OR
                       (p_event_type = 'ERRORS' AND n.error_files > 0) OR
                       (p_event_type = 'FILTERS' AND n.filter_files > 0))
                GROUP BY time_bucket(p_interval_str::interval, n.bucket_start),
                         n.data_source_id, n.event_group_id, n.flow_id, n.action_id, n.cause_id
            ),
            anno_sum AS (
                SELECT
                    a.bucket_time,
                    a.data_source_id,
                    a.event_group_id,
                    a.flow_id,
                    a.action_id,
                    a.cause_id,
                    SUM(a.anno_error_files)::int  AS sum_error_files,
                    SUM(a.anno_filter_files)::int AS sum_filter_files
                FROM anno a
                GROUP BY a.bucket_time, a.data_source_id, a.event_group_id,
                         a.flow_id, a.action_id, a.cause_id
            ),
            not_present AS (
                SELECT
                    t.bucket_time,
                    t.data_source_id,
                    t.event_group_id,
                    NULL::integer         AS annotation_value_id,
                    t.flow_id,
                    t.action_id,
                    t.cause_id,
                    GREATEST(0, t.total_error_files
                        - COALESCE(a.sum_error_files,  0))::int AS np_error_files,
                    GREATEST(0, t.total_filter_files
                        - COALESCE(a.sum_filter_files, 0))::int AS np_filter_files
                FROM total t
                         LEFT JOIN anno_sum a
                                   ON  t.bucket_time = a.bucket_time
                                       AND t.data_source_id = a.data_source_id
                                       AND t.event_group_id = a.event_group_id
                                       AND t.flow_id = a.flow_id
                                       AND t.action_id = a.action_id
                                       AND t.cause_id = a.cause_id
                WHERE v_anno_all
            ),
            combined_data AS (
                SELECT
                    a.bucket_time,
                    a.data_source_id,
                    a.event_group_id,
                    a.annotation_value_id,
                    a.flow_id,
                    a.action_id,
                    a.cause_id,
                    a.anno_error_files  AS files_err,
                    a.anno_filter_files AS files_filter
                FROM anno a
                UNION ALL
                SELECT
                    np.bucket_time,
                    np.data_source_id,
                    np.event_group_id,
                    np.annotation_value_id,
                    np.flow_id,
                    np.action_id,
                    np.cause_id,
                    np.np_error_files   AS files_err,
                    np.np_filter_files  AS files_filter
                FROM not_present np
            ),
            aggregated AS (
                SELECT
                    cd.bucket_time,
                    cd.data_source_id,
                    cd.event_group_id,
                    cd.annotation_value_id,
                    cd.flow_id,
                    cd.action_id,
                    cd.cause_id,
                    SUM(cd.files_err)::int    AS sum_error_files,
                    SUM(cd.files_filter)::int AS sum_filter_files
                FROM combined_data cd
                GROUP BY cd.bucket_time,
                         cd.data_source_id,
                         cd.event_group_id,
                         cd.annotation_value_id,
                         cd.flow_id,
                         cd.action_id,
                         cd.cause_id
            )
        SELECT
            aggregated.bucket_time AS "time",
            fd.name AS datasource_name,
            eg.name AS group_name,
            CASE
                WHEN aggregated.annotation_value_id IS NULL THEN 'No Annotation'
                ELSE COALESCE(av.value_text, 'Unknown')
                END AS annotation_value,
            fd2.name AS flow_name,
            an.name  AS action_name,
            ec.cause,
            COALESCE(SUM(aggregated.sum_error_files),  0)::int AS error_files,
            COALESCE(SUM(aggregated.sum_filter_files), 0)::int AS filter_files
        FROM aggregated
                 JOIN flow_definitions fd ON aggregated.data_source_id = fd.id
                 JOIN event_groups     eg ON aggregated.event_group_id = eg.id
                 LEFT JOIN annotation_values av ON aggregated.annotation_value_id = av.id
                 JOIN flow_definitions fd2 ON aggregated.flow_id = fd2.id
                 JOIN action_names an      ON aggregated.action_id = an.id
                 JOIN error_causes ec      ON aggregated.cause_id = ec.id
        GROUP BY
            aggregated.bucket_time,
            fd.name, eg.name,
            aggregated.annotation_value_id, av.value_text,
            fd2.name, an.name, ec.cause
        ORDER BY "time", datasource_name, group_name, annotation_value, flow_name, action_name, ec.cause;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION get_errors_filters_data(
    p_annotation_key_name text,
    p_annotation_values_text text[],
    p_datasources_text text[],
    p_groups_text text[],
    p_start_time timestamptz,
    p_end_time timestamptz,
    p_interval_str text,
    p_event_type text DEFAULT 'BOTH'
)
    RETURNS TABLE (
                      bucket_time timestamptz,
                      datasource_name text,
                      group_name text,
                      annotation_value text,
                      flow_name text,
                      action_name text,
                      cause text,
                      error_files int,
                      filter_files int
                  ) AS $$
DECLARE
    v_annotation_key int;
    v_annotation_values int[];
    v_datasources int[];
    v_groups int[];
    v_include_all_annotation_values boolean := false;
    v_include_all_datasources boolean := false;
    v_include_all_groups boolean := false;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') THEN
        RETURN;
    END IF;

    v_include_all_annotation_values := 'All' = ANY(p_annotation_values_text);
    v_include_all_datasources := 'All' = ANY(p_datasources_text);
    v_include_all_groups := 'All' = ANY(p_groups_text);

    IF p_annotation_key_name = 'All' THEN
        v_annotation_key := -1;
    ELSE
        SELECT id INTO v_annotation_key FROM annotation_keys WHERE key_name = p_annotation_key_name;
        IF NOT FOUND THEN
            RETURN;
        END IF;
    END IF;

    IF v_include_all_annotation_values THEN
        v_annotation_values := ARRAY[-1];
    ELSE
        SELECT array_agg(id) INTO v_annotation_values
        FROM annotation_values
        WHERE value_text = ANY(p_annotation_values_text);
    END IF;

    IF v_include_all_datasources THEN
        v_datasources := ARRAY[-1];
    ELSE
        SELECT array_agg(id) INTO v_datasources
        FROM flow_definitions
        WHERE name = ANY(p_datasources_text);
    END IF;

    IF v_include_all_groups THEN
        v_groups := ARRAY[-1];
    ELSE
        SELECT array_agg(id) INTO v_groups
        FROM event_groups
        WHERE name = ANY(p_groups_text);
    END IF;

    RETURN QUERY
        SELECT * FROM get_errors_filters_data_by_id(
                v_annotation_key,
                COALESCE(v_annotation_values, ARRAY[-1]::int[]),
                COALESCE(v_datasources, ARRAY[-1]::int[]),
                COALESCE(v_groups, ARRAY[-1]::int[]),
                p_start_time,
                p_end_time,
                p_interval_str,
                p_event_type
                      );
END;
$$ LANGUAGE plpgsql;
