-- Replace survey column with ingress type enum

CREATE TYPE analytic_ingress_type_enum AS ENUM ('DATA_SOURCE', 'CHILD', 'SURVEY');
ALTER TABLE analytics ADD COLUMN analytic_ingress_type analytic_ingress_type_enum;
UPDATE analytics
SET analytic_ingress_type = CASE WHEN survey THEN 'SURVEY'::analytic_ingress_type_enum ELSE 'DATA_SOURCE'::analytic_ingress_type_enum END;
ALTER TABLE analytics ALTER COLUMN analytic_ingress_type SET NOT NULL;
ALTER TABLE analytics DROP COLUMN survey;

-- Replace materialized views

DROP MATERIALIZED VIEW IF EXISTS analytics_5m_noanno;
DROP MATERIALIZED VIEW IF EXISTS analytics_5m_anno;

CREATE MATERIALIZED VIEW analytics_5m_noanno
            WITH (timescaledb.continuous) AS
SELECT
    time_bucket('5 minutes', event_time) AS bucket_start,
    data_source_id,
    event_group_id,
    analytic_ingress_type,
    SUM(CASE WHEN event_type = 'INGRESS' THEN bytes_count ELSE 0 END)::bigint AS ingress_bytes,
    SUM(CASE WHEN event_type = 'INGRESS' THEN file_count ELSE 0 END) AS ingress_files,
    SUM(CASE WHEN event_type = 'EGRESS' THEN bytes_count ELSE 0 END)::bigint AS egress_bytes,
    SUM(CASE WHEN event_type = 'EGRESS' THEN file_count ELSE 0 END) AS egress_files,
    SUM(CASE WHEN event_type = 'ERROR' THEN file_count ELSE 0 END) AS error_files,
    SUM(CASE WHEN event_type = 'FILTER' THEN file_count ELSE 0 END) AS filter_files,
    SUM(CASE WHEN event_type = 'CANCEL' THEN file_count ELSE 0 END) AS cancelled_files,
    MAX(updated) AS ignored
FROM analytics
GROUP BY time_bucket('5 minutes', event_time), data_source_id, event_group_id, analytic_ingress_type
WITH NO DATA;

SELECT add_continuous_aggregate_policy(
               'analytics_5m_noanno',
               start_offset => INTERVAL '3 days',
               end_offset   => INTERVAL '1 minute',
               schedule_interval => INTERVAL '1 minute'
       );

CREATE INDEX idx_noanno ON analytics_5m_noanno(bucket_start, analytic_ingress_type, data_source_id, event_group_id);
SELECT add_retention_policy('analytics_5m_noanno', INTERVAL '30 days');

CREATE MATERIALIZED VIEW analytics_5m_anno
            WITH (timescaledb.continuous) AS
SELECT
    time_bucket('5 minutes', a.event_time) AS bucket_start,
    a.data_source_id,
    a.event_group_id,
    a.analytic_ingress_type,
    ea.annotation_key_id,
    ea.annotation_value_id,
    SUM(CASE WHEN a.event_type = 'INGRESS' THEN a.bytes_count ELSE 0 END)::bigint AS ingress_bytes,
    SUM(CASE WHEN a.event_type = 'INGRESS' THEN a.file_count ELSE 0 END) AS ingress_files,
    SUM(CASE WHEN a.event_type = 'EGRESS' THEN a.bytes_count ELSE 0 END)::bigint AS egress_bytes,
    SUM(CASE WHEN a.event_type = 'EGRESS' THEN a.file_count ELSE 0 END) AS egress_files,
    SUM(CASE WHEN a.event_type = 'ERROR' THEN a.file_count ELSE 0 END) AS error_files,
    SUM(CASE WHEN a.event_type = 'FILTER' THEN a.file_count ELSE 0 END) AS filter_files,
    SUM(CASE WHEN a.event_type = 'CANCEL' THEN a.file_count ELSE 0 END) AS cancelled_files,
    MAX(a.updated) AS ignored
FROM analytics a
         JOIN event_annotations ea ON a.did = ea.did
GROUP BY time_bucket('5 minutes', a.event_time), a.data_source_id, a.event_group_id, a.analytic_ingress_type,
         ea.annotation_key_id, ea.annotation_value_id
WITH NO DATA;

SELECT add_continuous_aggregate_policy(
               'analytics_5m_anno',
               start_offset => INTERVAL '3 days',
               end_offset   => INTERVAL '1 minute',
               schedule_interval => INTERVAL '1 minute'
       );

CREATE INDEX idx_anno ON analytics_5m_anno(bucket_start, analytic_ingress_type, data_source_id, event_group_id, annotation_key_id, annotation_value_id);
CREATE INDEX idx_anno_distinct_anno_key ON analytics_5m_anno(annotation_key_id);
CREATE INDEX idx_anno_distinct_anno_val ON analytics_5m_anno(annotation_value_id);
SELECT add_retention_policy('analytics_5m_anno', INTERVAL '30 days');

-- Replace helper functions

DROP FUNCTION get_analytics_data_by_id;
CREATE FUNCTION get_analytics_data_by_id(
    p_annotation_key int,
    p_annotation_values int[],
    p_datasources int[],
    p_groups int[],
    p_analytic_ingress_types text[],
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
    v_include_all_analytic boolean;
    v_analytic_types analytic_ingress_type_enum[];
BEGIN
    -- Determine if parameters contain '-1' (which we use for "All"):
    SELECT EXISTS(SELECT 1 FROM unnest(p_annotation_values) x WHERE x = -1) INTO v_anno_all;
    SELECT EXISTS(SELECT 1 FROM unnest(p_datasources) x WHERE x = -1)        INTO v_ds_all;
    SELECT EXISTS(SELECT 1 FROM unnest(p_groups) x WHERE x = -1)            INTO v_group_all;

    -- Check if 'All' was passed for analytic_ingress_type
    v_include_all_analytic := 'All' = ANY(p_analytic_ingress_types);
    IF NOT v_include_all_analytic THEN
        v_analytic_types := ARRAY(SELECT unnest(p_analytic_ingress_types)::analytic_ingress_type_enum);
    END IF;

    RETURN QUERY
        WITH
            -- Filter arrays excluding -1
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

            /* 1) Collect “annotated” data from analytics_5m_anno */
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
                  AND (v_include_all_analytic OR a.analytic_ingress_type = ANY(v_analytic_types))
                  AND a.bucket_start BETWEEN p_start_time AND p_end_time
                GROUP BY a.bucket_start, a.data_source_id, a.event_group_id, a.annotation_value_id
            ),

            /* 2) Collect “no-annotation” totals from analytics_5m_noanno */
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
                  AND (v_include_all_analytic OR n.analytic_ingress_type = ANY(v_analytic_types))
                  AND n.bucket_start BETWEEN p_start_time AND p_end_time
                GROUP BY n.bucket_start, n.data_source_id, n.event_group_id
            ),

            /* 3) Sum annotation data by bucket (just in case there are multiple matching annotation_value_ids) */
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

            /* 4) “Not Present” data (only when -1 is in annotation_values) */
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

            /* 5) Combine annotated + “Not Present” into one table */
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

            /* 6) Aggregate everything by exact bucket_start + dimension columns (but do NOT yet gapfill) */
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

        /* 7) Final SELECT: gapfill on the reduced aggregated set */
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

DROP FUNCTION get_analytics_data;
CREATE FUNCTION get_analytics_data(
    p_annotation_key_name text,
    p_annotation_values_text text[],
    p_datasources_text text[],
    p_groups_text text[],
    p_analytic_ingress_types text[],
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
    -- Check for "All" values
    v_include_all_annotation_values := 'All' = ANY(p_annotation_values_text);
    v_include_all_datasources := 'All' = ANY(p_datasources_text);
    v_include_all_groups := 'All' = ANY(p_groups_text);

    -- Get annotation key ID from name (or -1 if "All")
    IF p_annotation_key_name = 'All' THEN
        v_annotation_key := -1;
    ELSE
        SELECT id INTO v_annotation_key FROM annotation_keys WHERE key_name = p_annotation_key_name;
        IF NOT FOUND THEN
            RETURN;  -- Return empty result if key not found
        END IF;
    END IF;

    -- Convert annotation value names to IDs
    IF v_include_all_annotation_values THEN
        v_annotation_values := ARRAY[-1];
    ELSE
        SELECT array_agg(id) INTO v_annotation_values
        FROM annotation_values
        WHERE value_text = ANY(p_annotation_values_text);
    END IF;

    -- Convert datasource names to IDs
    IF v_include_all_datasources THEN
        v_datasources := ARRAY[-1];
    ELSE
        SELECT array_agg(id) INTO v_datasources
        FROM flow_definitions
        WHERE name = ANY(p_datasources_text);
    END IF;

    -- Convert group names to IDs
    IF v_include_all_groups THEN
        v_groups := ARRAY[-1];
    ELSE
        SELECT array_agg(id) INTO v_groups
        FROM event_groups
        WHERE name = ANY(p_groups_text);
    END IF;

    -- Call the original function with the resolved IDs
    RETURN QUERY
        SELECT * FROM get_analytics_data_by_id(
                v_annotation_key,
                COALESCE(v_annotation_values, ARRAY[-1]::int[]),
                COALESCE(v_datasources, ARRAY[-1]::int[]),
                COALESCE(v_groups, ARRAY[-1]::int[]),
                p_analytic_ingress_types,
                p_start_time,
                p_end_time,
                p_interval_str
                      );
END;
$$ LANGUAGE plpgsql;