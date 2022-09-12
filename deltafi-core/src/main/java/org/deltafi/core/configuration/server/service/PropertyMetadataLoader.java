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
package org.deltafi.core.configuration.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.Property;
import org.deltafi.common.types.PropertySet;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * At startup read the latest property-metadata.json from the classpath into a
 * list of PropertySets. Grab existing PropertySets from mongo and port any
 * values that are overridden there into the new PropertySets.
 * Replace the existing PropertySets in mongo with the latest PropertySets.
 */
@Slf4j
public class PropertyMetadataLoader {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Resource propertyMetadata = new ClassPathResource("property-metadata.json");

    public List<PropertySet> loadPropertyMetadata(List<PropertySet> currentPropertyMetadata)  {
        if (isPropertyMetadataReadable()) {
            try {
                List<PropertySet> updatedPropertyMetadata = OBJECT_MAPPER.readValue(propertyMetadata.getInputStream(), new TypeReference<>() {});

                keepOverriddenValues(updatedPropertyMetadata, currentPropertyMetadata);
                return updatedPropertyMetadata;
            } catch (IOException e) {
                log.error("Failed to load propertyMetadata sets", e);
            }
        }
        return currentPropertyMetadata;
    }

    /**
     * Take any custom values found in the propertySetsToPort and set them in the
     * propertySetsToUpdate if the property is found
     * @param propertySetsToUpdate propertySets that need the values set
     * @param propertySetsToPort propertySets that contain values to use
     */
    public void keepOverriddenValues(List<PropertySet> propertySetsToUpdate, List<PropertySet> propertySetsToPort) {
        propertySetsToUpdate.forEach(propertySetToUpdate -> keepOverriddenValues(propertySetToUpdate, propertySetsToPort));
    }

    /**
     * Find the existing PropertySet from the currentPropertySets list with the same id
     * as the incomingPropertySet. If existing properties are found copy any values
     * that are set into the incoming properties to preserve overridden values.
     *
     * @param propertySetsToUpdate - propertySet that need the values set
     * @param propertySetsToPort - propertySets that contain values to use
     */
    private void keepOverriddenValues(PropertySet propertySetsToUpdate, List<PropertySet> propertySetsToPort) {
        List<Property> existingProperties = propertySetsToPort.stream()
                .filter(propertySetToPort -> propertySetToPort.getId().equals(propertySetsToUpdate.getId()))
                .findFirst().map(PropertySet::getProperties)
                .orElse(Collections.emptyList());

        // don't loop through the incoming properties if there are no existing values to port
        if(!existingProperties.isEmpty()) {
            propertySetsToUpdate.getProperties().forEach(property -> keepOverriddenValues(property, existingProperties));
        }

    }

    /**
     * Find the value existing property value and set it on the incoming property.
     *
     * @param property - property that needs to get the value set if it was set in the existing PropertySet
     * @param existingProperties - properties stored in Mongo that may have user updated values
     */
    private void keepOverriddenValues(Property property, List<Property> existingProperties) {
        existingProperties.stream()
                .filter(existingProperty -> existingProperty.getKey().equals(property.getKey()))
                .findFirst()
                .map(Property::getValue)
                .ifPresent(property::setValue);
    }

    /**
     * Check that the propertyMetadata exists and can be read
     * @return - true if the propertyMetadata file can be read
     */
    private boolean isPropertyMetadataReadable() {
        return Objects.nonNull(propertyMetadata) && propertyMetadata.exists() && propertyMetadata.isReadable();
    }

}
