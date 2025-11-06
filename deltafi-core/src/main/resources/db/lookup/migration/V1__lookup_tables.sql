CREATE TABLE lookup_tables (
    name text PRIMARY KEY,
    source_plugin jsonb,
    variables jsonb,
    columns text[] NOT NULL,
    key_columns text[] NOT NULL,
    service_backed boolean DEFAULT false,
    backing_service_active boolean DEFAULT false,
    pull_through boolean DEFAULT false,
    refresh_duration text,
    last_refresh timestamp(6) with time zone
);
