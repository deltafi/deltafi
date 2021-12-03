package org.deltafi.config.server.configuration;

import org.deltafi.config.server.environment.DeltaFiCompositeEnvironmentRepository;
import org.deltafi.config.server.service.StateHolderService;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

@Configuration
public class CompositeEnvironmentConfiguration {

    @Bean
    @Primary
    public DeltaFiCompositeEnvironmentRepository searchPathCompositeEnvironmentRepository(List<EnvironmentRepository> environmentRepos, ConfigServerProperties properties, StateHolderService stateHolderService) {
        return new DeltaFiCompositeEnvironmentRepository(environmentRepos, properties.isFailOnCompositeError(), stateHolderService);
    }
}
