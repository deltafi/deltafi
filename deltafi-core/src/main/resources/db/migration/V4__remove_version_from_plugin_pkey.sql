ALTER TABLE plugins
    DROP CONSTRAINT plugins_pkey,
    ADD PRIMARY KEY (artifact_id, group_id);
