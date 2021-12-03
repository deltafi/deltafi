package org.deltafi.config.server.repo;

import org.assertj.core.api.Assertions;
import org.deltafi.config.server.constants.PropertyConstants;
import org.deltafi.config.server.domain.Property;
import org.deltafi.config.server.domain.PropertySet;
import org.deltafi.config.server.domain.PropertyUpdate;
import org.deltafi.config.server.testUtil.DataProviderUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.deltafi.config.server.constants.PropertyConstants.DELTAFI_PROPERTY_SET;

@DataMongoTest
class PropertyRepositoryImplTest {

    private static final String TEST_PLUGIN = "test-plugin";
    private static final String EDITABLE = "editable";
    private static final String NOT_EDITABLE = "not-editable";
    private static final String ORIGINAL_VALUE = "original";

    @Autowired
    PropertyRepository propertyRepository;

    @BeforeEach
    public void loadData() {
        PropertySet common = buildPropertySet(DELTAFI_PROPERTY_SET);
        PropertySet actionKit = buildPropertySet(PropertyConstants.ACTION_KIT_PROPERTY_SET);
        PropertySet testPlugin = buildPropertySet(TEST_PLUGIN);

        propertyRepository.saveAll(List.of(common, actionKit, testPlugin));
    }

    @Test
    void getIds() {
        Set<String> ids = propertyRepository.getIds();
        assertThat(ids).hasSize(3)
                .contains(DELTAFI_PROPERTY_SET, PropertyConstants.ACTION_KIT_PROPERTY_SET, TEST_PLUGIN);
    }

    @Test
    void updateProperties() {
        PropertyUpdate commonUpdate = PropertyUpdate.builder()
                .propertySetId(DELTAFI_PROPERTY_SET).key(EDITABLE).value("new value").build();
        PropertyUpdate pluginUpdate = PropertyUpdate.builder()
                .propertySetId(TEST_PLUGIN).key(EDITABLE).value("new value").build();

        int propertySetsUpdated = propertyRepository.updateProperties(List.of(commonUpdate, pluginUpdate));
        assertThat(propertySetsUpdated).isEqualTo(2);

        Assertions.assertThat(getValue(DELTAFI_PROPERTY_SET)).isEqualTo("new value");
        Assertions.assertThat(getValue(TEST_PLUGIN)).isEqualTo("new value");
    }

    @Test
    void updateProperties_notEditable() {
        PropertyUpdate notEditable = PropertyUpdate.builder()
                .propertySetId(DELTAFI_PROPERTY_SET).key(NOT_EDITABLE).value("new value").build();

        int propertySetsUpdated = propertyRepository.updateProperties(List.of(notEditable));
        assertThat(propertySetsUpdated).isZero();

        Assertions.assertThat(getValue(DELTAFI_PROPERTY_SET, NOT_EDITABLE)).isEqualTo(ORIGINAL_VALUE);

    }

    @Test
    void updateProperties_notExists() {
        PropertyUpdate notExists = PropertyUpdate.builder()
                .propertySetId(DELTAFI_PROPERTY_SET).key("missing").value("abc").build();

        int propertySetsUpdated = propertyRepository.updateProperties(List.of(notExists));
        assertThat(propertySetsUpdated).isZero();
    }

    String getValue(String propertySet) {
        return getValue(propertySet, EDITABLE);
    }

    String getValue(String propertySet, String key) {
        return propertyRepository.findById(propertySet)
                .map(PropertySet::getProperties).orElse(Collections.emptySet()).stream()
                .filter(p -> key.equals(p.getKey())).findFirst().map(Property::getValue).orElse(null);
    }

    PropertySet buildPropertySet(String name) {
        PropertySet propertySet = DataProviderUtil.getPropertySet(name);
        propertySet.getProperties().add(getProperty(EDITABLE, true));
        propertySet.getProperties().add(getProperty(NOT_EDITABLE, false));
        return propertySet;
    }

    Property getProperty(String name, boolean editable) {
        return Property.builder()
                .key(name)
                .editable(editable)
                .defaultValue("default it")
                .value(ORIGINAL_VALUE).build();
    }

}