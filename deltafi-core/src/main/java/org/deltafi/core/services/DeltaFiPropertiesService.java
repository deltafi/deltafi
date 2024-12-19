/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.deltafi.common.types.KeyValue;
import org.deltafi.core.types.Property;
import org.deltafi.core.types.PropertySet;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.configuration.PropertyInfo;
import org.deltafi.core.repo.DeltaFiPropertiesRepo;
import org.deltafi.core.types.snapshot.SnapshotRestoreOrder;
import org.deltafi.core.types.Result;
import org.deltafi.core.types.snapshot.Snapshot;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.*;

@Slf4j
@Service
public class DeltaFiPropertiesService implements Snapshotter {

    private final DeltaFiPropertiesRepo deltaFiPropertiesRepo;
    private DeltaFiProperties cachedDeltaFiProperties;
    private List<Property> properties;
    private static final Set<String> allowedProperties;
    private static final List<Property> defaultProperties;

    static {
        allowedProperties = new HashSet<>();
        defaultProperties = new ArrayList<>();
        for (Field field : DeltaFiProperties.class.getDeclaredFields()) {
            PropertyInfo propertyInfo = field.getAnnotation(PropertyInfo.class);
            if (propertyInfo != null) {
                allowedProperties.add(field.getName());
                String value = !PropertyInfo.NULL.equals(propertyInfo.defaultValue()) ? propertyInfo.defaultValue() : null;

                defaultProperties.add(Property.builder().key(field.getName()).defaultValue(value)
                        .description(propertyInfo.description()).refreshable(propertyInfo.refreshable()).build());
            }
        }
    }

    public DeltaFiPropertiesService(DeltaFiPropertiesRepo deltaFiPropertiesRepo) {
        this.deltaFiPropertiesRepo = deltaFiPropertiesRepo;

        // make sure the latest DeltaFiProperties structure is reflected in the properties collection
        upsertProperties();
        refreshProperties();
    }

    /**
     * Upsert the set of properties for this version of DeltaFi. Prune obsolete properties.
     */
    public void upsertProperties() {
        String[] keys = new String[defaultProperties.size()];
        String[] defaultValues = new String[defaultProperties.size()];
        String[] descriptions = new String[defaultProperties.size()];
        Boolean[] refreshables = new Boolean[defaultProperties.size()];

        for (int i = 0; i < defaultProperties.size(); i++) {
            Property prop = defaultProperties.get(i);
            keys[i] = prop.getKey();
            defaultValues[i] = prop.getDefaultValue();
            descriptions[i] = prop.getDescription();
            refreshables[i] = prop.isRefreshable();
        }

        deltaFiPropertiesRepo.batchUpsertAndDeleteProperties(
                keys, defaultValues, descriptions, refreshables, allowedProperties
        );
    }

    /**
     * Update the cached properties to the latest from storage
     * Bind the latest properties DeltaFiProperties and cach
     * the result
     */
    public void refreshProperties() {
        this.properties = deltaFiPropertiesRepo.findAll();
        Map<String, String> propertiesMap = new HashMap<>();

        // use forEach instead stream collect to avoid NPE with null values
        this.properties.forEach(p -> propertiesMap.put(p.getKey(), p.getValue()));

        Binder bind = new Binder(new MapConfigurationPropertySource(propertiesMap));
        cachedDeltaFiProperties = bind.bindOrCreate("", DeltaFiProperties.class);
    }

    public DeltaFiProperties getDeltaFiProperties(Boolean skipCache) {
        if (Boolean.TRUE.equals(skipCache)) {
            refreshProperties();
        }
        return cachedDeltaFiProperties;
    }

    public DeltaFiProperties getDeltaFiProperties() {
        return cachedDeltaFiProperties;
    }

    /**
     * Get the properties and return them as a list of PropertySets
     * @return the list of PropertySets
     */
    public List<PropertySet> getPopulatedProperties() {
        PropertySet common = commonPropertySet();
        common.setProperties(this.properties);
        return List.of(common);
    }

    /**
     * Update the DeltaFiProperties based on the list of updates
     * @param updates changes to make to the properties
     * @return true if the update was successful
     */
    public boolean updateProperties(List<KeyValue> updates) {
        List<KeyValue> allowedUpdates = updates.stream().filter(this::isValid).toList();
        boolean changed = false;
        for (KeyValue keyValue : allowedUpdates) {
            changed = (deltaFiPropertiesRepo.updateProperty(keyValue.getKey(), keyValue.getValue()) > 0) || changed;
        }
        return refresh(changed);
    }

    /**
     * For each property id reset the value to the default value
     * @param propertyNames list of properties to reset
     * @return true if the update was successful
     */
    public boolean unsetProperties(List<String> propertyNames) {
        List<String> toUnset = propertyNames.stream().filter(allowedProperties::contains).toList();
        return refresh(!toUnset.isEmpty() && deltaFiPropertiesRepo.unsetProperties(toUnset) > 0);
    }

    @Override
    public void updateSnapshot(Snapshot snapshot) {
        List<KeyValue> propertiesSnapshot = deltaFiPropertiesRepo.findAll().stream()
                .map(this::keyValue).filter(Objects::nonNull).toList();

        snapshot.setDeltaFiProperties(propertiesSnapshot);
    }

    private KeyValue keyValue(Property property) {
        return property.hasValue() ? new KeyValue(property.getKey(), property.getCustomValue()) : null;
    }

    @Override
    public Result resetFromSnapshot(Snapshot snapshot, boolean hardReset) {
        if (hardReset) {
            unsetProperties(new ArrayList<>(allowedProperties));
        }

        List<KeyValue> snapshotProperties = snapshot.getDeltaFiProperties();
        if (snapshotProperties != null) {
            updateProperties(snapshotProperties);
        }

        refreshProperties();
        return Result.successResult();
    }

    private boolean refresh(boolean changed) {
        if (changed) {
            refreshProperties();
        }

        return changed;
    }

    @Override
    public int getOrder() {
        return SnapshotRestoreOrder.PROPERTIES_ORDER;
    }

    private PropertySet commonPropertySet() {
        PropertySet propertySet = new PropertySet();
        propertySet.setId("deltafi-common");
        propertySet.setDisplayName("Common Properties");
        propertySet.setDescription("Properties used across all parts of the system.");
        return propertySet;
    }

    private boolean isValid(KeyValue update) {
        String key = update.getKey();
        String value = update.getValue();

        if (!allowedProperties.contains(key)) {
            log.warn("Unrecognized property update received with a key of {}", key);
            return false;
        }

        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(key, value)));
        try {
            return binder.bind("", DeltaFiProperties.class).get() != null;
        } catch (Exception e) {
            Throwable root = ExceptionUtils.getRootCause(e);
            String message = root != null ? root.getMessage() : e.getMessage();
            log.error("Invalid property {}:{} failed with message - {}", key, value, message);
        }

        return false;
    }
}
