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
package org.deltafi.core.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.types.Plugin;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.integration.config.Configuration;
import org.deltafi.core.plugin.PluginRegistryService;
import org.deltafi.core.services.DataSourceService;
import org.deltafi.core.services.EgressFlowService;
import org.deltafi.core.services.FlowService;
import org.deltafi.core.services.TransformFlowService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigurationValidator {

    private final DataSourceService dataSourceService;
    private final TransformFlowService transformFlowService;
    private final EgressFlowService egressFlowService;
    private final PluginRegistryService pluginRegistryService;

    public List<String> validateConfig(Configuration config) {
        List<String> errors = new ArrayList<>();

        if (config.getPlugins() == null || config.getPlugins().isEmpty()) {
            errors.add("Test configuration must specify at least one plugin");
        } else {
            errors.addAll(checkPlugins(config.getPlugins()));
        }

        if (config.getDataSources() != null) {
            errors.addAll(checkOrStartFlow("dataSource", dataSourceService, config.getDataSources()));
        }

        if (config.getTransformationFlows() != null) {
            errors.addAll(checkOrStartFlow("transformation", transformFlowService, config.getTransformationFlows()));
        }

        if (config.getEgressFlows() != null) {
            errors.addAll(checkOrStartFlow("egress", egressFlowService, config.getEgressFlows()));
        }

        errors.addAll(validateInput(config));
        errors.addAll(validateExpectedDeltaFile(config));

        return errors;
    }

    private List<String> validateInput(Configuration config) {
        if (config.getInput() == null) {
            return List.of("Test configuration is missing input");
        }

        return config.getInput().validate(config.getDataSources());
    }

    private List<String> validateExpectedDeltaFile(Configuration config) {
        if (config.getExpectedDeltaFile() == null) {
            return List.of("Test configuration is missing expectedDeltaFile");
        }

        return config.getExpectedDeltaFile().validate(0);
    }

    private Collection<String> checkPlugins(List<PluginCoordinates> plugins) {
        List<String> errors = new ArrayList<>();
        List<Plugin> allInstalledPlugins = null;
        for (PluginCoordinates pluginCoordinates : plugins) {
            if (StringUtils.isEmpty(pluginCoordinates.getGroupId()) || StringUtils.isEmpty(pluginCoordinates.getArtifactId())) {
                errors.add("Invalid plugin specified: " + pluginCoordinates);
            } else {
                if (StringUtils.isEmpty(pluginCoordinates.getVersion())) {
                    // find without version
                    if (allInstalledPlugins == null) {
                        allInstalledPlugins = pluginRegistryService.getPlugins();
                    }
                    boolean found = false;
                    for (Plugin p : allInstalledPlugins) {
                        if (p.getPluginCoordinates().equalsIgnoreVersion(pluginCoordinates)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        errors.add("Plugin not found: " + pluginCoordinates.getGroupId() + ":" + pluginCoordinates.getArtifactId() + ":*");
                    }

                } else if (pluginRegistryService.getPlugin(pluginCoordinates).isEmpty()) {
                    errors.add("Plugin not found: " + pluginCoordinates);
                }
            }
        }
        return errors;
    }

    private Collection<String> checkOrStartFlow(String type, FlowService<?, ?, ?> flowService, List<String> flows) {
        List<String> errors = new ArrayList<>();
        for (String flow : flows) {
            if (!flowService.hasFlow(flow)) {
                errors.add("Flow does not exist (" + type + "): " + flow);
            } else if (!flowService.hasRunningFlow(flow)) {
                if (!flowService.startFlow(flow)) {
                    errors.add("Could not start flow (" + type + "): " + flow);
                }
            }
        }
        return errors;
    }

}
