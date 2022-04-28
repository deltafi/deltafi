package org.deltafi.core.domain.plugin;

import org.deltafi.core.domain.api.types.PluginCoordinates;

import java.util.List;

public interface PluginRepositoryCustom {

    /**
     * Find and return any Plugins that have the matching dependency.
     *
     * @param pluginCoordinates the plugin coordinates to match
     * @return - the List of Plugin that match
     */
    List<Plugin> findPluginsWithDependency(PluginCoordinates pluginCoordinates);
}
