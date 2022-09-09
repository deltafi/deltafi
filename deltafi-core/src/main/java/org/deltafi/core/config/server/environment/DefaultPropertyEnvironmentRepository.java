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

import org.deltafi.common.types.PropertySet;
import org.deltafi.core.config.server.service.PropertyService;
import org.deltafi.core.config.server.service.StateHolderService;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.core.Ordered;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 * Create a propertySource from the property-metadata.json
 * that contains all the default property values.
 *
 * This source will be the lowest in precedence.
 */
public class DefaultPropertyEnvironmentRepository extends AbstractDeltaFiEnvironmentRepository {

    private final Map<String, PropertySource> appToPropertySet = new HashMap<>();
    private final PropertyService propertyService;

    public DefaultPropertyEnvironmentRepository(PropertyService propertyService, StateHolderService stateHolderService, ConfigServerProperties properties){
        super(stateHolderService, properties);
        this.propertyService = propertyService;
    }

    @PostConstruct
    public void loadDefaultProperties() {
        loadFromPropertyMetadata(propertyService.getAll());
    }

    public void loadFromPropertyMetadata(List<PropertySet> propertySets) {
        if (Objects.nonNull(propertySets)) {
            propertySets.forEach(this::addDefaultPropertySource);
        }
    }

    private void addDefaultPropertySource(PropertySet propertySet) {
        appToPropertySet.put(propertySet.getId(), toPropertySource(propertySet));
    }

    private PropertySource toPropertySource(PropertySet props) {
        return new PropertySource(props.getId() + "  defaults-values", getDefaultPropertiesAsMap(props));
    }

    public Map<String, String> getDefaultPropertiesAsMap(PropertySet propertySet) {
        Map<String, String> props = new HashMap<>();
        if (Objects.nonNull(propertySet)) {
            propertySet.getProperties().stream().filter(property -> Objects.nonNull(property.getDefaultValue()))
                    .forEach(property -> props.put(property.getKey(), property.getDefaultValue()));
        }
        return props;
    }

    @Override
    public Optional<PropertySource> findPropertySource(String application) {
        return Optional.ofNullable(appToPropertySet.get(application));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
