package org.deltafi.config.server.environment;

import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.deltafi.config.server.constants.PropertyConstants;
import org.deltafi.config.server.service.StateHolderService;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.environment.EnvironmentCleaner;
import org.springframework.cloud.config.server.environment.JGitEnvironmentProperties;
import org.springframework.cloud.config.server.environment.JGitEnvironmentRepository;
import org.springframework.cloud.config.server.environment.NativeEnvironmentProperties;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Objects;
import java.util.Set;

import static org.deltafi.config.server.constants.PropertyConstants.PROFILE;

@Slf4j
public class GitEnvironmentRepository extends JGitEnvironmentRepository {

    private final EnvironmentCleaner cleaner = new EnvironmentCleaner();
    private final StateHolderService stateHolderService;
    private final String label;

    private String cachedVersion;

    public GitEnvironmentRepository(ConfigurableEnvironment environment, JGitEnvironmentProperties properties, StateHolderService stateHolderService) {
        super(environment, properties);
        this.stateHolderService = stateHolderService;
        this.label = properties.getDefaultLabel();
    }

    @Override
    public synchronized void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();
        cachedVersion = getLocations(PropertyConstants.DELTAFI_PROPERTY_SET, getDefaultLabel(), label).getVersion();
    }

    public synchronized Environment findOne(Set<String> applications) {
        return findOne(Strings.join(applications, ','), PROFILE, label);
    }

    @Override
    public synchronized Environment findOne(String application, String profile, String label) {
        return super.findOne(application, PROFILE, this.label);
    }

    @Override
    public synchronized Environment findOne(String application, String profile, String label, boolean includeOrigin) {
        DeltaFiNativeEnvironmentRepository delegate = new DeltaFiNativeEnvironmentRepository(getEnvironment(),
                new NativeEnvironmentProperties(), this.label);
        Locations locations = getLocations(application, PROFILE, this.label);
        delegate.setSearchLocations(locations.getLocations());
        Environment result = delegate.findOne(application, PROFILE, "", includeOrigin);
        result.setVersion(locations.getVersion());
        result.setLabel(this.label);
        if (Objects.nonNull(cachedVersion) && !cachedVersion.equals(locations.getVersion())) {
            stateHolderService.updateConfigStateId();
        }

        result.setState(stateHolderService.getConfigStateIdString());
        return this.cleaner.clean(result, getWorkingDirectory().toURI().toString(), getUri());
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 10;
    }
}
