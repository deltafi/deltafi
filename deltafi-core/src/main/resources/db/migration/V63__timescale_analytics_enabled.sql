-- Add timescaleAnalyticsEnabled property, defaulting to current metricsEnabled value
INSERT INTO properties (refreshable, key, custom_value, default_value, description)
SELECT true, 'timescaleAnalyticsEnabled',
       (SELECT custom_value FROM properties WHERE key = 'metricsEnabled'),
       'true',
       'Enable analytics storage in TimescaleDB'
WHERE NOT EXISTS (SELECT 1 FROM properties WHERE key = 'timescaleAnalyticsEnabled');
