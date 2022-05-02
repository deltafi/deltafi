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
package org.deltafi.config.server.environment;

import org.deltafi.config.server.api.domain.PropertySet;
import org.deltafi.config.server.service.PropertyService;
import org.deltafi.config.server.service.StateHolderService;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Component
public class MongoEnvironmentRepository extends DeltaFiEnvironmentRepository {

    private final PropertyService propertiesService;

    public MongoEnvironmentRepository(PropertyService propertiesService, StateHolderService stateHolderService, ConfigServerProperties properties) {
        super(stateHolderService, properties);
        this.propertiesService = propertiesService;
    }

    @Override
    public Optional<PropertySource> findPropertySource(String app) {
        return propertiesService.findById(app)
                .map(propertySet -> this.toPropertySource(app, propertySet));
    }

    private PropertySource toPropertySource(String app, PropertySet props) {
        return new PropertySource(app + "  mongo-values", getPropertiesAsMap(props));
    }

    private Map<String, String> getPropertiesAsMap(PropertySet propertySet) {
        Map<String, String> props = new HashMap<>();
        if (Objects.nonNull(propertySet)) {
            propertySet.getProperties().stream().filter(property -> Objects.nonNull(property.getValue()))
                    .forEach(property -> props.put(property.getKey(), property.getValue()));
        }
        return props;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

}
