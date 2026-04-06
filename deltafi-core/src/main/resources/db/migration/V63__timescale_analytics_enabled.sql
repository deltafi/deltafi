-- Add timescaleAnalyticsEnabled property, defaulting to false when TimescaleDB is not installed
DO $$
DECLARE
    ts_installed BOOLEAN;
    initial_value TEXT;
BEGIN
    IF EXISTS (SELECT 1 FROM properties WHERE key = 'timescaleAnalyticsEnabled') THEN
        RETURN;
    END IF;

    ts_installed := EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb');

    IF ts_installed THEN
        initial_value := (SELECT custom_value FROM properties WHERE key = 'metricsEnabled');
    ELSE
        initial_value := 'false';
    END IF;

    INSERT INTO properties (refreshable, key, custom_value, default_value, description)
    VALUES (true, 'timescaleAnalyticsEnabled', initial_value, 'true',
            'Enable analytics storage in TimescaleDB');
END $$;
