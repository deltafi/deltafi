/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
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
