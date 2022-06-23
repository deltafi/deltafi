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
package org.deltafi.core.config.server.environment;

import org.deltafi.core.config.server.constants.PropertyConstants;
import org.deltafi.core.config.server.service.StateHolderService;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public abstract class AbstractDeltaFiEnvironmentRepository implements EnvironmentRepository, Ordered {

    private final StateHolderService stateHolderService;
    private final String label;

    protected AbstractDeltaFiEnvironmentRepository(StateHolderService stateHolderService, ConfigServerProperties properties) {
        this.stateHolderService = stateHolderService;
        this.label = properties.getDefaultLabel();
    }

    @Override
    public Environment findOne(String application, String profile, String labelIn) {
        String[] profiles = parseProfiles(profile);

        Environment environment = new Environment(application, profiles, label, null, stateHolderService.getConfigStateIdString());

        List<String> applications = parseApplication(application);

        // multiple queries to ensure correct PropertySource precedence, typically 2 queries
        for (String app : applications) {
            findPropertySource(app)
                    .ifPresent(environment::addFirst);
        }

        return environment;
    }

    public abstract Optional<PropertySource> findPropertySource(String application);

    protected List<String> parseApplication(String application) {
        if (!StringUtils.hasText(application) || PropertyConstants.APPLICATION.equals(application) || PropertyConstants.CORE_DOMAIN_APP_NAME.equals(application)) {
            return PropertyConstants.DEFAULT_APPLICATIONS;
        }

        if (!PropertyConstants.DEFAULT_APPLICATIONS.contains(application)) {
            return Arrays.asList(StringUtils.commaDelimitedListToStringArray(PropertyConstants.DEFAULT_PROPERTY_SETS + "," + application));
        }

        return List.of(application);
    }

    protected String[] parseProfiles(String profile) {
        return StringUtils.hasText(profile) ?
                StringUtils.commaDelimitedListToStringArray(profile) : new String[]{PropertyConstants.PROFILE};
    }

    protected String defaultIfEmpty(String value) {
        return StringUtils.hasText(value) ? value : PropertyConstants.DEFAULT_PROPERTY_SETS;
    }

}
