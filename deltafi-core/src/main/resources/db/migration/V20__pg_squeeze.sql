CREATE EXTENSION IF NOT EXISTS pg_squeeze;

INSERT INTO squeeze.tables (tabschema, tabname, schedule, free_space_extra)
VALUES
    ('public', 'delta_files', ('{0,30}', NULL, NULL, NULL, NULL), 30),
    ('public', 'delta_file_flows', ('{0,30}', NULL, NULL, NULL, NULL), 30),
    ('public', 'annotations', ('{0,30}', NULL, NULL, NULL, NULL), 30);