package org.deltafi.config.server.environment.factory;

import org.deltafi.config.server.environment.GitEnvironmentRepository;
import org.deltafi.config.server.service.StateHolderService;
import org.eclipse.jgit.transport.HttpTransport;
import org.springframework.cloud.config.server.environment.ConfigurableHttpConnectionFactory;
import org.springframework.cloud.config.server.environment.EnvironmentRepositoryFactory;
import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentProperties;
import org.springframework.cloud.config.server.support.TransportConfigCallbackFactory;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Optional;

public class GitEnvironmentRepositoryFactory
        implements EnvironmentRepositoryFactory<GitEnvironmentRepository, MultipleJGitEnvironmentProperties> {

    private final ConfigurableEnvironment environment;
    private final Optional<ConfigurableHttpConnectionFactory> connectionFactory;
    private final TransportConfigCallbackFactory transportConfigCallbackFactory;
    private final StateHolderService stateHolderService;
    private final String label;


    public GitEnvironmentRepositoryFactory(ConfigurableEnvironment environment,
                                           Optional<ConfigurableHttpConnectionFactory> connectionFactory,
                                           TransportConfigCallbackFactory transportConfigCallbackFactory,
                                           StateHolderService stateHolderService, String label) {
        this.environment = environment;
        this.connectionFactory = connectionFactory;
        this.transportConfigCallbackFactory = transportConfigCallbackFactory;
        this.stateHolderService = stateHolderService;
        this.label = label;
    }

    @Override
    public GitEnvironmentRepository build(MultipleJGitEnvironmentProperties environmentProperties)
            throws Exception {
        if (this.connectionFactory.isPresent()) {
            HttpTransport.setConnectionFactory(this.connectionFactory.get());
            this.connectionFactory.get().addConfiguration(environmentProperties);
        }

        GitEnvironmentRepository repository = new GitEnvironmentRepository(this.environment,
                environmentProperties, stateHolderService);
        repository.setTransportConfigCallback(transportConfigCallbackFactory.build(environmentProperties));
        repository.setDefaultLabel(this.label);
        return repository;
    }
}
