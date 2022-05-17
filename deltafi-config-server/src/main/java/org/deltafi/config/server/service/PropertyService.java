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
package org.deltafi.config.server.service;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.config.server.api.domain.Property;
import org.deltafi.config.server.api.domain.PropertyId;
import org.deltafi.config.server.api.domain.PropertySet;
import org.deltafi.config.server.api.domain.PropertyUpdate;
import org.deltafi.config.server.environment.DeltaFiNativeEnvironmentRepository;
import org.deltafi.config.server.environment.GitEnvironmentRepository;
import org.deltafi.config.server.repo.PropertyRepository;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.deltafi.config.server.api.domain.PropertySource.*;

@Slf4j
@Service
public class PropertyService {

    private static final Environment EMPTY_ENV = new Environment("empty");

    private final PropertyRepository repo;
    private final StateHolderService stateHolderService;
    private final Optional<GitEnvironmentRepository> gitEnvironmentRepository;
    private final Optional<DeltaFiNativeEnvironmentRepository> nativeEnvironmentRepository;

    private final PropertySource EMPTY_SOURCE = new PropertySource("empty", new HashMap<>());

    public PropertyService(PropertyRepository repo, StateHolderService stateHolderService, Optional<GitEnvironmentRepository> gitEnvironmentRepository,
                           Optional<DeltaFiNativeEnvironmentRepository> nativeEnvironmentRepository) {
        this.repo = repo;
        this.stateHolderService = stateHolderService;
        this.gitEnvironmentRepository = gitEnvironmentRepository;
        this.nativeEnvironmentRepository = nativeEnvironmentRepository;
    }

    public Optional<PropertySet> findById(String application) {
        return repo.findById(application);
    }

    public List<PropertySet> getAllProperties() {
        Environment externalEnvironment = getExternalEnvironment();

        // get all properties set via mongo
        List<PropertySet> propertySets = repo.findAll();

        List<PropertySet> mergedPropertySets = propertySets.stream()
                .map(fromMongo -> mergeWithExternalProps(fromMongo, externalEnvironment.getPropertySources()))
                .collect(Collectors.toList());

        mergedPropertySets.forEach(this::setDefaultValues);

        addExternalOnlyProperties(mergedPropertySets);

        return mergedPropertySets;
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
            if (!knownProperties.contains(entry.getKey())) {
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

    public int updateProperties(List<PropertyUpdate> propertyUpdates) {
        int propertySetUpdated = repo.updateProperties(propertyUpdates);
        if (propertySetUpdated > 0) {
            stateHolderService.updateConfigStateId();
        }
        return propertySetUpdated;
    }

    public int unsetProperties(List<PropertyId> propertyIds) {
        int propertySetUpdated = repo.unsetProperties(propertyIds);
        if (propertySetUpdated > 0) {
            stateHolderService.updateConfigStateId();
        }
        return propertySetUpdated;
    }

    /**
     * Save the given set of properties. This is always a full replacement
     * if the properties already exist.
     */
    public void saveProperties(PropertySet properties) {
        repo.save(properties);
        stateHolderService.updateConfigStateId();
    }

    /**
     * Remove a set of properties from the system
     */
    public boolean removeProperties(String propertySetId) {
        if (repo.existsById(propertySetId)) {
            repo.deleteById(propertySetId);
            stateHolderService.updateConfigStateId();
            return true;
        }
        return false;
    }

    /**
     * Only one of GitEnvironmentRepository and NativeEnvironmentRepository can be active at one time.
     * By default, GitEnvironmentRepository is active, check it first.
     *
     * @return - Environment from git if it is present, else tries to get Environment from native
     */
    private Environment getExternalEnvironment() {
        return gitEnvironmentRepository.map(gitEnvRepo -> gitEnvRepo.findOne(getPropertySetIds()))
                .orElseGet(this::getNativeEnvironment);
    }

    private Environment getNativeEnvironment() {
        return nativeEnvironmentRepository.map(nativeEnvRepo -> nativeEnvRepo.findOne(getPropertySetIds()))
                .orElse(EMPTY_ENV);
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
