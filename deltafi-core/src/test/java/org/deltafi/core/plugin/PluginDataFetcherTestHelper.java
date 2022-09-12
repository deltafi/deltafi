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

import org.deltafi.core.generated.client.PluginsProjectionRoot;
import org.deltafi.core.generated.client.RegisterPluginProjectionRoot;
import org.deltafi.core.generated.client.UninstallPluginProjectionRoot;

import static org.junit.jupiter.api.Assertions.*;

public class PluginDataFetcherTestHelper {

    public static final PluginsProjectionRoot PLUGINS_PROJECTION_ROOT = new PluginsProjectionRoot()
            .pluginCoordinates()
            .groupId()
            .artifactId()
            .version().parent()
            .displayName()
            .description()
            .actionKitVersion()
            .actions()
            .name()
            .description()
            .requiresDomains().parent()
            .dependencies()
            .groupId()
            .artifactId()
            .version().parent()
            .propertySets()
            .id()
            .displayName()
            .description()
            .properties()
            .key()
            .description()
            .defaultValue()
            .refreshable()
            .editable()
            .hidden()
            .value().parent().parent();

    public static final RegisterPluginProjectionRoot REGISTER_PLUGIN_PROJECTION_ROOT = new RegisterPluginProjectionRoot()
            .success()
            .errors();

    public static final UninstallPluginProjectionRoot UNINSTALL_PLUGIN_PROJECTION_ROOT = new UninstallPluginProjectionRoot()
            .success()
            .errors();

    public static void validatePlugin1(org.deltafi.common.types.Plugin plugin1) {
        assertEquals("org.deltafi", plugin1.getPluginCoordinates().getGroupId());
        assertEquals("plugin-1", plugin1.getPluginCoordinates().getArtifactId());
        assertEquals("1.0.0", plugin1.getPluginCoordinates().getVersion());

        assertEquals("Test Plugin 1", plugin1.getDisplayName());
        assertEquals("This is a test plugin", plugin1.getDescription());
        assertEquals("1.1.0", plugin1.getActionKitVersion());

        assertEquals(2, plugin1.getActions().size());
        assertEquals("org.deltafi.test.actions.TestAction1", plugin1.getActions().get(0).getName());
        assertEquals("org.deltafi.test.actions.TestAction2", plugin1.getActions().get(1).getName());
        assertEquals("TestAction2", plugin1.getActions().get(1).getDescription());
        assertEquals(1, plugin1.getActions().get(1).getRequiresDomains().size());
        assertEquals("test", plugin1.getActions().get(1).getRequiresDomains().get(0));

        assertEquals(2, plugin1.getDependencies().size());
        assertEquals("org.deltafi", plugin1.getDependencies().get(0).getGroupId());
        assertEquals("plugin-2", plugin1.getDependencies().get(0).getArtifactId());
        assertEquals("1.0.0", plugin1.getDependencies().get(0).getVersion());
        assertEquals("plugin-3", plugin1.getDependencies().get(1).getArtifactId());

        assertEquals(2, plugin1.getPropertySets().size());
        assertEquals("propertySet1", plugin1.getPropertySets().get(0).getId());
        assertEquals("Property Set 1", plugin1.getPropertySets().get(0).getDisplayName());
        assertEquals("A description of property set 1", plugin1.getPropertySets().get(0).getDescription());
        assertEquals(2, plugin1.getPropertySets().get(0).getProperties().size());
        assertEquals("property1", plugin1.getPropertySets().get(0).getProperties().get(0).getKey());
        assertEquals("A description of property 1", plugin1.getPropertySets().get(0).getProperties().get(0).getDescription());
        assertEquals("property1Default", plugin1.getPropertySets().get(0).getProperties().get(0).getDefaultValue());
        assertTrue(plugin1.getPropertySets().get(0).getProperties().get(0).isRefreshable());
        assertTrue(plugin1.getPropertySets().get(0).getProperties().get(0).isEditable());
        assertFalse(plugin1.getPropertySets().get(0).getProperties().get(0).isHidden());
        assertEquals("property1Value", plugin1.getPropertySets().get(0).getProperties().get(0).getValue());
        assertEquals("property4Value", plugin1.getPropertySets().get(1).getProperties().get(1).getValue());
        assertEquals("propertySet2", plugin1.getPropertySets().get(1).getId());
    }
}
