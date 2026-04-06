-- Originally: VACUUM FULL event_annotations
-- Skipped on non-TimescaleDB installs (table does not exist)
-- On TimescaleDB installs upgrading, this migration was already applied
DO $$ BEGIN PERFORM 1; END $$;
