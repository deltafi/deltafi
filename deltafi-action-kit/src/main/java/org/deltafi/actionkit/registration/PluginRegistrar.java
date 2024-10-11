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
package org.deltafi.actionkit.registration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import feign.Retryer;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.parameters.ActionParametersSchemaGenerator;
import org.deltafi.actionkit.action.transform.Join;
import org.deltafi.common.http.client.feign.FeignClientFactory;
import org.deltafi.common.types.*;
import org.deltafi.common.util.ResourceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.*;

@Slf4j
public class PluginRegistrar {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

    @Autowired(required = false)
    private final List<Action<?, ?, ?>> actions = Collections.emptyList();

    @Autowired
    BuildProperties buildProperties;

    @Autowired
    ApplicationContext applicationContext;

    @Value("${CORE_URL}")
    private String coreUrl;

    public void register() {
        PluginRegistration pluginRegistration = buildPluginRegistration();

        log.info("Registering plugin with core: {}", pluginRegistration.getPluginCoordinates());
        CoreClient coreClient = FeignClientFactory.build(CoreClient.class, coreUrl, new JacksonEncoder(OBJECT_MAPPER),
                new JacksonDecoder(OBJECT_MAPPER), new Retryer.Default(500, 2000, 3));
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
                .actions(actions.stream().map(this::buildActionDescriptor).toList());

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

    private ActionDescriptor buildActionDescriptor(Action<?, ?, ?> action) {
        Map<String, Object> schema = OBJECT_MAPPER.convertValue(
                ActionParametersSchemaGenerator.generateSchema(action.getParamClass()),
                new TypeReference<>() {});

        return ActionDescriptor.builder()
                .name(action.getClassCanonicalName())
                .description(action.getDescription())
                .type(action.getActionType())
                .supportsJoin(action instanceof Join)
                .schema(schema)
                .build();
    }

    private List<Variable> loadVariables() {
        Resource variablesResource = findVariables();
        if (variablesResource == null) {
            log.info("No flow variables have been defined");
            return null;
        }

        try {
            return ResourceMapper.readValues(variablesResource, Variable.class);
        } catch (IOException e) {
            log.warn("Unable to load variables", e);
            return Collections.emptyList();
        }
    }

    private Resource findVariables() {
        for (String extension : List.of(".json", ".yaml", ".yml", ".jsonl")) {
            Resource variablesResource = applicationContext.getResource("classpath:flows/variables" + extension);
            if (variablesResource.exists()) {
                return variablesResource;
            }
        }
        return null;
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
            String filename = flowPlanResource.getFilename();
            if (filename == null || filename.startsWith("variables.")) {
                continue;
            }
            try {
                flowPlans.add(ResourceMapper.readValue(flowPlanResource, FlowPlan.class));
            } catch (IOException e) {
                log.warn("Unable to load flow plan ({})", flowPlanResource.getFilename(), e);
            }
        }
        return flowPlans;
    }
}
