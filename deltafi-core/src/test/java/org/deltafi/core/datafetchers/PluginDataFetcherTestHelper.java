/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.datafetchers;

import org.deltafi.common.types.Plugin;
import org.deltafi.core.generated.client.ForcePluginUninstallProjectionRoot;
import org.deltafi.core.generated.client.PluginsProjectionRoot;
import org.deltafi.core.generated.client.UninstallPluginProjectionRoot;

import static org.junit.jupiter.api.Assertions.*;

public class PluginDataFetcherTestHelper {

    public static final PluginsProjectionRoot PLUGINS_PROJECTION_ROOT = new PluginsProjectionRoot<>()
            .pluginCoordinates()
            .groupId()
            .artifactId()
            .version().parent()
            .displayName()
            .description()
            .actionKitVersion()
            .actions()
            .name()
            .actionOptions()
            .description()
            .parent()
            .parent()
            .dependencies()
            .groupId()
            .artifactId()
            .version().parent();

    public static final ForcePluginUninstallProjectionRoot FORCED_UNINSTALL_PLUGIN_PROJECTION_ROOT = new ForcePluginUninstallProjectionRoot<>()
            .success()
            .errors();

    public static final UninstallPluginProjectionRoot UNINSTALL_PLUGIN_PROJECTION_ROOT = new UninstallPluginProjectionRoot<>()
            .success()
            .errors();

    public static void validatePlugin1(Plugin plugin1) {
        assertEquals("org.deltafi", plugin1.getPluginCoordinates().getGroupId());
        assertEquals("plugin-1", plugin1.getPluginCoordinates().getArtifactId());
        assertEquals("1.0.0", plugin1.getPluginCoordinates().getVersion());

        assertEquals("Test Plugin 1", plugin1.getDisplayName());
        assertEquals("This is a test plugin", plugin1.getDescription());
        assertEquals("1.1.0", plugin1.getActionKitVersion());

        assertEquals(2, plugin1.getActions().size());
        assertEquals("org.deltafi.test.actions1.TestAction1", plugin1.getActions().getFirst().getName());
        assertEquals("org.deltafi.test.actions1.TestAction2", plugin1.getActions().get(1).getName());
        assertEquals("TestAction2", plugin1.getActions().get(1).getActionOptions().getDescription());

        assertEquals(2, plugin1.getDependencies().size());
        assertEquals("org.deltafi", plugin1.getDependencies().getFirst().getGroupId());
        assertEquals("plugin-2", plugin1.getDependencies().getFirst().getArtifactId());
        assertEquals("1.0.0", plugin1.getDependencies().getFirst().getVersion());
        assertEquals("plugin-3", plugin1.getDependencies().get(1).getArtifactId());
    }
}
