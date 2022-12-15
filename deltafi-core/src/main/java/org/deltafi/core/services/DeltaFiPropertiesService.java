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
package org.deltafi.core.services;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.Property;
import org.deltafi.common.types.PropertySet;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.repo.DeltaFiPropertiesRepo;
import org.deltafi.core.snapshot.SnapshotRestoreOrder;
import org.deltafi.core.snapshot.Snapshotter;
import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.types.PropertyId;
import org.deltafi.core.types.PropertyType;
import org.deltafi.core.types.PropertyUpdate;
import org.deltafi.core.types.Result;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DeltaFiPropertiesService implements Snapshotter {

    private final DeltaFiPropertiesRepo deltaFiPropertiesRepo;
    private DeltaFiProperties cachedDeltaFiProperties;

    private Map<String, PropertyType> propertyMap;

    public DeltaFiPropertiesService(DeltaFiPropertiesRepo deltaFiPropertiesRepo) {
        this.deltaFiPropertiesRepo = deltaFiPropertiesRepo;
        propertyMap = Arrays.stream(PropertyType.values()).collect(Collectors.toMap(PropertyType::getKey, Function.identity()));
    }

    @PostConstruct
    public void ensurePropertiesExist() {
        if (!deltaFiPropertiesRepo.existsById(DeltaFiProperties.PROPERTY_ID)) {
            cachedDeltaFiProperties = deltaFiPropertiesRepo.save(new DeltaFiProperties());
        } else {
            cachedDeltaFiProperties = getDeltaFiPropertiesFromRepo();
        }
    }

    /**
     * Update the cached properties to the latest from storage
     */
    public void refreshProperties() {
        cachedDeltaFiProperties = getDeltaFiPropertiesFromRepo();
    }

    public DeltaFiProperties getDeltaFiProperties() {
        return cachedDeltaFiProperties;
    }

    private DeltaFiProperties getDeltaFiPropertiesFromRepo() {
        return deltaFiPropertiesRepo.findById(DeltaFiProperties.PROPERTY_ID).orElseThrow();
    }

    /**
     * Get the properties and return them as a list of PropertySets
     * @return
     */
    public List<PropertySet> getPopulatedProperties() {
        PropertySet common = commonPropertySet();

        List<Property> properties = new ArrayList<>();
        for (PropertyType propertyType: PropertyType.values()) {
            properties.add(propertyType.toProperty(cachedDeltaFiProperties));
        }

        common.setProperties(properties);
        return List.of(common);
    }

    /**
     * Update the DeltaFiProperties based on the list of updates
     * @param updates changes to make to the properties
     * @return true if the update was successful
     */
    public boolean updateProperties(List<PropertyUpdate> updates) {
        Map<PropertyType, String> updateMap = new EnumMap<>(PropertyType.class);
        for (PropertyUpdate propertyUpdate : updates) {
            PropertyType propertyType = propertyMap.get(propertyUpdate.getKey());
            if (propertyType != null) {
                updateMap.put(propertyType, propertyUpdate.getValue());
            } else {
                warnInvalidProperty(propertyUpdate.getKey());
            }
        }


        boolean changed = !updateMap.isEmpty() ? deltaFiPropertiesRepo.updateProperties(updateMap) : false;

        refreshProperties();
        return changed;
    }

    /**
     * For each property id reset the value to the default value
     * @param propertyIds list of properties to reset
     * @return true if the update was successful
     */
    public boolean unsetProperties(List<PropertyId> propertyIds) {
        List<PropertyType> propertyTypes = propertyIds.stream().map(this::toPropertyType)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        boolean changed = !propertyTypes.isEmpty() ? deltaFiPropertiesRepo.unsetProperties(propertyTypes) : false;
        refreshProperties();
        return changed;
    }

    @Override
    public void updateSnapshot(SystemSnapshot systemSnapshot) {
        systemSnapshot.setDeltaFiProperties(getDeltaFiProperties());
    }

    @Override
    public Result resetFromSnapshot(SystemSnapshot systemSnapshot, boolean hardReset) {
        if (hardReset) {
            deltaFiPropertiesRepo.save(systemSnapshot.getDeltaFiProperties());
        } else {
            deltaFiPropertiesRepo.save(mergeProperties(systemSnapshot.getDeltaFiProperties()));
        }

        refreshProperties();
        return Result.success();
    }

    @Override
    public int getOrder() {
        return SnapshotRestoreOrder.PROPERTIES_ORDER;
    }

    /**
     * Copy values from the source DeltaFiProperties into the current DeltaFiProperties
     * @param source that hold the property values to copy
     * @return latest properties with the source properties merged in
     */
    private DeltaFiProperties mergeProperties(DeltaFiProperties source) {
        DeltaFiProperties target = getDeltaFiPropertiesFromRepo();

        for (PropertyType propertyType : source.getSetProperties()) {
            propertyType.copyValue(target, source);
            target.getSetProperties().add(propertyType);
        }

        return target;
    }

    private PropertyType toPropertyType(PropertyId propertyId) {
        PropertyType propertyType = propertyMap.get(propertyId.getKey());
        if (propertyType == null) {
            warnInvalidProperty(propertyId.getKey());
        }

        return propertyType;
    }

    private PropertySet commonPropertySet() {
        PropertySet propertySet = new PropertySet();
        propertySet.setId("deltafi-common");
        propertySet.setDisplayName("Common Properties");
        propertySet.setDescription("Properties used across all parts of the system.");
        return propertySet;
    }

    private void warnInvalidProperty(String key) {
        log.warn("Invalid property update received with key of {}", key);
    }
}
