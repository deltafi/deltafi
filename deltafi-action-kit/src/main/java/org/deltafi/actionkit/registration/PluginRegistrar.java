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
package org.deltafi.actionkit.registration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import feign.Retryer;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.properties.ActionsProperties;
import org.deltafi.common.http.client.feign.FeignClientFactory;
import org.deltafi.common.types.FlowPlan;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.common.types.PluginRegistration;
import org.deltafi.common.types.Variable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
public class PluginRegistrar {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    @Autowired(required = false)
    private final List<Action<?>> actions = Collections.emptyList();

    @Autowired
    BuildProperties buildProperties;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    ActionsProperties actionsProperties;

    @Value("${CORE_URL:http://deltafi-core-service}")
    private String coreUrl;

    public void register() {
        PluginRegistration pluginRegistration = buildPluginRegistration();

        log.info("Registering plugin with core: {}", pluginRegistration.getPluginCoordinates());
        CoreClient coreClient = FeignClientFactory.build(CoreClient.class, coreUrl, new JacksonEncoder(OBJECT_MAPPER), new JacksonDecoder(OBJECT_MAPPER), new Retryer.Default(500, 2000, 3));
        coreClient.postPlugin(pluginRegistration);
    }

    private PluginRegistration buildPluginRegistration() {
        PluginCoordinates pluginCoordinates = PluginCoordinates.builder()
                .groupId(buildProperties.getGroup())
                .artifactId(buildProperties.getArtifact())
                .version(buildProperties.getVersion())
                .build();

        PluginRegistration.PluginRegistrationBuilder pluginRegistrationBuilder = PluginRegistration.builder()
                .pluginCoordinates(pluginCoordinates)
                .displayName(buildProperties.getName())
                .description(buildProperties.get("description"))
                .actionKitVersion(buildProperties.get("actionKitVersion"))
                .dependencies(toPluginCoordinatesList(buildProperties.get("pluginDependencies")))
                .actions(actions.stream().map(Action::getActionDescriptor).toList());

        Resource flowsDirectory = applicationContext.getResource("classpath:flows");
        if (flowsDirectory.exists()) {
            pluginRegistrationBuilder.variables(loadVariables()).flowPlans(loadFlowPlans());
        } else {
            log.info("No flows directory exists to load variables or flows");
        }

        return pluginRegistrationBuilder.build();
    }

    private List<PluginCoordinates> toPluginCoordinatesList(String pluginDependencies) {
        return pluginDependencies == null ? List.of() :
            Arrays.stream(pluginDependencies.split(",\\s?")).map(PluginCoordinates::new).toList();
    }

    private List<Variable> loadVariables() {
        Resource variablesResource = applicationContext.getResource("classpath:flows/variables.json");
        if (!variablesResource.exists()) {
            log.info("No flow variables have been defined");
            return null;
        }

        try {
            String variablesJson = new String(variablesResource.getInputStream().readAllBytes());
            return OBJECT_MAPPER.readValue(variablesJson, new TypeReference<>() {});
        } catch (IOException e) {
            log.warn("Unable to load variables", e);
            return Collections.emptyList();
        }
    }

    private List<FlowPlan> loadFlowPlans() {
        Resource[] flowPlanResources;
        try {
            flowPlanResources = applicationContext.getResources("classpath:flows/*");
        } catch (IOException e) {
            log.warn("Unable to load flow plans", e);
            return Collections.emptyList();
        }

        if (flowPlanResources.length == 0) {
            log.info("No flow plans exist in the flows directory");
            return Collections.emptyList();
        }

        List<FlowPlan> flowPlans = new ArrayList<>();
        for (Resource flowPlanResource : flowPlanResources) {
            if ("variables.json".equals(flowPlanResource.getFilename())) {
                continue;
            }
            try {
                String flowJson = new String(flowPlanResource.getInputStream().readAllBytes());
                flowPlans.add(OBJECT_MAPPER.readValue(flowJson, FlowPlan.class));
            } catch (IOException e) {
                log.warn("Unable to load flow plan ({})", flowPlanResource.getFilename(), e);
            }
        }
        return flowPlans;
    }
}
