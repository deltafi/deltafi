DELETE FROM resume_policies
WHERE name NOT IN (
	SELECT MAX(name)
	FROM resume_policies
	GROUP BY data_source, error_substring, action);

ALTER TABLE resume_policies
    DROP CONSTRAINT IF EXISTS resume_policies_action_type_check,
    DROP CONSTRAINT IF EXISTS resume_policies_error_substring_action_action_type_key,
    DROP COLUMN action_type;
ALTER TABLE ONLY resume_policies ADD CONSTRAINT resume_policies_data_source_error_substring_action_key UNIQUE NULLS NOT DISTINCT(data_source, error_substring, action);

UPDATE system_snapshot SET resume_policies = json_build_array();
