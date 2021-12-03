package org.deltafi.config.server.environment.factory;

import org.deltafi.config.server.environment.DeltaFiNativeEnvironmentRepository;
import org.springframework.cloud.config.server.environment.EnvironmentRepositoryFactory;
import org.springframework.cloud.config.server.environment.NativeEnvironmentProperties;
import org.springframework.core.env.ConfigurableEnvironment;

public class DeltaFiNativeEnvironmentRepositoryFactory implements EnvironmentRepositoryFactory<DeltaFiNativeEnvironmentRepository, NativeEnvironmentProperties> {

    private final ConfigurableEnvironment environment;
    private final String label;

    public DeltaFiNativeEnvironmentRepositoryFactory(ConfigurableEnvironment environment, String label) {
        this.environment = environment;
        this.label = label;
    }

    @Override
    public DeltaFiNativeEnvironmentRepository build(NativeEnvironmentProperties environmentProperties) {
        DeltaFiNativeEnvironmentRepository repository = new DeltaFiNativeEnvironmentRepository(this.environment,
                environmentProperties, label);
        repository.setDefaultLabel(label);
        return repository;
    }

}
