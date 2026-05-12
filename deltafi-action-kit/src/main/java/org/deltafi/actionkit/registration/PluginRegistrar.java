/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
// ABOUTME: Registers the plugin and its actions with the DeltaFi core on startup.
// ABOUTME: Builds the plugin registration payload including actions, flows, variables, and tests.
package org.deltafi.actionkit.registration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.transform.Join;
import org.deltafi.actionkit.lookup.LookupTableSupplier;
import org.deltafi.actionkit.lookup.LookupTableSupplierRunner;
import org.deltafi.common.types.*;
import org.deltafi.common.types.integration.IntegrationTest;
import org.deltafi.common.util.ClasspathScanner;
import org.deltafi.common.util.ResourceMapper;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Registers the plugin and its actions with the DeltaFi core on startup. Framework-agnostic:
 * discovers classpath resources, build metadata, and the {@code IMAGE} / {@code IMAGE_PULL_SECRET}
 * environment variables itself. The wire transport is pluggable via {@link CoreClient}.
 */
@Slf4j
@RequiredArgsConstructor
public class PluginRegistrar {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

    private static final String FLOWS_DIRECTORY = "flows";
    private static final String INTEGRATION_DIRECTORY = "integration";

    private final List<Action<?, ?, ?>> singletonActions;
    private final LookupTableSupplierRunner lookupTableSupplierRunner;
    private final SchemaGenerator schemaGenerator;
    private final BuildInfo buildInfo;
    private final CoreClient coreClient;
    private final ClassLoader classLoader;

    /** Build and submit the plugin registration. Closes the CoreClient on the way out. */
    @PostConstruct
    public void register() {
        PluginRegistration registration = buildPluginRegistration();
        log.info("Registering plugin with core: {}", registration.getPluginCoordinates());
        try (coreClient) {
            coreClient.register(registration);
        } catch (Exception e) {
            throw new IllegalStateException("Plugin registration failed", e);
        }
    }

    /**
     * Read environment configuration. Override to redirect to a framework lookup (Spring
     * {@code Environment}, Micronaut, a test fixture).
     */
    protected String env(String name) {
        return System.getenv(name);
    }

    private PluginRegistration buildPluginRegistration() {
        PluginRegistration.PluginRegistrationBuilder builder = PluginRegistration.builder()
                .image(env("IMAGE"))
                .imagePullSecret(env("IMAGE_PULL_SECRET"))
                .pluginCoordinates(buildInfo.pluginCoordinates())
                .displayName(buildInfo.name())
                .description(buildInfo.description())
                .actionKitVersion(buildInfo.actionKitVersion())
                .dependencies(buildInfo.dependenciesList())
                .actions(singletonActions.stream().map(this::buildActionDescriptor).toList())
                .lookupTables(lookupTableSupplierRunner.getLookupTableSupplierMap().values().stream()
                        .map(LookupTableSupplier::getLookupTable).toList());

        if (classLoader.getResource(FLOWS_DIRECTORY) != null) {
            List<URL> flowResources = ClasspathScanner.list(classLoader, FLOWS_DIRECTORY);
            builder.variables(loadVariables(flowResources)).flowPlans(loadFlowPlans(flowResources));
        } else {
            log.info("No flows directory exists to load variables or flows");
        }

        if (classLoader.getResource(INTEGRATION_DIRECTORY) != null) {
            builder.integrationTests(loadIntegrationTests());
        } else {
            log.info("No tests directory exists to load integration tests");
        }

        return builder.build();
    }

    private ActionDescriptor buildActionDescriptor(Action<?, ?, ?> action) {
        Map<String, Object> schema = OBJECT_MAPPER.convertValue(
                schemaGenerator.generateSchema(action.getParamClass()),
                new TypeReference<>() {});

        return ActionDescriptor.builder()
                .name(action.getClassCanonicalName())
                .type(action.getActionType())
                .supportsJoin(action instanceof Join)
                .schema(schema)
                .actionOptions(action.getActionOptions())
                .build();
    }

    private List<Variable> loadVariables(List<URL> flowResources) {
        URL url = findVariables(flowResources);
        if (url == null) {
            log.info("No plugin variables have been defined");
            return Collections.emptyList();
        }
        try {
            return ResourceMapper.readValues(url, Variable.class);
        } catch (IOException e) {
            log.warn("Unable to load variables", e);
            return Collections.emptyList();
        }
    }

    private URL findVariables(List<URL> flowResources) {
        for (String extension : List.of(".json", ".yaml", ".yml", ".jsonl")) {
            String target = "variables" + extension;
            for (URL url : flowResources) {
                if (target.equals(ClasspathScanner.filenameOf(url))) {
                    return url;
                }
            }
        }
        return null;
    }

    private List<FlowPlan> loadFlowPlans(List<URL> flowResources) {
        if (flowResources.isEmpty()) {
            log.info("No flow plans exist in the flows directory");
            return Collections.emptyList();
        }
        List<FlowPlan> flowPlans = new ArrayList<>();
        for (URL url : flowResources) {
            String filename = ClasspathScanner.filenameOf(url);
            if (filename.startsWith("variables.") || invalidExtension(filename)) {
                continue;
            }
            try {
                flowPlans.add(ResourceMapper.readValue(url, FlowPlan.class));
            } catch (IOException e) {
                log.warn("Unable to load flow plan ({})", filename, e);
            }
        }
        return flowPlans;
    }

    private List<IntegrationTest> loadIntegrationTests() {
        List<URL> resources = ClasspathScanner.list(classLoader, INTEGRATION_DIRECTORY);
        if (resources.isEmpty()) {
            log.info("No tests exist in the tests directory");
            return Collections.emptyList();
        }
        List<IntegrationTest> tests = new ArrayList<>();
        for (URL url : resources) {
            String filename = ClasspathScanner.filenameOf(url);
            if (invalidExtension(filename)) {
                continue;
            }
            try {
                tests.add(ResourceMapper.readValue(url, IntegrationTest.class));
            } catch (IOException e) {
                log.warn("Unable to load test ({})", filename, e);
            }
        }
        return tests;
    }

    private boolean invalidExtension(String filename) {
        return !filename.endsWith(".json") && !filename.endsWith(".yaml") && !filename.endsWith(".yml");
    }
}
