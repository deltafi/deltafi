package org.deltafi.config.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.config.server.api.domain.Property;
import org.deltafi.config.server.api.domain.PropertySet;
import org.deltafi.config.server.api.domain.PropertyUpdate;
import org.deltafi.config.server.environment.DeltaFiNativeEnvironmentRepository;
import org.deltafi.config.server.environment.GitEnvironmentRepository;
import org.deltafi.config.server.repo.PropertyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.deltafi.config.server.api.domain.PropertySource.*;

@Slf4j
@Service
public class PropertyService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Environment EMPTY_ENV = new Environment("empty");

    private final PropertyRepository repo;
    private final StateHolderService stateHolderService;
    private final Optional<GitEnvironmentRepository> gitEnvironmentRepository;
    private final Optional<DeltaFiNativeEnvironmentRepository> nativeEnvironmentRepository;

    @Value("classpath:property-metadata.json")
    private Resource propertyMetadata;

    public PropertyService(PropertyRepository repo, StateHolderService stateHolderService, Optional<GitEnvironmentRepository> gitEnvironmentRepository,
                           Optional<DeltaFiNativeEnvironmentRepository> nativeEnvironmentRepository) {
        this.repo = repo;
        this.stateHolderService = stateHolderService;
        this.gitEnvironmentRepository = gitEnvironmentRepository;
        this.nativeEnvironmentRepository = nativeEnvironmentRepository;
    }

    @PostConstruct
    public void loadDefaultPropertySets()  {
        if (shouldLoadPropertyMetadata()) {
            try {
                List<PropertySet> propertySets = OBJECT_MAPPER.readValue(propertyMetadata.getInputStream(), new TypeReference<>() {});
                repo.saveAll(propertySets);
                log.info("Loaded default property sets");
            } catch (IOException e) {
                log.error("Failed to load default property sets", e);
            }
        }
    }

    public Optional<PropertySet> findById(String application) {
        return repo.findById(application);
    }

    public List<PropertySet> getAllProperties() {
        Environment gitEnvironment = getExternalEnvironment();

        // get all properties set via mongo
        List<PropertySet> propertySets = repo.findAll();

        List<PropertySet> mergedPropertySets = propertySets.stream()
                .map(fromMongo -> mergeWithGitProps(fromMongo, gitEnvironment.getPropertySources()))
                .collect(Collectors.toList());

        mergedPropertySets.forEach(this::setDefaultValues);

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

    private PropertySet mergeWithGitProps(PropertySet propertySet, List<PropertySource> gitPropertySources) {
        return gitPropertySources.stream()
                .filter(propertySource -> propertySource.getName().contains(propertySet.getId()))
                .findFirst()
                .map(fromGit -> merge(propertySet, fromGit))
                .orElse(propertySet);
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

    private boolean shouldLoadPropertyMetadata() {
        return Objects.nonNull(propertyMetadata) && repo.count() == 0 && propertyMetadata.exists() && propertyMetadata.isReadable();
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

    public void setPropertyMetadata(Resource resource) {
        this.propertyMetadata = resource;
    }
}
