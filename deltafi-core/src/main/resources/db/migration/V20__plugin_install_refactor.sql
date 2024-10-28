-- remove the plugin image repository table that is no longer used
DROP TABLE plugin_image_repository CASCADE;

-- remove the plugin image repository column from the system snapshot table
alter table system_snapshot drop column plugin_image_repositories;

-- rip out PluginImageRepo permissions from any roles that included them
WITH remove_permissions AS (
    SELECT jsonb_build_array('PluginImageRepoView', 'PluginImageRepoWrite', 'PluginImageRepoDelete')::jsonb AS remove_permissions_list
)

UPDATE roles
SET permissions = (
    SELECT jsonb_agg(elem)
    FROM jsonb_array_elements_text(permissions) AS elem
    WHERE NOT (elem IN (
        SELECT jsonb_array_elements_text(remove_permissions_list) FROM remove_permissions
    ))
)
WHERE EXISTS (
    SELECT 1
    FROM jsonb_array_elements_text(permissions) AS elem
    WHERE elem IN (
        SELECT jsonb_array_elements_text(remove_permissions_list) FROM remove_permissions
    )
);

-- add new columns to the plugin table
ALTER TABLE plugins
    ADD COLUMN image text,
    ADD COLUMN image_pull_secret text;