/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.core.configuration.LocalStorageProperties;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.configuration.PropertyGroup;
import org.deltafi.core.configuration.PropertyInfo;
import org.deltafi.core.repo.DeltaFiPropertiesRepo;
import org.deltafi.core.types.Property;
import org.deltafi.core.types.PropertySet;
import org.deltafi.core.types.Result;
import org.deltafi.core.types.snapshot.Snapshot;
import org.deltafi.core.types.snapshot.SnapshotRestoreOrder;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.*;

@Slf4j
@Service
public class DeltaFiPropertiesService implements Snapshotter {

    private static final String AGE_OFF_META_ONLY = "Number of days that a DeltaFile metadata should live, any records older will be removed. " +
            "The externally stored content will not be removed by this setting.";
    private static final String DISABLED_DESCRIPTION = "Disabled due to using external storage.  ";
    private static final Set<String> DISABLED_FOR_EXTERNAL_STORAGE = Set.of("diskSpacePercentThreshold", "ingressDiskSpaceRequirementInMb",
            "checkContentStoragePercentThreshold", "checkDeleteLagErrorThreshold", "checkDeleteLagWarningThreshold");

    private static final Set<String> allowedProperties;
    private static final List<Property> defaultProperties;
    private static final Map<String, PropertyGroup> propertyToGroup;

    static {
        allowedProperties = new HashSet<>();
        defaultProperties = new ArrayList<>();
        propertyToGroup = new HashMap<>();
        for (Field field : DeltaFiProperties.class.getDeclaredFields()) {
            PropertyInfo propertyInfo = field.getAnnotation(PropertyInfo.class);
            if (propertyInfo != null) {
                allowedProperties.add(field.getName());
                String value = !PropertyInfo.NULL.equals(propertyInfo.defaultValue()) ? propertyInfo.defaultValue() : null;
                propertyToGroup.put(field.getName(), propertyInfo.group());

                defaultProperties.add(Property.builder().key(field.getName()).defaultValue(value)
                        .description(propertyInfo.description()).refreshable(propertyInfo.refreshable())
                        .editable(true).dataType(propertyInfo.dataType()).build());
            }
        }
    }

    private final DeltaFiPropertiesRepo deltaFiPropertiesRepo;
    private final LocalStorageProperties localStorageProperties;
    private final Set<String> disabledProperties;
    private DeltaFiProperties cachedDeltaFiProperties;
    private List<Property> properties;

    public DeltaFiPropertiesService(DeltaFiPropertiesRepo deltaFiPropertiesRepo, LocalStorageProperties localStorageProperties) {
        this.deltaFiPropertiesRepo = deltaFiPropertiesRepo;
        this.localStorageProperties = localStorageProperties;
        this.disabledProperties = new HashSet<>();
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
        String[] dataTypes = new String[defaultProperties.size()];
        Boolean[] editables = new Boolean[defaultProperties.size()];

        for (int i = 0; i < defaultProperties.size(); i++) {
            Property prop = defaultProperties.get(i);
            keys[i] = prop.getKey();

            if (isExternalContent()) {
                modifyForExternalStorage(prop);
            }
            defaultValues[i] = prop.getDefaultValue();
            descriptions[i] = prop.getDescription();
            refreshables[i] = prop.isRefreshable();
            dataTypes[i] = prop.getDataType().name();
            editables[i] = prop.isEditable();

            if (!prop.isEditable()) {
                disabledProperties.add(prop.getKey());
            }
        }

        deltaFiPropertiesRepo.batchUpsertAndDeleteProperties(
                keys, defaultValues, descriptions, refreshables, editables, dataTypes, allowedProperties
        );
    }

    private void modifyForExternalStorage(Property property) {
        String key = property.getKey();
        if (DISABLED_FOR_EXTERNAL_STORAGE.contains(key)) {
            property.setEditable(false);
            property.setDescription(DISABLED_DESCRIPTION + property.getDescription());
        } else if (key.equals("ageOffDays")) {
            property.setDescription(AGE_OFF_META_ONLY);
        }
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
     *
     * @return the list of PropertySets
     */
    public List<PropertySet> getPopulatedProperties(Boolean splitByGroup) {
        if (splitByGroup != null && splitByGroup) {
            List<PropertySet> result = new ArrayList<>();
            for (PropertyGroup group : PropertyGroup.values()) {
                PropertySet groupSet = groupPropertySet(group);
                groupSet.setProperties(this.properties.stream()
                        .filter(p -> propertyToGroup.get(p.getKey()).equals(group))
                        .toList());
                result.add(groupSet);
            }
            return result;
        }
        PropertySet common = commonPropertySet();
        common.setProperties(this.properties);
        return List.of(common);
    }

    /**
     * Update the DeltaFiProperties based on the list of updates
     *
     * @param updates changes to make to the properties
     * @return true if the update was successful
     */
    public boolean updateProperties(List<KeyValue> updates) {
        return updateProperties(updates, false);
    }

    /**
     * Update the DeltaFiProperties based on the list of updates
     * @param updates changes to make to the properties
     * @param fromSnapshot true if these updates are being applied from a snapshot
     * @return true if the update was successful
     */
     boolean updateProperties(List<KeyValue> updates, boolean fromSnapshot) {
        List<KeyValue> allowedUpdates = updates.stream()
                .filter(keyValue -> isValid(keyValue, fromSnapshot))
                .toList();
        boolean changed = false;
        for (KeyValue keyValue : allowedUpdates) {
            changed = (deltaFiPropertiesRepo.updateProperty(keyValue.getKey(), keyValue.getValue()) > 0) || changed;
        }
        return refresh(changed);
    }

    /**
     * For each property id reset the value to the default value
     *
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
            updateProperties(snapshotProperties, true);
        }

        refreshProperties();
        return Result.successResult();
    }

    public boolean isExternalContent() {
        return !this.localStorageProperties.content();
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

    private PropertySet groupPropertySet(PropertyGroup group) {
        PropertySet propertySet = new PropertySet();
        propertySet.setId("deltafi-common-" + group.getName());
        propertySet.setDisplayName("Core - " + group.getLabel());
        if (group.getDescription().isEmpty()) {
            propertySet.setDescription("Properties used for " + group.getName());
        } else {
            propertySet.setDescription(group.getDescription());
        }
        return propertySet;
    }

    private boolean isValid(KeyValue update, boolean fromSnapshot) {
        String key = update.getKey();
        String value = update.getValue();

        if (!allowedProperties.contains(key)) {
            log.warn("Unrecognized property update received with a key of {}", key);
            return false;
        }

        if (disabledProperties.contains(key)) {
            if (fromSnapshot) {
                log.warn("Property with a key of {} cannot be applied from the snapshot because the property is disabled", key);
                return false;
            } else {
                throw new IllegalArgumentException("Property " + key + " is disabled and cannot be changed");
            }
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
