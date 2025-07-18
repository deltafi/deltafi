SELECT remove_continuous_aggregate_policy('analytics_5m_noanno');
SELECT add_continuous_aggregate_policy(
               'analytics_5m_noanno',
               start_offset => INTERVAL '3 day',
               end_offset   => INTERVAL '10 second',
               schedule_interval => INTERVAL '1 minute'
       );

SELECT remove_continuous_aggregate_policy('analytics_5m_anno');
SELECT add_continuous_aggregate_policy(
               'analytics_5m_anno',
               start_offset => INTERVAL '3 day',
               end_offset   => INTERVAL '10 second',
               schedule_interval => INTERVAL '1 minute'
       );

SELECT remove_continuous_aggregate_policy('errors_filters_5m_noanno');
SELECT add_continuous_aggregate_policy(
               'errors_filters_5m_noanno',
               start_offset => INTERVAL '3 day',
               end_offset   => INTERVAL '10 second',
               schedule_interval => INTERVAL '1 minute'
       );

SELECT remove_continuous_aggregate_policy('errors_filters_5m_anno');
SELECT add_continuous_aggregate_policy(
               'errors_filters_5m_anno',
               start_offset => INTERVAL '3 day',
               end_offset   => INTERVAL '10 second',
               schedule_interval => INTERVAL '1 minute'
       );

SELECT remove_retention_policy('analytics');
SELECT add_retention_policy('analytics', INTERVAL '3 day');
