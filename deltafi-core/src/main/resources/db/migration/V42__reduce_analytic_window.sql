-- Skip this entire migration if TimescaleDB is not installed
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') THEN
        RETURN;
    END IF;

    PERFORM remove_continuous_aggregate_policy('analytics_5m_noanno', if_exists => TRUE);
    PERFORM add_continuous_aggregate_policy(
        'analytics_5m_noanno',
        start_offset => INTERVAL '1 day',
        end_offset   => INTERVAL '10 second',
        schedule_interval => INTERVAL '1 minute'
    );
    ALTER MATERIALIZED VIEW analytics_5m_noanno
        SET (timescaledb.materialized_only = false);

    PERFORM remove_continuous_aggregate_policy('analytics_5m_anno', if_exists => TRUE);
    PERFORM add_continuous_aggregate_policy(
        'analytics_5m_anno',
        start_offset => INTERVAL '1 day',
        end_offset   => INTERVAL '10 second',
        schedule_interval => INTERVAL '1 minute'
    );
    ALTER MATERIALIZED VIEW analytics_5m_anno
        SET (timescaledb.materialized_only = false);

    PERFORM remove_continuous_aggregate_policy('errors_filters_5m_noanno', if_exists => TRUE);
    PERFORM add_continuous_aggregate_policy(
        'errors_filters_5m_noanno',
        start_offset => INTERVAL '1 day',
        end_offset   => INTERVAL '10 second',
        schedule_interval => INTERVAL '1 minute'
    );
    ALTER MATERIALIZED VIEW errors_filters_5m_noanno
        SET (timescaledb.materialized_only = false);

    PERFORM remove_continuous_aggregate_policy('errors_filters_5m_anno', if_exists => TRUE);
    PERFORM add_continuous_aggregate_policy(
        'errors_filters_5m_anno',
        start_offset => INTERVAL '1 day',
        end_offset   => INTERVAL '10 second',
        schedule_interval => INTERVAL '1 minute'
    );
    ALTER MATERIALIZED VIEW errors_filters_5m_anno
        SET (timescaledb.materialized_only = false);

    PERFORM remove_retention_policy('analytics', if_exists => TRUE);
    PERFORM add_retention_policy('analytics', INTERVAL '1 day', if_not_exists => TRUE);
END $$;
