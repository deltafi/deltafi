package org.deltafi.config.server.environment;

import org.deltafi.config.server.service.StateHolderService;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.environment.SearchPathCompositeEnvironmentRepository;

import java.util.List;

public class DeltaFiCompositeEnvironmentRepository extends SearchPathCompositeEnvironmentRepository {

    private final StateHolderService stateHolderService;

    public DeltaFiCompositeEnvironmentRepository(List<EnvironmentRepository> environmentRepositories,
                                                 boolean failOnError, StateHolderService stateHolderService) {
        super(environmentRepositories, failOnError);
        this.stateHolderService = stateHolderService;
    }

    @Override
    public Environment findOne(String application, String profile, String label, boolean includeOrigin) {
        Environment env = super.findOne(application, profile, label, includeOrigin);
        env.setState(stateHolderService.getConfigStateIdString());
        return env;
    }
}
