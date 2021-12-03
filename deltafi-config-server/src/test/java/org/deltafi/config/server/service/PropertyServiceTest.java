package org.deltafi.config.server.service;

import org.deltafi.config.server.domain.Property;
import org.deltafi.config.server.domain.PropertySet;
import org.deltafi.config.server.domain.PropertyUpdate;
import org.deltafi.config.server.environment.GitEnvironmentRepository;
import org.deltafi.config.server.repo.PropertyRepository;
import org.deltafi.config.server.testUtil.DataProviderUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.deltafi.config.server.constants.PropertyConstants.DELTAFI_PROPERTY_SET;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class PropertyServiceTest {

    private static final String PLUGIN_APP = "plugin-app";
    private static final String IN_BOTH = "inBoth";
    private static final String GIT_VALUE = "gitValue";
    private static final String GIT_ONLY = "gitOnly";
    private static final String ONLY_SET_IN_GIT = "onlySetInGit";
    private static final String MONGO_VALUE = "mongoValue";
    private static final String MONGO_ONLY = "mongoOnly";
    private static final String DEFAULT_VALUE = "defaultValue";
    public static final String UNKNOWN_PLUGIN = "unknown-plugin";

    PropertyService propertyService;

    @Mock
    PropertyRepository propertyRepository;

    @Mock
    StateHolderService stateHolderService;

    @Mock
    GitEnvironmentRepository gitRepo;

    @Mock
    Resource boostrapProps;

    @BeforeEach
    public void setup() {
        propertyService = new PropertyService(propertyRepository, stateHolderService, Optional.of(gitRepo), Optional.empty());
        propertyService.setPropertyMetadata(boostrapProps);
    }


    @Test
    void loadDefaultPropertySets() throws IOException {
        Mockito.when(boostrapProps.exists()).thenReturn(true);
        Mockito.when(boostrapProps.isReadable()).thenReturn(true);
        Mockito.when(boostrapProps.getInputStream()).thenReturn(new ByteArrayInputStream("[]]".getBytes()));

        Mockito.when(propertyRepository.count()).thenReturn(0L);

        propertyService.loadDefaultPropertySets();
        Mockito.verify(propertyRepository).saveAll(Mockito.anyList());
    }

    @Test
    void loadDefaultPropertySets_alreadyLoaded() {
        Mockito.when(propertyRepository.count()).thenReturn(1L);

        propertyService.loadDefaultPropertySets();
        Mockito.verify(propertyRepository, times(0)).saveAll(Mockito.anyList());
    }

    @Test
    void loadDefaultPropertySets_noBootstrapFile() {
        Mockito.when(boostrapProps.exists()).thenReturn(false);
        Mockito.when(propertyRepository.count()).thenReturn(0L);

        propertyService.loadDefaultPropertySets();
        Mockito.verify(propertyRepository, times(0)).saveAll(Mockito.anyList());
    }

    @Test
    void getAllProperties() {
        Set<String> ids = Set.of(DELTAFI_PROPERTY_SET, PLUGIN_APP);
        Mockito.when(propertyRepository.getIds()).thenReturn(ids);
        Mockito.when(propertyRepository.findAll()).thenReturn(mongoEnv());
        Mockito.when(gitRepo.findOne(ids)).thenReturn(gitEnv());
        List<PropertySet> propertySets = propertyService.getAllProperties();

        assertThat(propertySets).hasSize(2);

        Set<Property> common = propertySets.stream().filter(ps -> DELTAFI_PROPERTY_SET.equals(ps.getId()))
                .findFirst().map(PropertySet::getProperties).orElse(Collections.emptySet());

        assertThat(common).hasSize(4);

        assertThat(getValue(common, MONGO_ONLY)).isEqualTo(MONGO_VALUE);
        assertThat(getValue(common, IN_BOTH)).isEqualTo(MONGO_VALUE);
        assertThat(getValue(common, ONLY_SET_IN_GIT)).isEqualTo(GIT_VALUE);
        assertThat(getProperty(common, DEFAULT_VALUE)).isPresent();
        assertThat(getValue(common, DEFAULT_VALUE)).isEqualTo(DEFAULT_VALUE);

        // only known properties should be returned
        assertThat(getProperty(common, GIT_ONLY)).isEmpty();
        assertThat(propertySets.stream().filter(ps -> UNKNOWN_PLUGIN.equals(ps.getId()))).isEmpty();

        Set<Property> plugin = propertySets.stream().filter(ps -> PLUGIN_APP.equals(ps.getId()))
                .findFirst().map(PropertySet::getProperties).orElse(Collections.emptySet());
        assertThat(plugin).hasSize(1);
    }

    String getValue(Set<Property> props, String key) {
        return getProperty(props, key).map(Property::getValue).orElse(null);
    }

    Optional<Property> getProperty(Set<Property> props, String key) {
        return props.stream().filter( p -> key.equals(p.getKey())).findFirst();
    }


    @Test
    void updateProperties() {
        List<PropertyUpdate> updates = List.of(PropertyUpdate.builder()
                .propertySetId(DELTAFI_PROPERTY_SET).key("key").value("value").build());
        Mockito.when(propertyRepository.updateProperties(updates)).thenReturn(1);
        propertyService.updateProperties(updates);
        Mockito.verify(stateHolderService).updateConfigStateId();
    }

    @Test
    void updateProperties_noneUpdated() {
        List<PropertyUpdate> updates = List.of(PropertyUpdate.builder()
                .propertySetId(DELTAFI_PROPERTY_SET).key("key").value("value").build());
        Mockito.when(propertyRepository.updateProperties(updates)).thenReturn(0);
        propertyService.updateProperties(updates);
        Mockito.verify(stateHolderService, times(0)).updateConfigStateId();
    }

    @Test
    void saveProperties() {
        PropertySet propertySet = DataProviderUtil.getPropertySet("test");
        propertyService.saveProperties(propertySet);
        Mockito.verify(propertyRepository).save(propertySet);
        Mockito.verify(stateHolderService).updateConfigStateId();
    }

    @Test
    void removeProperties() {
        Mockito.when(propertyRepository.existsById("props")).thenReturn(true);
        propertyService.removeProperties("props");
        Mockito.verify(propertyRepository).deleteById("props");
        Mockito.verify(stateHolderService).updateConfigStateId();
    }

    @Test
    void removeProperties_notFound() {
        Mockito.when(propertyRepository.existsById("props")).thenReturn(false);
        propertyService.removeProperties("props");
        Mockito.verify(propertyRepository, times(0)).deleteById("props");
        Mockito.verify(stateHolderService, times(0)).updateConfigStateId();
    }

    Environment gitEnv() {
        Environment env = new Environment("git", "default");
        Map<String, String> props = Map.of(IN_BOTH, GIT_VALUE, GIT_ONLY, GIT_VALUE, ONLY_SET_IN_GIT, GIT_VALUE);
        PropertySource propertySource = new PropertySource(DELTAFI_PROPERTY_SET, props);
        PropertySource shouldBeIgnored = new PropertySource(UNKNOWN_PLUGIN, props);
        env.addFirst(shouldBeIgnored);
        env.addFirst(propertySource);
        return env;
    }

    List<PropertySet> mongoEnv() {
        PropertySet common = DataProviderUtil.getPropertySet(DELTAFI_PROPERTY_SET);
        Property inBoth = Property.builder().key(IN_BOTH).value(MONGO_VALUE).build();
        Property mongoOnly = Property.builder().key(MONGO_ONLY).value(MONGO_VALUE).build();
        Property setFromGit = Property.builder().key(ONLY_SET_IN_GIT).defaultValue(DEFAULT_VALUE).build();
        Property valueNotSet = Property.builder().key(DEFAULT_VALUE).defaultValue(DEFAULT_VALUE).build();
        common.getProperties().addAll(List.of(inBoth, mongoOnly, setFromGit, valueNotSet));

        PropertySet knownPlugin = DataProviderUtil.getPropertySetWithProperty(PLUGIN_APP);
        return List.of(common, knownPlugin);
    }
}