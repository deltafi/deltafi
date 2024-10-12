-- annotations table
ALTER TABLE annotations ALTER COLUMN key TYPE text;
ALTER TABLE annotations ALTER COLUMN value TYPE text;

-- delete_policies table
ALTER TABLE delete_policies ALTER COLUMN after_complete TYPE text;
ALTER TABLE delete_policies ALTER COLUMN after_create TYPE text;
ALTER TABLE delete_policies ALTER COLUMN flow TYPE text;
ALTER TABLE delete_policies ALTER COLUMN name TYPE text;

-- delta_file_flows table
ALTER TABLE delta_file_flows ALTER COLUMN name TYPE text;
ALTER TABLE delta_file_flows ALTER COLUMN state TYPE text;
ALTER TABLE delta_file_flows ALTER COLUMN test_mode_reason TYPE text;
ALTER TABLE delta_file_flows ALTER COLUMN type TYPE text;
ALTER TABLE delta_file_flows ALTER COLUMN error_acknowledged_reason TYPE text;
ALTER TABLE delta_file_flows ALTER COLUMN error_or_filter_cause TYPE text;

-- delta_files table
ALTER TABLE delta_files ALTER COLUMN content_deleted_reason TYPE text;
ALTER TABLE delta_files ALTER COLUMN data_source TYPE text;
ALTER TABLE delta_files ALTER COLUMN name TYPE text;
ALTER TABLE delta_files ALTER COLUMN normalized_name TYPE text;
ALTER TABLE delta_files ALTER COLUMN stage TYPE text;

-- events table
ALTER TABLE events ALTER COLUMN content TYPE text;
ALTER TABLE events ALTER COLUMN severity TYPE text;
ALTER TABLE events ALTER COLUMN source TYPE text;
ALTER TABLE events ALTER COLUMN summary TYPE text;

-- flows table
ALTER TABLE flows ALTER COLUMN discriminator TYPE text;
ALTER TABLE flows ALTER COLUMN description TYPE text;
ALTER TABLE flows ALTER COLUMN cron_schedule TYPE text;
ALTER TABLE flows ALTER COLUMN ingress_status TYPE text;
ALTER TABLE flows ALTER COLUMN ingress_status_message TYPE text;
ALTER TABLE flows ALTER COLUMN memo TYPE text;
ALTER TABLE flows ALTER COLUMN name TYPE text;
ALTER TABLE flows ALTER COLUMN topic TYPE text;
ALTER TABLE flows ALTER COLUMN type TYPE text;

-- links table
ALTER TABLE links ALTER COLUMN description TYPE text;
ALTER TABLE links ALTER COLUMN link_type TYPE text;
ALTER TABLE links ALTER COLUMN name TYPE text;
ALTER TABLE links ALTER COLUMN url TYPE text;

-- plugin_image_repository table
ALTER TABLE plugin_image_repository ALTER COLUMN image_pull_secret TYPE text;
ALTER TABLE plugin_image_repository ALTER COLUMN image_repository_base TYPE text;

-- plugin_variables table
ALTER TABLE plugin_variables ALTER COLUMN artifact_id TYPE text;
ALTER TABLE plugin_variables ALTER COLUMN group_id TYPE text;
ALTER TABLE plugin_variables ALTER COLUMN version TYPE text;

-- plugins table
ALTER TABLE plugins ALTER COLUMN action_kit_version TYPE text;
ALTER TABLE plugins ALTER COLUMN artifact_id TYPE text;
ALTER TABLE plugins ALTER COLUMN description TYPE text;
ALTER TABLE plugins ALTER COLUMN display_name TYPE text;
ALTER TABLE plugins ALTER COLUMN group_id TYPE text;
ALTER TABLE plugins ALTER COLUMN version TYPE text;

-- properties table
ALTER TABLE properties ALTER COLUMN custom_value TYPE text;
ALTER TABLE properties ALTER COLUMN default_value TYPE text;
ALTER TABLE properties ALTER COLUMN description TYPE text;
ALTER TABLE properties ALTER COLUMN key TYPE text;

-- resume_policies table
ALTER TABLE resume_policies ALTER COLUMN action TYPE text;
ALTER TABLE resume_policies ALTER COLUMN data_source TYPE text;
ALTER TABLE resume_policies ALTER COLUMN error_substring TYPE text;
ALTER TABLE resume_policies ALTER COLUMN name TYPE text;

-- system_snapshot table
ALTER TABLE system_snapshot ALTER COLUMN reason TYPE text;

-- test_results table
ALTER TABLE test_results ALTER COLUMN description TYPE text;
ALTER TABLE test_results ALTER COLUMN id TYPE text;
ALTER TABLE test_results ALTER COLUMN status TYPE text;

-- roles table
ALTER TABLE roles ALTER COLUMN name TYPE text;

-- users table
ALTER TABLE users ALTER COLUMN dn TYPE text;
ALTER TABLE users ALTER COLUMN name TYPE text;
ALTER TABLE users ALTER COLUMN password TYPE text;
ALTER TABLE users ALTER COLUMN username TYPE text;