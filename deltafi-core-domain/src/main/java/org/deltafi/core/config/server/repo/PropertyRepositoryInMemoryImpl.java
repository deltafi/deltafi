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
package org.deltafi.core.config.server.repo;

import org.deltafi.core.domain.api.types.Property;
import org.deltafi.core.domain.api.types.PropertyId;
import org.deltafi.core.domain.api.types.PropertySet;
import org.deltafi.core.domain.api.types.PropertyUpdate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Property set storage, stored in memory
 */
public class PropertyRepositoryInMemoryImpl implements PropertyRepository {

    private static final PropertySet EMPTY_PROPERTY_SET = new PropertySet();
    Map<String, PropertySet> propertySets = new ConcurrentHashMap<>();

    @Override
    public PropertySet save(PropertySet propertySet) {
        propertySets.put(propertySet.getId(), propertySet);
        return propertySet;
    }

    @Override
    public List<PropertySet> saveAll(Collection<PropertySet> propertySets) {
        if (null == propertySets) {
            return List.of();
        }

        return propertySets.stream().map(this::save).collect(Collectors.toList());
    }

    @Override
    public Optional<PropertySet> findById(String id) {
        return Optional.ofNullable(propertySets.get(id));
    }

    @Override
    public List<PropertySet> findAll() {
        return new ArrayList<>(propertySets.values());
    }

    @Override
    public boolean removeById(String id) {
        return null != propertySets.remove(id);
    }

    @Override
    public void removeAll() {
        propertySets.clear();
    }

    @Override
    public Set<String> getIds() {
        return propertySets.keySet();
    }

    @Override
    public int updateProperties(List<PropertyUpdate> updates) {
        int numUpdates = 0;
        for (PropertyUpdate propertyUpdate : updates) {
            PropertySet propertySet = propertySets.getOrDefault(propertyUpdate.getPropertySetId(), EMPTY_PROPERTY_SET);
            numUpdates += propertySet.getProperties().stream()
                    .mapToInt(property -> updateProperty(propertyUpdate, property))
                    .sum();
        }
        return numUpdates;
    }

    @Override
    public int unsetProperties(List<PropertyId> propertyIds) {
        int numUpdates = 0;
        for (PropertyId propertyId : propertyIds) {
            PropertySet propertySet = propertySets.getOrDefault(propertyId.getPropertySetId(), EMPTY_PROPERTY_SET);
            numUpdates += propertySet.getProperties().stream()
                    .mapToInt(property -> unsetProperty(propertyId, property))
                    .sum();
        }
        return numUpdates;
    }

    private int updateProperty(PropertyUpdate update, Property property) {
        if (keyMatchesAndEditable(update.getKey(), property)) {
            property.setValue(update.getValue());
            return 1;
        }
        return 0;
    }

    private int unsetProperty(PropertyId propertyId, Property property) {
        if (keyMatchesAndEditable(propertyId.getKey(), property) && null != property.getValue()) {
            property.setValue(null);
            return 1;
        }
        return 0;
    }

    private boolean keyMatchesAndEditable(String key, Property property) {
        return property.isEditable() && key.equals(property.getKey());
    }
}
