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

import org.deltafi.common.types.Property;
import org.deltafi.common.types.PropertySet;
import org.deltafi.core.configuration.server.environment.DeltaFiNativeEnvironmentRepository;
import org.deltafi.core.configuration.server.environment.GitEnvironmentRepository;
import org.deltafi.core.configuration.server.repo.PropertyRepository;
import org.deltafi.core.snapshot.SnapshotRestoreOrder;
import org.deltafi.core.snapshot.Snapshotter;
import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.types.PropertyId;
import org.deltafi.core.types.PropertyUpdate;
import org.deltafi.core.types.Result;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.context.refresh.ContextRefresher;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.deltafi.common.types.PropertySource.*;

public class PropertyService implements Snapshotter {

    private static final PropertySource EMPTY_SOURCE = new PropertySource("empty", new HashMap<>());
    private static final Environment EMPTY_ENV = new Environment("empty");

    private final PropertyRepository repo;
    private final StateHolderService stateHolderService;
    private final GitEnvironmentRepository gitEnvironmentRepository;
    private final DeltaFiNativeEnvironmentRepository nativeEnvironmentRepository;
    private final PropertyMetadataLoader propertyMetadataLoader;
    private Map<String, PropertySet> propertyCache = new HashMap<>();
    private ContextRefresher refresher;

    public PropertyService(PropertyRepository repo, PropertyMetadataLoader propertyMetadataLoader, StateHolderService stateHolderService, GitEnvironmentRepository gitEnvironmentRepository,
                           DeltaFiNativeEnvironmentRepository nativeEnvironmentRepository) {
        this.repo = repo;
        this.propertyMetadataLoader = propertyMetadataLoader;
        this.stateHolderService = stateHolderService;
        this.gitEnvironmentRepository = gitEnvironmentRepository;
        this.nativeEnvironmentRepository = nativeEnvironmentRepository;
    }

    public void setContextRefresher(ContextRefresher contextRefresher) {
        this.refresher = contextRefresher;
    }

    /**
     * Get the existing PropertySets from storage and merge them with the
     * new properties in the property-metadata.json
     */
    @PostConstruct
    public void loadUpdatedPropertyMetadata() {
        List<PropertySet> mergedProperties = propertyMetadataLoader.loadPropertyMetadata(repo.findAll());
        repo.saveAll(mergedProperties);
        reloadCacheOnly();
    }

    /**
     * Check if another instance of deltafi-core has updated the properties.
     * If so reload the cache and trigger a context refresh
     */
    public void syncProperties() {
        if (stateHolderService.needsSynced()) {
            reloadCacheAndRefreshContext();
        }
    }

    /**
     * Retrieve all the latest stored PropertySets and cache them
     */
    public void reloadCacheOnly() {
        propertyCache = repo.findAll().stream()
                .collect(Collectors.toMap(PropertySet::getId, Function.identity()));
    }

    void reloadCacheAndRefreshContext() {
        stateHolderService.updateConfigStateId();
        reloadCacheOnly();
        if (null != refresher) {
            refresher.refresh();
        }
    }

    /**
     * Find a PropertySet by the application id
     * @param application id of the PropertySet
     * @return the PropertySet if it is found
     */
    public Optional<PropertySet> findById(String application) {
        PropertySet propertySet = propertyCache.get(application);

        if (null == propertySet) {
            reloadCacheOnly();
            propertySet = propertyCache.get(application);
        }

        return Optional.ofNullable(propertySet);
    }

    /**
     * Get all the PropertySets and set the value from the appropriate source Environment.
     * @return the list of the PropertySets showing what values will be provided to the applications
     */
    public List<PropertySet> getPopulatedProperties() {
        Environment externalEnvironment = getExternalEnvironment();

        List<PropertySet> propertySets = repo.findAll();

        List<PropertySet> mergedPropertySets = propertySets.stream()
                .map(fromMongo -> mergeWithExternalProps(fromMongo, externalEnvironment.getPropertySources()))
                .collect(Collectors.toList());

        mergedPropertySets.forEach(this::setDefaultValues);

        addExternalOnlyProperties(mergedPropertySets);

        return mergedPropertySets;
    }

    /**
     * Get all the PropertySets from storage.
     * @return all the stored property sets
     */
    public List<PropertySet> getAll() {
        return repo.findAll();
    }

    /**
     * Update the stored properties based on the list of incoming property updates
     * @param propertyUpdates list of updates to make
     * @return number of updates that occurred
     */
    public int updateProperties(List<PropertyUpdate> propertyUpdates) {
        int propertySetUpdated = repo.updateProperties(propertyUpdates);
        if (propertySetUpdated > 0) {
            reloadCacheAndRefreshContext();
        }
        return propertySetUpdated;
    }

    /**
     * Reset the property value to null
     * @param propertyIds list of property ID's that need to be reset
     * @return number of properties that were reset
     */
    public int unsetProperties(List<PropertyId> propertyIds) {
        int propertySetUpdated = repo.unsetProperties(propertyIds);
        if (propertySetUpdated > 0) {
            reloadCacheAndRefreshContext();
        }
        return propertySetUpdated;
    }

    /**
     * Save the given set of properties. This is always a full replacement
     * if the properties already exist.
     */
    public void saveProperties(PropertySet properties) {
        repo.save(properties);
        reloadCacheAndRefreshContext();
    }

    /**
     * Remove a set of properties from the system
     */
    public boolean removeProperties(String propertySetId) {
        if (repo.removeById(propertySetId)) {
            reloadCacheAndRefreshContext();
            return true;
        }
        return false;
    }

    @Override
    public void updateSnapshot(SystemSnapshot systemSnapshot) {
        systemSnapshot.setPropertySets(getAll().stream()
                .map(this::pruneDefaultProperties)
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
    }

    private PropertySet pruneDefaultProperties(PropertySet propertySet) {
        List<Property> customizedProperties = filterWithCustomValues(propertySet.getProperties());

        if (customizedProperties.isEmpty()) {
            return null;
        }

        PropertySet copy = createCopy(propertySet);
        copy.setProperties(customizedProperties);
        return copy;
    }

    private List<Property> filterWithCustomValues(List<Property> fullList) {
        return null != fullList ? fullList.stream()
                .filter(Property::hasValue).collect(Collectors.toList()) : List.of();
    }

    @Override
    public Result resetFromSnapshot(SystemSnapshot systemSnapshot, boolean hardReset) {

        if (hardReset) {
            // Unset values instead removing/replacing because the snapshot could contain properties from different DeltaFi/Plugin versions
            repo.resetAllPropertyValues();
        }

        List<PropertySet> propertySets = getAll();

        propertyMetadataLoader.keepOverriddenValues(propertySets, systemSnapshot.getPropertySets());
        repo.saveAll(propertySets);

        reloadCacheAndRefreshContext();
        return new Result();
    }

    @Override
    public int getOrder() {
        return SnapshotRestoreOrder.PROPERTY_SET_ORDER;
    }

    private void setDefaultValues(PropertySet propertySet) {
        propertySet.getProperties().forEach(this::setDefaultValue);
    }

    private void setDefaultValue(Property property) {
        if (isNull(property.getValue())) {
            property.setValue(property.getDefaultValue());
            property.setPropertySource(DEFAULT);
        }
    }

    private void addExternalOnlyProperties(List<PropertySet> knownProperties) {
        List<PropertySource> propertySets = getExternalEnvironment().getPropertySources();
        propertySets.forEach(externalPropertySet -> addExternalOnlyProperties(externalPropertySet, knownProperties));
    }

    private void addExternalOnlyProperties(PropertySource externalProperty, List<PropertySet> knownProperties) {
        Optional<PropertySet> knownPropertySet = knownProperties.stream()
                .filter(propertySet -> propertySetMatches(propertySet, externalProperty))
                .findFirst();

        if (knownPropertySet.isPresent()) {
            addExternalOnlyProperties(externalProperty, knownPropertySet.get());
        } else {
            PropertySet propertySet = new PropertySet();
            propertySet.setId(externalProperty.getName());
            propertySet.setDisplayName(externalProperty.getName());
            propertySet.setDescription("Externally defined property set");
            addExternalOnlyProperties(externalProperty, propertySet);
            knownProperties.add(propertySet);
        }
    }

    private void addExternalOnlyProperties(PropertySource externalProperty, PropertySet knownPropertySet) {
        Set<String> knownProperties = knownPropertySet.getProperties().stream()
                .map(Property::getKey).collect(Collectors.toSet());

        Map<?, ?> gitPropMap = externalProperty.getSource();

        for (Map.Entry<?, ?> entry : gitPropMap.entrySet()) {
            if (!knownProperties.contains(entry.getKey().toString())) {
                knownPropertySet.getProperties().add(Property.builder()
                        .description("Externally defined property")
                        .editable(false)
                        .refreshable(false)
                        .hidden(false)
                        .key(entry.getKey().toString())
                        .value(entry.getValue().toString())
                        .propertySource(EXTERNAL)
                        .build());
            }
        }
    }

    private PropertySet mergeWithExternalProps(PropertySet propertySet, List<PropertySource> externalPropertySources) {
        PropertySource externalPropertySource = externalPropertySources.stream()
                .filter(propertySource -> propertySetMatches(propertySet, propertySource))
                .findFirst().orElse(EMPTY_SOURCE);

        // Always run the merge to ensure the propertySource is properly set when the value comes from only mongo
        return merge(propertySet, externalPropertySource);
    }

    private PropertySet merge(PropertySet mongoProps, PropertySource gitProps) {
        PropertySet merged = createCopy(mongoProps);

        Map<?, ?> gitPropMap = gitProps.getSource();

        List<Property> mergedProperties = mongoProps.getProperties().stream()
                .map(property -> mergeProperty(property, gitPropMap.get(property.getKey())))
                .collect(Collectors.toList());

        merged.getProperties().addAll(mergedProperties);

        return merged;
    }

    private Property mergeProperty(Property property, Object value) {
        if (nonNull(property.getValue())) {
            property.setPropertySource(MONGO);
        } else if (nonNull(value)) {
            property.setValue(value.toString());
            property.setPropertySource(EXTERNAL);
        }

        return property;
    }

    /**
     * Only one of GitEnvironmentRepository and NativeEnvironmentRepository can be active at one time.
     * By default, GitEnvironmentRepository is active, check it first.
     *
     * @return - Environment from git if it is present, else tries to get Environment from native
     */
    private Environment getExternalEnvironment() {
        return getGitEnvironmentRepository().map(gitEnvRepo -> gitEnvRepo.findOne(getPropertySetIds()))
                .orElseGet(this::getNativeEnvironment);
    }

    private Environment getNativeEnvironment() {
        return getDeltaFiNativeEnvironmentRepository().map(nativeEnvRepo -> nativeEnvRepo.findOne(getPropertySetIds()))
                .orElse(EMPTY_ENV);
    }

    private Optional<GitEnvironmentRepository> getGitEnvironmentRepository() {
        return Optional.ofNullable(gitEnvironmentRepository);
    }

    private Optional<DeltaFiNativeEnvironmentRepository> getDeltaFiNativeEnvironmentRepository() {
        return Optional.ofNullable(nativeEnvironmentRepository);
    }

    private boolean propertySetMatches(PropertySet propertySet, PropertySource propertySource) {
        return propertySource.getName().contains(propertySet.getId());
    }

    private Set<String> getPropertySetIds() {
        return repo.getIds();
    }

    private PropertySet createCopy(PropertySet propertySet) {
        PropertySet copy = new PropertySet();
        copy.setDisplayName(propertySet.getDisplayName());
        copy.setDescription(propertySet.getDescription());
        copy.setId(propertySet.getId());
        return copy;
    }

}
