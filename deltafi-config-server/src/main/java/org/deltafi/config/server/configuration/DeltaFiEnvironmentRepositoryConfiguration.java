package org.deltafi.config.server.configuration;

import org.deltafi.config.server.environment.DeltaFiNativeEnvironmentRepository;
import org.deltafi.config.server.environment.factory.DeltaFiNativeEnvironmentRepositoryFactory;
import org.deltafi.config.server.environment.GitEnvironmentRepository;
import org.deltafi.config.server.environment.factory.GitEnvironmentRepositoryFactory;
import org.deltafi.config.server.service.StateHolderService;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.cloud.config.server.environment.ConfigurableHttpConnectionFactory;
import org.springframework.cloud.config.server.environment.HttpClientConfigurableHttpConnectionFactory;
import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentProperties;
import org.springframework.cloud.config.server.environment.NativeEnvironmentProperties;
import org.springframework.cloud.config.server.support.GoogleCloudSourceSupport;
import org.springframework.cloud.config.server.support.TransportConfigCallbackFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Optional;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ NativeEnvironmentProperties.class})
@Import({ DeltaFiNativeRepositoryConfiguration.class})
public class DeltaFiEnvironmentRepositoryConfiguration {

    @Bean
    public MultipleJGitEnvironmentProperties multipleJGitEnvironmentProperties() {
        return new MultipleJGitEnvironmentProperties();
    }

    @Configuration(proxyBeanMethods = false)
    static class JGitFactoryConfig {

        @Bean
        public GitEnvironmentRepositoryFactory gitEnvironmentRepositoryFactory(
                ConfigurableEnvironment environment,
                Optional<ConfigurableHttpConnectionFactory> jgitHttpConnectionFactory,
                Optional<TransportConfigCallback> customTransportConfigCallback,
                Optional<GoogleCloudSourceSupport> googleCloudSourceSupport,
                StateHolderService stateHolderService,
                ConfigServerProperties properties) {
            final TransportConfigCallbackFactory transportConfigCallbackFactory = new TransportConfigCallbackFactory(
                    customTransportConfigCallback.orElse(null), googleCloudSourceSupport.orElse(null));
            return new GitEnvironmentRepositoryFactory(environment,  jgitHttpConnectionFactory, transportConfigCallbackFactory,
                    stateHolderService, properties.getDefaultLabel());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class JGitHttpClientConfig {

        @Bean
        public ConfigurableHttpConnectionFactory httpClientConnectionFactory() {
            return new HttpClientConfigurableHttpConnectionFactory();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DeltaFiNativeFactoryConfig {

        @Bean
        public DeltaFiNativeEnvironmentRepositoryFactory deltaFiNativeEnvironmentRepositoryFactory(
                ConfigurableEnvironment environment, ConfigServerProperties properties) {
            return new DeltaFiNativeEnvironmentRepositoryFactory(environment, properties.getDefaultLabel());
        }
    }
}

@Configuration(proxyBeanMethods = false)
@Profile("git")
class GitRepositoryConfiguration {
    @Bean
    public GitEnvironmentRepository gitEnvironmentRepository(
            GitEnvironmentRepositoryFactory gitEnvironmentRepositoryFactory,
            MultipleJGitEnvironmentProperties environmentProperties, ConfigServerProperties props) throws Exception {
        environmentProperties.setDefaultLabel(props.getDefaultLabel());
        return gitEnvironmentRepositoryFactory.build(environmentProperties);
    }
}

@Configuration(proxyBeanMethods = false)
@Profile("!git")
class DeltaFiNativeRepositoryConfiguration {
    @Bean
    public DeltaFiNativeEnvironmentRepository nativeEnvironmentRepository(DeltaFiNativeEnvironmentRepositoryFactory factory,
                                                                          NativeEnvironmentProperties environmentProperties,
                                                                          ConfigServerProperties props) {
        environmentProperties.setDefaultLabel(props.getDefaultLabel());
        return factory.build(environmentProperties);
    }
}

