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
package org.deltafi.config.server.repo;

import org.assertj.core.api.Assertions;
import org.deltafi.config.server.api.domain.Property;
import org.deltafi.config.server.api.domain.PropertyId;
import org.deltafi.config.server.api.domain.PropertySet;
import org.deltafi.config.server.api.domain.PropertyUpdate;
import org.deltafi.config.server.constants.PropertyConstants;
import org.deltafi.config.server.testUtil.DataProviderUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.deltafi.common.test.TestConstants.MONGODB_CONTAINER;
import static org.deltafi.config.server.constants.PropertyConstants.DELTAFI_PROPERTY_SET;

@DataMongoTest
@Testcontainers
class PropertyRepositoryImplTest {

    @Container
    public static MongoDBContainer mongoDBContainer = new MongoDBContainer(MONGODB_CONTAINER);

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

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
    void unsetProperties() {
        PropertyId commonUpdate = PropertyId.builder()
                .propertySetId(DELTAFI_PROPERTY_SET).key(EDITABLE).build();
        PropertyId pluginUpdate = PropertyId.builder()
                .propertySetId(TEST_PLUGIN).key(EDITABLE).build();

        int propertySetsUpdated = propertyRepository.unsetProperties(List.of(commonUpdate, pluginUpdate));
        assertThat(propertySetsUpdated).isEqualTo(2);

        Assertions.assertThat(getValue(DELTAFI_PROPERTY_SET)).isNull();
        Assertions.assertThat(getValue(TEST_PLUGIN)).isNull();
    }

    @Test
    void unsetProperties_notEditable() {
        PropertyId notEditable = PropertyId.builder()
                .propertySetId(DELTAFI_PROPERTY_SET).key(NOT_EDITABLE).build();

        int propertySetsUpdated = propertyRepository.unsetProperties(List.of(notEditable));
        assertThat(propertySetsUpdated).isZero();

        Assertions.assertThat(getValue(DELTAFI_PROPERTY_SET, NOT_EDITABLE)).isEqualTo(ORIGINAL_VALUE);
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