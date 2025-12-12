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
import org.deltafi.actionkit.lookup.*;
import org.deltafi.actionkit.properties.ActionsProperties;
import org.deltafi.actionkit.registration.PluginRegistrar;
import org.deltafi.actionkit.service.ActionEventQueue;
import org.deltafi.actionkit.service.HostnameService;
import org.deltafi.common.action.EventQueueProperties;
import org.deltafi.common.graphql.dgs.GraphQLClientFactory;
import org.deltafi.common.http.HttpService;
import org.deltafi.actionkit.lookup.LookupTableClient;
import org.deltafi.actionkit.lookup.LookupTableSupplier;
import org.deltafi.common.queue.valkey.ValkeyKeyedBlockingQueue;
import org.deltafi.common.ssl.SslAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.annotation.Nullable;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.util.*;

@AutoConfiguration
@EnableConfigurationProperties({ActionsProperties.class, EventQueueProperties.class})
@AutoConfigureAfter(SslAutoConfiguration.class)
@EnableScheduling
public class ActionKitAutoConfiguration {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Bean
    public StartupFailureHandler startupFailureHandler() {
        return new StartupFailureHandler();
    }

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

    @Bean
    public ActionEventQueue actionEventQueue(ValkeyKeyedBlockingQueue valkeyKeyedBlockingQueue) {
        return new ActionEventQueue(valkeyKeyedBlockingQueue);
    }

    @Bean
    public PluginRegistrar pluginRegistrar(ActionRunner actionRunner,
            LookupTableSupplierRunner lookupTableSupplierRunner, BuildProperties buildProperties,
            ApplicationContext applicationContext, Environment environment, SchemaGenerator schemaGenerator) {
        return new PluginRegistrar(actionRunner, lookupTableSupplierRunner, buildProperties, applicationContext,
                environment, schemaGenerator);
    }

    @Bean
    @DependsOn({"lookupTableSupplierRunner"})
    public ActionRunner actionRunner() {
        return new ActionRunner();
    }

    @Bean
    public HostnameService hostnameService(ActionsProperties actionsProperties) {
        return new HostnameService(actionsProperties);
    }

    @Bean
    public HeartbeatService heartbeatService() {
        return new HeartbeatService();
    }

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

    @Bean
    public LookupTableSupplierRunner lookupTableSupplierRunner(ActionEventQueue actionEventQueue,
            List<LookupTableSupplier> lookupTableSuppliers) {
        return new LookupTableSupplierRunner(actionEventQueue, lookupTableSuppliers);
    }

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

    @ConditionalOnMissingBean
    @Bean
    public LookupTableClient lookupTableClient(Environment environment, GraphQLClient graphQLClient,
            HttpService httpService) {
        String coreUrl = buildCoreUrl(environment);
        return new LookupTableClient(coreUrl + "/lookup", graphQLClient, httpService);
    }
}
