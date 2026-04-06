DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') THEN
        RETURN;
    END IF;

    PERFORM remove_continuous_aggregate_policy('analytics_5m_noanno');
    PERFORM add_continuous_aggregate_policy(
                   'analytics_5m_noanno',
                   start_offset => INTERVAL '3 day',
                   end_offset   => INTERVAL '10 second',
                   schedule_interval => INTERVAL '1 minute'
           );

    PERFORM remove_continuous_aggregate_policy('analytics_5m_anno');
    PERFORM add_continuous_aggregate_policy(
                   'analytics_5m_anno',
                   start_offset => INTERVAL '3 day',
                   end_offset   => INTERVAL '10 second',
                   schedule_interval => INTERVAL '1 minute'
           );

    PERFORM remove_continuous_aggregate_policy('errors_filters_5m_noanno');
    PERFORM add_continuous_aggregate_policy(
                   'errors_filters_5m_noanno',
                   start_offset => INTERVAL '3 day',
                   end_offset   => INTERVAL '10 second',
                   schedule_interval => INTERVAL '1 minute'
           );

    PERFORM remove_continuous_aggregate_policy('errors_filters_5m_anno');
    PERFORM add_continuous_aggregate_policy(
                   'errors_filters_5m_anno',
                   start_offset => INTERVAL '3 day',
                   end_offset   => INTERVAL '10 second',
                   schedule_interval => INTERVAL '1 minute'
           );

    PERFORM remove_retention_policy('analytics');
    PERFORM add_retention_policy('analytics', INTERVAL '3 day');
END $$;
