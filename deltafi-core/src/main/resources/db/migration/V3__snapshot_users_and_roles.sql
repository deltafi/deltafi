ALTER TABLE system_snapshot
    ADD COLUMN users jsonb,
    ADD COLUMN roles jsonb;