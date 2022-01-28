package org.deltafi.config.server.environment;

import org.deltafi.config.server.api.domain.PropertySet;
import org.deltafi.config.server.service.StateHolderService;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Create propertySource from the property-metadata.json
 * that contains all the default property values.
 *
 * This source will be the lowest in precedence.
 */
@Component
public class DefaultPropertyEnvironmentRepository extends DeltaFiEnvironmentRepository {

    private final Map<String, PropertySource> appToPropertySet = new HashMap<>();

    public DefaultPropertyEnvironmentRepository(StateHolderService stateHolderService, ConfigServerProperties properties){
        super(stateHolderService, properties);
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
