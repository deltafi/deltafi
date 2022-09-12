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
package org.deltafi.core.configuration.server.environment;

import org.apache.logging.log4j.util.Strings;
import org.deltafi.core.configuration.server.constants.PropertyConstants;
import org.deltafi.core.configuration.server.service.StateHolderService;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.environment.EnvironmentCleaner;
import org.springframework.cloud.config.server.environment.JGitEnvironmentProperties;
import org.springframework.cloud.config.server.environment.JGitEnvironmentRepository;
import org.springframework.cloud.config.server.environment.NativeEnvironmentProperties;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Objects;
import java.util.Set;

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
        return findOne(Strings.join(applications, ','), PropertyConstants.PROFILE, label);
    }

    @Override
    public synchronized Environment findOne(String application, String profile, String label) {
        return super.findOne(application, PropertyConstants.PROFILE, this.label);
    }

    @Override
    public synchronized Environment findOne(String application, String profile, String label, boolean includeOrigin) {
        DeltaFiNativeEnvironmentRepository delegate = new DeltaFiNativeEnvironmentRepository(getEnvironment(),
                new NativeEnvironmentProperties(), this.label);
        Locations locations = getLocations(application, PropertyConstants.PROFILE, this.label);
        delegate.setSearchLocations(locations.getLocations());
        Environment result = delegate.findOne(application, PropertyConstants.PROFILE, "", includeOrigin);
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