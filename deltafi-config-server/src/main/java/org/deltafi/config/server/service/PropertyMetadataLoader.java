package org.deltafi.config.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.config.server.api.domain.Property;
import org.deltafi.config.server.api.domain.PropertySet;
import org.deltafi.config.server.repo.PropertyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * At startup read the latest property-metadata.json from the classpath into a
 * list of PropertySets. Grab existing PropertySets from mongo and port any
 * values that are overridden there into the new PropertySets.
 * Replace the existing PropertySets in mongo with the latest PropertySets.
 */
@Slf4j
@Service
public class PropertyMetadataLoader {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final PropertyRepository repo;

    @Value("classpath:property-metadata.json")
    private Resource propertyMetadata;

    public PropertyMetadataLoader(PropertyRepository repo) {
        this.repo = repo;
    }

    @PostConstruct
    public void loadPropertyMetadata()  {
        if (isPropertyMetadataReadable()) {
            try {
                List<PropertySet> updatedPropertyMetadata = OBJECT_MAPPER.readValue(propertyMetadata.getInputStream(), new TypeReference<>() {});
                List<PropertySet> currentPropertyMetadata = repo.findAll();

                keepOverriddenValues(updatedPropertyMetadata, currentPropertyMetadata);
                repo.saveAll(updatedPropertyMetadata);
                log.debug("Loaded propertyMetadata");
            } catch (IOException e) {
                log.error("Failed to load propertyMetadata sets", e);
            }
        }
    }

    void keepOverriddenValues(List<PropertySet> updated, List<PropertySet> current) {
        updated.forEach(propertySet -> keepOverriddenValues(propertySet, current));

    }

    /**
     * Find the existing PropertySet from the currentPropertySets list with the same id
     * as the incomingPropertySet. If existing properties are found copy any values
     * that are set into the incoming properties to preserve overridden values.
     *
     * @param incomingPropertySet - propertySet based on the property-metadata.json
     * @param currentPropertySets - list of propertySets stored in mongo
     */
    private void keepOverriddenValues(PropertySet incomingPropertySet, List<PropertySet> currentPropertySets) {
        Set<Property> existingProperties = currentPropertySets.stream()
                .filter(currentPropertySet -> currentPropertySet.getId().equals(incomingPropertySet.getId()))
                .findFirst().map(PropertySet::getProperties)
                .orElse(Collections.emptySet());

        // don't loop through the incoming properties if there are no existing values to port
        if(!existingProperties.isEmpty()) {
            incomingPropertySet.getProperties().forEach(property -> keepOverriddenValues(property, existingProperties));
        }

    }

    /**
     * Find the value existing property value and set it on the incoming property.
     *
     * @param property - property that needs to get the value set if it was set in the existing PropertySet
     * @param existingProperties - properties stored in Mongo that may have user updated values
     */
    private void keepOverriddenValues(Property property, Set<Property> existingProperties) {
        existingProperties.stream()
                .filter(existingProperty -> existingProperty.getKey().equals(property.getKey()))
                .findFirst()
                .map(Property::getValue)
                .ifPresent(property::setValue);
    }

    /**
     * Check that the propertyMetadata exists and can be read
     * @return
     */
    private boolean isPropertyMetadataReadable() {
        return Objects.nonNull(propertyMetadata) && propertyMetadata.exists() && propertyMetadata.isReadable();
    }

}
