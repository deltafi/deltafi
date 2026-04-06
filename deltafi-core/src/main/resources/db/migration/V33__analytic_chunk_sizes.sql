DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') THEN
        RETURN;
    END IF;

    PERFORM set_chunk_time_interval('analytics_5m_noanno', INTERVAL '15 day');
    PERFORM set_chunk_time_interval('analytics_5m_anno', INTERVAL '15 day');
    PERFORM set_chunk_time_interval('errors_filters_5m_noanno', INTERVAL '15 day');
    PERFORM set_chunk_time_interval('errors_filters_5m_anno', INTERVAL '15 day');
    PERFORM set_chunk_time_interval('analytics', INTERVAL '12 hour');
    DELETE FROM analytics WHERE event_time < (NOW() - INTERVAL '7 days');
    DELETE FROM analytics WHERE event_time < (NOW() - INTERVAL '6 days');
    DELETE FROM analytics WHERE event_time < (NOW() - INTERVAL '5 days');
    DELETE FROM analytics WHERE event_time < (NOW() - INTERVAL '4 days');
    DELETE FROM analytics WHERE event_time < (NOW() - INTERVAL '3 days');
END $$;
