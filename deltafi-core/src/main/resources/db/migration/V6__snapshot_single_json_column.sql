-- add the new columns
ALTER TABLE system_snapshot
    ADD COLUMN schema_version int,
    ADD COLUMN snapshot jsonb;

-- combine the old json columns to set the snapshot column
UPDATE system_snapshot
SET schema_version = 1, snapshot = json_build_object(
        'deletePolicies', delete_policies,
        'deltaFiProperties', delta_fi_properties,
        'dataSinks', data_sinks,
        'installedPlugins', installed_plugins,
        'links', links,
        'pluginVariables', plugin_variables,
        'restDataSources', rest_data_sources,
        'resumePolicies', resume_policies,
        'timedDataSources', timed_data_sources,
        'transformFlows', transform_flows,
        'systemFlowPlans', system_flow_plans,
        'users', users,
        'roles', roles);

-- remove the old json columns
ALTER TABLE system_snapshot
    DROP COLUMN delete_policies,
    DROP COLUMN delta_fi_properties,
    DROP COLUMN data_sinks,
    DROP COLUMN installed_plugins,
    DROP COLUMN links,
    DROP COLUMN plugin_variables,
    DROP COLUMN rest_data_sources,
    DROP COLUMN resume_policies,
    DROP COLUMN timed_data_sources,
    DROP COLUMN transform_flows,
    DROP COLUMN system_flow_plans,
    DROP COLUMN users,
    DROP COLUMN roles;