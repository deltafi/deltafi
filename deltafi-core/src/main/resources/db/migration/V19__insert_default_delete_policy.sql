INSERT INTO delete_policies (id, enabled, max_percent, policy_type, name)
SELECT gen_random_uuid(), true, 80, 'DISK_SPACE', '80% Disk'
WHERE NOT EXISTS (SELECT * FROM delete_policies);