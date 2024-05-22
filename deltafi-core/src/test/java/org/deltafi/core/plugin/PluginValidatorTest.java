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

import org.deltafi.common.types.ActionDescriptor;
import org.deltafi.common.types.Plugin;
import org.deltafi.common.types.PluginCoordinates;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class PluginValidatorTest {
    @Mock
    PluginRepository pluginRepository;

    @InjectMocks
    PluginValidator pluginValidator;

    @Test
    void testValidate_Coords() {
        Plugin plugin = new Plugin();
        List<String> errors = pluginValidator.validate(plugin);
        assertThat(errors).hasSize(1).contains("The plugin coordinates must be provided");

        plugin.setPluginCoordinates(new PluginCoordinates("", null, "  "));
        errors = pluginValidator.validate(plugin);
        assertThat(errors).hasSize(3).contains("The plugin groupId cannot be null or empty",
                "The plugin artifactId cannot be null or empty",
                "The plugin version cannot be null or empty");
    }

    @Test
    void validate() {
        Plugin plugin1 = new Plugin();
        PluginCoordinates pluginCoordinates1 = new PluginCoordinates("group", "plugin-1", "1.0.0");
        plugin1.setPluginCoordinates(pluginCoordinates1);
        Plugin plugin2 = new Plugin();
        PluginCoordinates pluginCoordinates2 = new PluginCoordinates("group", "plugin-2", "1.0.0");
        plugin2.setPluginCoordinates(pluginCoordinates2);

        Mockito.when(pluginRepository.findAll()).thenReturn(List.of(plugin1, plugin2));

        Plugin plugin = new Plugin();
        plugin.setPluginCoordinates(new PluginCoordinates("group", "plugin", "1.0.0"));
        plugin.setDependencies(List.of(new PluginCoordinates("group", "unregistered", "1.0.0"),
                new PluginCoordinates("group", "plugin-1", "1.0.1")));
        List<String> dependencyErrors = pluginValidator.validate(plugin);

        assertThat(dependencyErrors).hasSize(2)
                        .contains("Plugin dependency not registered: group:unregistered:1.0.0.",
                                "Plugin dependency for group:plugin-1 not satisfied. Required version 1.0.1 but installed version is 1.0.0.");
    }

    @Test
    void validateUniqueActions() {
        Plugin pluginOldVersion = new Plugin();
        PluginCoordinates olderVersion = new PluginCoordinates("group", "plugin-1", "1.0.0");
        pluginOldVersion.setPluginCoordinates(olderVersion);
        pluginOldVersion.setActions(List.of(ActionDescriptor.builder().name("org.deltafi.A1").build()));

        Plugin pluginNewVersion = new Plugin();
        PluginCoordinates newVersion = new PluginCoordinates("group", "plugin-1", "1.0.1");
        pluginNewVersion.setPluginCoordinates(newVersion);
        pluginNewVersion.setActions(List.of(ActionDescriptor.builder().name("org.deltafi.A1").build()));

        Plugin differentPlugin = new Plugin();
        PluginCoordinates differentVersion = new PluginCoordinates("group", "plugin-2", "1.0.0");
        differentPlugin.setPluginCoordinates(differentVersion);
        differentPlugin.setActions(List.of(ActionDescriptor.builder().name("org.deltafi.A1").build()));

        List<String> errors = pluginValidator.validateUniqueActions(pluginNewVersion, List.of(pluginOldVersion, differentPlugin));
        assertThat(errors).hasSize(1).contains("Action 'org.deltafi.A1' has registered in another plugin 'group:plugin-2:1.0.0'");
    }
}
