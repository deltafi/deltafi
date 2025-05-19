-- insert the new diskSpaceDeleteThreshold using the most restrictive disk space policy without a flow specified
INSERT INTO properties (refreshable, key, custom_value, default_value, description)
VALUES (true,
        'diskSpacePercentThreshold',
        (SELECT COALESCE(MIN(max_percent)::text, '80')
         FROM delete_policies
         where policy_type = 'DISK_SPACE' and flow is null and enabled = true),
        '80',
        'The max percentage of disk space to use. When the system exceeds this percentage, content will be removed to lower the disk space usage.');

DELETE FROM delete_policies
WHERE policy_type = 'DISK_SPACE';