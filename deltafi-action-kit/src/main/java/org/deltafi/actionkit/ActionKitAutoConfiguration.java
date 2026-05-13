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
// ABOUTME: Spring auto-configuration that wires up all action-kit beans.
// ABOUTME: Creates the event queue, action runner, schema generator, and lookup table infrastructure.
package org.deltafi.actionkit;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import com.netflix.graphql.dgs.client.GraphQLClient;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.parameters.DataSizeDefinitionProvider;
import org.deltafi.actionkit.action.parameters.EnvVarDefinitionProvider;
import org.deltafi.actionkit.action.parameters.SchemaGeneratorConfigCustomizer;
import org.deltafi.actionkit.action.parameters.annotation.Size;
import org.deltafi.actionkit.action.service.ActionRunner;
import org.deltafi.actionkit.action.service.HeartbeatService;
import org.deltafi.actionkit.artifact.ArtifactConfiguration;
import org.deltafi.actionkit.lookup.*;
import org.deltafi.actionkit.properties.ActionsProperties;
import org.deltafi.actionkit.registration.CoreClient;
import org.deltafi.actionkit.registration.HttpCoreClient;
import org.deltafi.actionkit.registration.PluginRegistrar;
import org.deltafi.actionkit.service.ActionEventQueue;
import org.deltafi.actionkit.service.HostnameService;
import org.deltafi.common.action.EventQueueProperties;
import org.deltafi.common.graphql.dgs.GraphQLClientFactory;
import org.deltafi.common.http.HttpService;
import org.deltafi.common.listener.StartupFailureListener;
import org.deltafi.actionkit.lookup.LookupTableClient;
import org.deltafi.actionkit.lookup.LookupTableSupplier;
import org.deltafi.common.queue.valkey.ValkeyKeyedBlockingQueue;
import org.deltafi.common.ssl.SslAutoConfiguration;
import org.deltafi.common.types.BuildInfo;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.annotation.Nullable;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.util.*;

/**
 * Spring auto-configuration that wires up the action-kit infrastructure beans
 */
@AutoConfiguration
@EnableConfigurationProperties({ActionsProperties.class, EventQueueProperties.class})
@AutoConfigureAfter(SslAutoConfiguration.class)
@EnableScheduling
@Import(ArtifactConfiguration.class)
public class ActionKitAutoConfiguration {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Create the listener that handles startup failures.
     * @return a StartupFailureListener instance
     */
    @Bean
    public StartupFailureListener startupFailureHandler() {
        return new StartupFailureListener();
    }

    /**
     * Create the Valkey blocking queue sized for the total number of action threads plus housekeeping.
     * @param actionsProperties properties controlling action thread counts
     * @param eventQueueProperties properties for the Valkey connection
     * @param actions all registered actions
     * @param lookupTableSuppliers all registered lookup table suppliers
     * @return a configured ValkeyKeyedBlockingQueue
     * @throws URISyntaxException if the Valkey URI is malformed
     */
    @Bean
    public ValkeyKeyedBlockingQueue valkeyKeyedBlockingQueue(ActionsProperties actionsProperties,
            EventQueueProperties eventQueueProperties, List<Action<?, ?, ?>> actions,
            List<LookupTableSupplier> lookupTableSuppliers) throws URISyntaxException {
        // Calculate the total number of threads for all actions
        int totalThreads = actions.stream()
                .mapToInt(action -> actionsProperties.getActionThreads().getOrDefault(action.getClassCanonicalName(), 1))
                .sum();

        // Add one thread to handle all lookup table suppliers
        if (!lookupTableSuppliers.isEmpty()) {
            totalThreads++;
        }

        // Add a thread for heartbeats
        totalThreads += 1;

        return new ValkeyKeyedBlockingQueue(eventQueueProperties, totalThreads);
    }

    /**
     * Create the action event queue backed by Valkey.
     * @param valkeyKeyedBlockingQueue the underlying Valkey queue
     * @return a configured ActionEventQueue
     */
    @Bean
    public ActionEventQueue actionEventQueue(ValkeyKeyedBlockingQueue valkeyKeyedBlockingQueue) {
        return new ActionEventQueue(valkeyKeyedBlockingQueue);
    }

    /**
     * Translate Spring Boot's {@link BuildProperties} into the framework-agnostic {@link BuildInfo}
     * consumed by both the registrar and the action runner. Plugin authors can override this bean
     * to supply build metadata from another source.
     */
    @Bean
    @ConditionalOnMissingBean
    public BuildInfo buildInfo(BuildProperties buildProperties) {
        return new BuildInfo(
                buildProperties.getGroup(),
                buildProperties.getArtifact(),
                buildProperties.getVersion(),
                buildProperties.getName(),
                buildProperties.get("description"),
                buildProperties.get("actionKitVersion"),
                buildProperties.get("pluginDependencies"));
    }

    /** Default HTTP transport for plugin registration; replace by defining your own CoreClient bean. */
    @Bean
    @ConditionalOnMissingBean
    public CoreClient coreClient(Environment environment) {
        return new HttpCoreClient(environment.getProperty("CORE_URL", "http://localhost"));
    }

    /**
     * Create the registrar that registers the plugin and its actions with the core.
     * Runs before {@code actionRunner} so the plugin cannot consume work until registration is acknowledged.
     */
    @Bean
    public PluginRegistrar pluginRegistrar(LookupTableSupplierRunner lookupTableSupplierRunner,
            List<Action<?, ?, ?>> actions, BuildInfo buildInfo, CoreClient coreClient,
            SchemaGenerator schemaGenerator) {
        return new PluginRegistrar(actions, lookupTableSupplierRunner, schemaGenerator,
                buildInfo, coreClient, Thread.currentThread().getContextClassLoader());
    }

    /**
     * Create the action runner that polls for and executes actions.
     * Depends on {@code pluginRegistrar} so workers are not constructed until registration succeeds.
     */
    @Bean
    @DependsOn({"lookupTableSupplierRunner", "pluginRegistrar"})
    public ActionRunner actionRunner() {
        return new ActionRunner();
    }

    /**
     * Create the hostname service.
     * @param actionsProperties properties that may override the hostname
     * @return a configured HostnameService
     */
    @Bean
    public HostnameService hostnameService(ActionsProperties actionsProperties) {
        return new HostnameService(actionsProperties);
    }

    /** Create and start the heartbeat service
     * @return a configured HeartbeatService
     */
    @Bean
    public HeartbeatService heartbeatService(ActionEventQueue actionEventQueue, ActionRunner actionRunner) {
        HeartbeatService service = new HeartbeatService(actionEventQueue, actionRunner);
        service.start();
        return service;
    }

    /**
     * Create the JSON schema generator used to produce parameter schemas for action registration.
     * @param schemaGeneratorCustomizer optional customizer for the schema generator config
     * @return a configured SchemaGenerator
     */
    @Bean
    public SchemaGenerator parametersSchemaGenerator(@Nullable SchemaGeneratorConfigCustomizer schemaGeneratorCustomizer) {
        SchemaGeneratorConfigBuilder configBuilder =
                new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                        .without(Option.SCHEMA_VERSION_INDICATOR)
                        .with(Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT)
                        .with(Option.INLINE_ALL_SCHEMAS)
                        .with(new JacksonModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED,
                                JacksonOption.IGNORE_TYPE_INFO_TRANSFORM,
                                JacksonOption.FLATTENED_ENUMS_FROM_JSONPROPERTY));

        configBuilder.forFields()
                .withStringMinLengthResolver(fieldScope -> {
                    Size size = fieldScope.getAnnotationConsideringFieldAndGetter(Size.class);
                    return size == null ? null : size.minLength();
                })
                .withStringMaxLengthResolver(fieldScope -> {
                    Size size = fieldScope.getAnnotationConsideringFieldAndGetter(Size.class);
                    return size == null ? null : size.maxLength();
                })
                .withRequiredCheck(fieldScope -> {
                    JsonProperty jsonProperty = fieldScope.getAnnotationConsideringFieldAndGetter(JsonProperty.class);
                    return jsonProperty != null && jsonProperty.required();
                })
                .withDefaultResolver(fieldScope -> {
                    JsonProperty jsonProperty = fieldScope.getAnnotationConsideringFieldAndGetter(JsonProperty.class);
                    if ((jsonProperty == null) || jsonProperty.defaultValue().isEmpty()) {
                        return null;
                    }
                    try {
                        Class<?> type = fieldScope.getDeclaredType().getErasedType();
                        // limit the default types that are mapped, sub-objects should have independent defaults defined as needed
                        if (type.isPrimitive() || Map.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type)) {
                            return OBJECT_MAPPER.readValue(jsonProperty.defaultValue(), new TypeReference<>() {});
                        } else {
                            return jsonProperty.defaultValue();
                        }
                    } catch (JsonProcessingException e) {
                        return jsonProperty.defaultValue();
                    }
                });

            configBuilder.forTypesInGeneral()
                    .withAdditionalPropertiesResolver(scope ->
                            scope.getType().isInstanceOf(Map.class) ? scope.getTypeParameterFor(Map.class, 1) : null);
        configBuilder.forTypesInGeneral()
                .withCustomDefinitionProvider(new DataSizeDefinitionProvider())
                .withCustomDefinitionProvider(new EnvVarDefinitionProvider());

        if (schemaGeneratorCustomizer != null) {
            schemaGeneratorCustomizer.customize(configBuilder);
        }

        return new SchemaGenerator(configBuilder.build());
    }

    /**
     * Create the runner that manages lookup table supplier threads.
     * @param actionEventQueue the event queue for lookup table events
     * @param lookupTableSuppliers all registered lookup table suppliers
     * @return a configured LookupTableSupplierRunner
     */
    @Bean
    public LookupTableSupplierRunner lookupTableSupplierRunner(ActionEventQueue actionEventQueue,
            List<LookupTableSupplier> lookupTableSuppliers) {
        return new LookupTableSupplierRunner(actionEventQueue, lookupTableSuppliers);
    }

    /**
     * Create the GraphQL client for communicating with the core API.
     * @param environment the Spring environment for resolving the core URL
     * @param httpClient the HTTP client to use for requests
     * @return a configured GraphQLClient
     */
    @ConditionalOnMissingBean
    @Bean
    public GraphQLClient graphQLClient(Environment environment, HttpClient httpClient) {
        String coreUrl = buildCoreUrl(environment);
        return new GraphQLClientFactory(httpClient).build(coreUrl + "/graphql", "X-User-Permissions", "Admin",
                "X-User-Name", "deltafi-cli");
    }

    private String buildCoreUrl(Environment environment) {
        return environment.getProperty("CORE_URL", "http://deltafi-core:8080") + "/api/v2";
    }

    /**
     * Create the client for fetching lookup table data from the core.
     * @param environment the Spring environment for resolving the core URL
     * @param graphQLClient the GraphQL client for schema queries
     * @param httpService the HTTP service for REST requests
     * @return a configured LookupTableClient
     */
    @ConditionalOnMissingBean
    @Bean
    public LookupTableClient lookupTableClient(Environment environment, GraphQLClient graphQLClient,
            HttpService httpService) {
        String coreUrl = buildCoreUrl(environment);
        return new LookupTableClient(coreUrl + "/lookup", graphQLClient, httpService);
    }
}
