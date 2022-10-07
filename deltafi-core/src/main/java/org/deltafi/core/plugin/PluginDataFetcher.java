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
package org.deltafi.core.plugin;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import lombok.RequiredArgsConstructor;
import org.deltafi.common.types.Plugin;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.types.Result;

import java.util.Collection;

@DgsComponent
@RequiredArgsConstructor
public class PluginDataFetcher {
    private final PluginRegistryService pluginRegistryService;

    @DgsQuery
    public Collection<Plugin> plugins() {
        return pluginRegistryService.getPluginsWithVariables();
    }

    @DgsQuery
    public boolean verifyActionsAreRegistered(PluginCoordinates pluginCoordinates) {
        return pluginRegistryService.verifyActionsAreRegistered(pluginCoordinates);
    }

    @DgsMutation
    public Result uninstallPlugin(boolean dryRun, PluginCoordinates pluginCoordinatesInput) {
        return pluginRegistryService.uninstallPlugin(dryRun, pluginCoordinatesInput);
    }
}
