/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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

import lombok.AllArgsConstructor;
import org.deltafi.common.types.Plugin;
import org.deltafi.common.types.PluginCoordinates;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class SystemPluginService {

    public static final String SYSTEM_PLUGIN_GROUP_ID = "org.deltafi";
    public static final String SYSTEM_PLUGIN_ARTIFACT_ID = "system-plugin";
    private static final String DISPLAY_NAME = "System Plugin";
    private static final String DESCRIPTION = "System Plugin that holds flows created within the system";

    private final BuildProperties buildProperties;

    public PluginEntity getSystemPlugin() {
        PluginEntity plugin = new PluginEntity();
        plugin.setPluginCoordinates(getSystemPluginCoordinates());
        plugin.setDisplayName(DISPLAY_NAME);
        plugin.setDescription(DESCRIPTION);
        plugin.setActionKitVersion(plugin.getPluginCoordinates().getVersion());
        plugin.setActions(List.of());
        return plugin;
    }

    public PluginCoordinates getSystemPluginCoordinates() {
        return new PluginCoordinates(SYSTEM_PLUGIN_GROUP_ID, SYSTEM_PLUGIN_ARTIFACT_ID, buildProperties.getVersion());
    }
}