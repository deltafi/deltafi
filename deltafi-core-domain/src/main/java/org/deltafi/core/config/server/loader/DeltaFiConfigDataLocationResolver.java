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
package org.deltafi.core.config.server.loader;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import lombok.extern.slf4j.Slf4j;
import org.bson.UuidRepresentation;
import org.deltafi.core.config.server.environment.*;
import org.deltafi.core.config.server.environment.factory.GitEnvironmentRepositoryFactory;
import org.deltafi.core.config.server.repo.*;
import org.deltafi.core.config.server.service.PropertyMetadataLoader;
import org.deltafi.core.config.server.service.PropertyService;
import org.deltafi.core.config.server.service.StateHolderService;
import org.springframework.boot.BootstrapContext;
import org.springframework.boot.BootstrapContextClosedEvent;
import org.springframework.boot.BootstrapRegistry;
import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.autoconfigure.mongo.MongoClientFactory;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;
import org.springframework.boot.autoconfigure.mongo.MongoPropertiesClientSettingsBuilderCustomizer;
import org.springframework.boot.context.config.*;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.cloud.config.server.environment.*;
import org.springframework.cloud.config.server.support.TransportConfigCallbackFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Look for the deltafi prefix in the config location. If it is found create each EnvironmentRepository
 * that is needed based on the location settings (for example deltafi:mongodb=true&amp;git=true).
 *
 * Any beans that are needed in the main ApplicationContext will be added when the BootstrapContext is closed
 * to make them available for autowiring, otherwise they are just registered in the BootstrapContext for reuse
 * when the properties are refreshed.
 */
@Slf4j
public class DeltaFiConfigDataLocationResolver implements ConfigDataLocationResolver<DeltaFiConfigDataResource>, Ordered {

    public static final String PREFIX = "deltafi:";
    public static final String MONGODB_PROPERTIES_PREFIX = "spring.data.mongodb";
    public static final String GIT_PROPERTIES_PREFIX = "spring.cloud.config.server.git";
    public static final String NATIVE_PROPERTIES_PREFIX = "spring.cloud.config.server.native";
    public static final String CONFIG_BEAN_NAME_PREFIX = "configData";

    @Override
    public boolean isResolvable(ConfigDataLocationResolverContext context, ConfigDataLocation location) {
        return location.hasPrefix(PREFIX);
    }

    @Override
    public List<DeltaFiConfigDataResource> resolveProfileSpecific(ConfigDataLocationResolverContext context, ConfigDataLocation location, Profiles profiles) throws ConfigDataLocationNotFoundException {
        return Collections.emptyList();
    }

    @Override
    public List<DeltaFiConfigDataResource> resolve(ConfigDataLocationResolverContext context, ConfigDataLocation location) throws ConfigDataLocationNotFoundException, ConfigDataResourceNotFoundException {
        bindAndRegisterProperties(context);
        registerEnvironmentRepositories(context, location);
        return List.of(new DeltaFiConfigDataResource());
    }

    private static void bindAndRegisterProperties(ConfigDataLocationResolverContext context) {
        bindAndRegisterProperties(context, MONGODB_PROPERTIES_PREFIX, MongoProperties.class);
        bindAndRegisterProperties(context, GIT_PROPERTIES_PREFIX, MultipleJGitEnvironmentProperties.class);
        bindAndRegisterProperties(context, NATIVE_PROPERTIES_PREFIX, NativeEnvironmentProperties.class);

        // add the ConfigServerProperties to the ApplicationContext for the EnvironmentController
        bindAndRegisterProperties(context, ConfigServerProperties.PREFIX, ConfigServerProperties.class);
        addBeanToApplicationContext(context, ConfigServerProperties.class);
    }

    private static void registerEnvironmentRepositories(ConfigDataLocationResolverContext context, ConfigDataLocation location) {
        Properties properties = readProperties(location);
        boolean useGit = Boolean.parseBoolean(properties.getOrDefault("git", "false").toString());
        boolean useMongo = Boolean.parseBoolean(properties.getOrDefault("mongodb", "true").toString());

        // do not promote the Mongo related beans, so they are created in the main ApplicationContext with any necessary customizations
        if (useMongo) {
            registerMongoTemplate(context);
        }
        // these beans are only needed in the bootstrap context
        registerBean(context, StateHolderRepository.class, ctx -> DeltaFiConfigDataLocationResolver.buildStateHolderRepository(ctx, useMongo));
        registerEnvRepoPrereqs(context);

        registerAndPromoteBean(context, PropertyRepository.class, ctx -> DeltaFiConfigDataLocationResolver.buildPropertyRepository(ctx, useMongo));

        if (useGit) {
            registerBean(context, GitEnvironmentRepository.class, DeltaFiConfigDataLocationResolver::buildGitEnvRepo);
        } else {
            registerBean(context, DeltaFiNativeEnvironmentRepository.class, DeltaFiConfigDataLocationResolver::buildNativeEnvRepo);
        }

        // Push the PropertyService up for use in the PropertyDataFetcher
        registerAndPromoteBean(context, PropertyService.class, DeltaFiConfigDataLocationResolver::buildPropertyService);
        registerBean(context, MongoEnvironmentRepository.class, DeltaFiConfigDataLocationResolver::buildMongoEnvRepo);
        registerBean(context, DefaultPropertyEnvironmentRepository.class, DeltaFiConfigDataLocationResolver::buildDefaultEnvRepo);

        // Only add the DeltaFiCompositeEnvironmentRepository to the application context so there are no conflicts in the EnvironmentController when it tries to autowire in an EnvironmentRepository
        registerAndPromoteBean(context, DeltaFiCompositeEnvironmentRepository.class, DeltaFiConfigDataLocationResolver::buildCompositeEnvRepo);
    }

    private static void registerMongoTemplate(ConfigDataLocationResolverContext context) {
        context.getBootstrapContext().registerIfAbsent(MongoTemplate.class, DeltaFiConfigDataLocationResolver::buildMongoTemplate);
    }

    private static void registerEnvRepoPrereqs(ConfigDataLocationResolverContext context) {
        registerBean(context, StateHolderService.class, ctx -> new StateHolderService(context.getBootstrapContext().get(StateHolderRepository.class)));
        registerBean(context, ConfigurableEnvironment.class, ctx -> new StandardEnvironment());
        registerBean(context, PropertyMetadataLoader.class, ctx -> new PropertyMetadataLoader());
    }

    private static PropertyRepository buildPropertyRepository(BootstrapContext ctx, boolean useMongo) {
        return useMongo ? new PropertyRepositoryImpl(ctx.get(MongoTemplate.class)) : new PropertyRepositoryInMemoryImpl();
    }

    private static StateHolderRepository buildStateHolderRepository(BootstrapContext ctx, boolean useMongo) {
        return useMongo ? new StateHolderRepositoryImpl(ctx.get(MongoTemplate.class)) : new StateHolderRepositoryInMemoryImpl();
    }

    private static GitEnvironmentRepository buildGitEnvRepo(BootstrapContext ctx) {

        ConfigServerProperties configServerProperties = ctx.get(ConfigServerProperties.class);
        MultipleJGitEnvironmentProperties gitEnvironmentProperties = ctx.get(MultipleJGitEnvironmentProperties.class);
        ConfigurableHttpConnectionFactory configurableHttpConnectionFactory = new HttpClientConfigurableHttpConnectionFactory();

        gitEnvironmentProperties.setDefaultLabel(configServerProperties.getDefaultLabel());

        final TransportConfigCallbackFactory transportConfigCallbackFactory = new TransportConfigCallbackFactory(null, null);
        GitEnvironmentRepositoryFactory gitEnvironmentRepositoryFactory = new GitEnvironmentRepositoryFactory( ctx.get(ConfigurableEnvironment.class),
                configurableHttpConnectionFactory, transportConfigCallbackFactory, ctx.get(StateHolderService.class), configServerProperties.getDefaultLabel());

        try {
            return gitEnvironmentRepositoryFactory.build(gitEnvironmentProperties);
        } catch (Exception e) {
            throw new IllegalStateException("failed to register git environment repository", e);
        }
    }

    private static DeltaFiNativeEnvironmentRepository buildNativeEnvRepo(BootstrapContext ctx) {
        ConfigurableEnvironment configurableEnvironment = ctx.get(ConfigurableEnvironment.class);
        NativeEnvironmentProperties nativeEnvironmentProperties = ctx.get(NativeEnvironmentProperties.class);
        ConfigServerProperties configServerProperties = ctx.get(ConfigServerProperties.class);

        nativeEnvironmentProperties.setDefaultLabel(configServerProperties.getDefaultLabel());
        DeltaFiNativeEnvironmentRepository repository = new DeltaFiNativeEnvironmentRepository(configurableEnvironment,
                nativeEnvironmentProperties, configServerProperties.getDefaultLabel());
        repository.setDefaultLabel(configServerProperties.getDefaultLabel());
        return repository;
    }


    private static PropertyService buildPropertyService(BootstrapContext ctx) {
        GitEnvironmentRepository gitEnvRepo = ctx.getOrElse(GitEnvironmentRepository.class, null);
        DeltaFiNativeEnvironmentRepository nativeEnvRepo = ctx.getOrElse(DeltaFiNativeEnvironmentRepository.class, null);
        PropertyService propertyService = new PropertyService(ctx.get(PropertyRepository.class), ctx.get(PropertyMetadataLoader.class), ctx.get(StateHolderService.class), gitEnvRepo, nativeEnvRepo);
        propertyService.loadUpdatedPropertyMetadata();
        return propertyService;
    }

    private static DefaultPropertyEnvironmentRepository buildDefaultEnvRepo(BootstrapContext ctx) {
        DefaultPropertyEnvironmentRepository defaultPropertyEnvironmentRepository = new DefaultPropertyEnvironmentRepository(ctx.get(PropertyService.class), ctx.get(StateHolderService.class), ctx.get(ConfigServerProperties.class));
        defaultPropertyEnvironmentRepository.loadDefaultProperties();
        return defaultPropertyEnvironmentRepository;
    }

    private static DeltaFiCompositeEnvironmentRepository buildCompositeEnvRepo(BootstrapContext ctx) {
        List<EnvironmentRepository> environmentRepositories = new ArrayList<>();
        environmentRepositories.add(ctx.get(DefaultPropertyEnvironmentRepository.class));
        environmentRepositories.add(ctx.get(MongoEnvironmentRepository.class));

        if (ctx.isRegistered(GitEnvironmentRepository.class)) {
            environmentRepositories.add(ctx.get(GitEnvironmentRepository.class));
        } else {
            environmentRepositories.add(ctx.get(DeltaFiNativeEnvironmentRepository.class));
        }

        return new DeltaFiCompositeEnvironmentRepository(environmentRepositories, ctx.get(ConfigServerProperties.class).isFailOnCompositeError(), ctx.get(StateHolderService.class));
    }

    private static MongoTemplate buildMongoTemplate(BootstrapContext ctx) {
        MongoProperties properties = ctx.get(MongoProperties.class);
        properties.setUuidRepresentation(UuidRepresentation.STANDARD);
        MongoClientSettingsBuilderCustomizer mongoClientSettingsBuilderCustomizer = new MongoPropertiesClientSettingsBuilderCustomizer(properties, null);
        MongoClient mongoClient = new MongoClientFactory(List.of(mongoClientSettingsBuilderCustomizer))
                .createMongoClient(MongoClientSettings.builder().build());
        return new MongoTemplate(mongoClient, properties.getDatabase());
    }

    private static MongoEnvironmentRepository buildMongoEnvRepo(BootstrapContext ctx) {
        return new MongoEnvironmentRepository(ctx.get(PropertyService.class), ctx.get(StateHolderService.class), ctx.get(ConfigServerProperties.class));
    }

    protected static <T> void registerAndPromoteBean(ConfigDataLocationResolverContext context, Class<T> type,
                                              BootstrapRegistry.InstanceSupplier<T> supplier) {
        registerBean(context, type, supplier);
        addBeanToApplicationContext(context, type);
    }

    private static <T> void registerBean(ConfigDataLocationResolverContext context, Class<T> type,
                                    BootstrapRegistry.InstanceSupplier<T> supplier) {
        ConfigurableBootstrapContext bootstrapContext = context.getBootstrapContext();
        bootstrapContext.registerIfAbsent(type, supplier);
    }

    private static <T> void bindAndRegisterProperties(ConfigDataLocationResolverContext context, String propertyPrefix, Class<T> propertiesClass) {
        context.getBootstrapContext().registerIfAbsent(propertiesClass, ignore -> context.getBinder().bindOrCreate(propertyPrefix, propertiesClass));
    }

    private static <T> void addBeanToApplicationContext(ConfigDataLocationResolverContext context, Class<T> type) {
        context.getBootstrapContext().addCloseListener(event -> addBeanToApplicationContext(event, type));
    }

    private static <T> void addBeanToApplicationContext(BootstrapContextClosedEvent event, Class<T> type) {
        String beanName = CONFIG_BEAN_NAME_PREFIX + type.getSimpleName();
        if (!event.getApplicationContext().getBeanFactory().containsBean(beanName)) {
            T instance = event.getBootstrapContext().get(type);
            event.getApplicationContext()
                    .getBeanFactory()
                    .registerSingleton(CONFIG_BEAN_NAME_PREFIX + type.getSimpleName(),
                            instance);
        }
    }

    private static Properties readProperties(ConfigDataLocation location) {
        String paramStr = location.getNonPrefixedValue(PREFIX);

        if (StringUtils.hasText(paramStr)) {
            Properties properties = StringUtils
                    .splitArrayElementsIntoProperties(StringUtils.delimitedListToStringArray(paramStr, "&"), "=");
            if (properties != null) {
                return properties;
            }
        }

        return new Properties();
    }

    @Override
    public int getOrder() {
        return -10;
    }
}
