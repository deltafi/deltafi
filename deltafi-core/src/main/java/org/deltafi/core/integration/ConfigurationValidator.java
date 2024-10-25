/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.services.*;
import org.deltafi.core.types.PluginEntity;
import org.deltafi.core.types.integration.ExpectedDeltaFile;
import org.deltafi.core.types.integration.IntegrationTest;
import org.deltafi.core.types.integration.TestCaseIngress;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigurationValidator {
    // 1 second longer then the refresh in FlowConfigurationCacheEvictScheduler:
    private static final Long FLOW_START_DELAY_MILLIS = 6000L;

    private final RestDataSourceService restDataSourceService;
    private final TransformFlowService transformFlowService;
    private final DataSinkService dataSinkService;
    @Lazy
    private final PluginService pluginService;

    public static List<String> validateExpectedDeltaFiles(IntegrationTest config) {
        if (config.getExpectedDeltaFiles() == null || config.getExpectedDeltaFiles().isEmpty()) {
            return List.of("Test configuration is missing expectedDeltaFiles");
        }

        List<String> errors = new ArrayList<>();
        for (ExpectedDeltaFile expectedDeltaFile : config.getExpectedDeltaFiles()) {
            errors.addAll(expectedDeltaFile.validate(0));
        }
        return errors;
    }

    public List<String> preSaveCheck(IntegrationTest config) {
        return validateConfig(config, false);
    }

    public List<String> validateToStart(IntegrationTest config) {
        return validateConfig(config, true);
    }

    private List<String> validateConfig(IntegrationTest config, boolean prepareToStart) {
        List<String> errors = new ArrayList<>();

        if (StringUtils.isEmpty(config.getName())) {
            errors.add("Test configuration must specify a 'name'");
        }

        if (StringUtils.isEmpty(config.getDescription())) {
            errors.add("Test configuration must specify a 'description'");
        }

        if (StringUtils.isNoneEmpty(config.getTimeout())) {
            try {
                Duration.parse(config.getTimeout());
            } catch (Exception e) {
                errors.add("Invalid 'timeout'; must be a valid Duration");
            }
        }

        if (config.getPlugins() == null || config.getPlugins().isEmpty()) {
            errors.add("Test configuration must specify at least one plugin");
        } else {
            if (prepareToStart) {
                errors.addAll(checkPlugins(config.getPlugins()));
            }
        }

        boolean flowsStarted = false;
        if (prepareToStart) {
            if (config.getDataSources() != null) {
                if (checkOrStartFlows(errors, "dataSource", restDataSourceService, config.getDataSources())) {
                    flowsStarted = true;
                }
            }

            if (config.getTransformationFlows() != null) {
                if (checkOrStartFlows(errors, "transformation", transformFlowService, config.getTransformationFlows())) {
                    flowsStarted = true;
                }
            }

            if (config.getDataSinks() != null) {
                if (checkOrStartFlows(errors, "dataSink", dataSinkService, config.getDataSinks())) {
                    flowsStarted = true;
                }
            }
        }

        errors.addAll(validateInputs(config));
        errors.addAll(validateExpectedDeltaFiles(config));

        if (errors.isEmpty() && prepareToStart && flowsStarted) {
            try {
                // Allow core and core-workers to sync
                Thread.sleep(FLOW_START_DELAY_MILLIS);
            } catch (Exception e) {
                errors.add("Thread error during sync delay");
            }
        }

        return errors;
    }

    private List<String> validateInputs(IntegrationTest config) {
        if (config.getInputs() == null || config.getInputs().isEmpty()) {
            return List.of("Test configuration is missing 'inputs''");
        }

        List<String> errors = new ArrayList<>();
        for (TestCaseIngress input : config.getInputs()) {
            errors.addAll(input.validate(config.getDataSources()));
        }
        return errors;
    }

    private Collection<String> checkPlugins(List<PluginCoordinates> plugins) {
        List<String> errors = new ArrayList<>();
        List<PluginEntity> allInstalledPlugins = null;
        for (PluginCoordinates pluginCoordinates : plugins) {
            if (StringUtils.isEmpty(pluginCoordinates.getGroupId()) || StringUtils.isEmpty(pluginCoordinates.getArtifactId())) {
                errors.add("Invalid plugin specified: " + pluginCoordinates);
            } else {
                if ("ANY".equals(pluginCoordinates.getVersion())) {
                    // find without version
                    if (allInstalledPlugins == null) {
                        allInstalledPlugins = pluginService.getPlugins();
                    }
                    boolean found = false;
                    for (PluginEntity p : allInstalledPlugins) {
                        if (p.getPluginCoordinates().equalsIgnoreVersion(pluginCoordinates)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        errors.add("Plugin not found: " + pluginCoordinates.getGroupId() + ":" + pluginCoordinates.getArtifactId() + ":*");
                    }

                } else if (pluginService.getPlugin(pluginCoordinates).isEmpty()) {
                    errors.add("Plugin not found: " + pluginCoordinates);
                }
            }
        }
        return errors;
    }

    private boolean checkOrStartFlows(List<String> errors, String type, FlowService<?, ?, ?, ?> flowService, List<String> flows) {
        boolean flowStarted = false;
        for (String flow : flows) {
            if (!flowService.hasFlow(flow)) {
                errors.add("Flow does not exist (" + type + "): " + flow);
            } else if (!flowService.hasRunningFlow(flow)) {
                if (!flowService.startFlow(flow)) {
                    errors.add("Could not start flow (" + type + "): " + flow);
                } else {
                    flowStarted = true;
                }
            }
        }
        return flowStarted;
    }

}
