package org.deltafi.config.server.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.config.server.config.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConfigServerProperties.class)
@Import({ DeltaFiEnvironmentRepositoryConfiguration.class, CompositeEnvironmentConfiguration.class, ResourceRepositoryConfiguration.class,
        ConfigServerEncryptionConfiguration.class, ConfigServerMvcConfiguration.class,
        ResourceEncryptorConfiguration.class })
public class ConfigServerConfiguration {
}
