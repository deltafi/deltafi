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

import org.assertj.core.api.Assertions;
import org.deltafi.config.server.api.domain.Property;
import org.deltafi.config.server.api.domain.PropertySet;
import org.deltafi.config.server.environment.DefaultPropertyEnvironmentRepository;
import org.deltafi.config.server.testUtil.DataProviderUtil;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.Set;

class PropertyMetadataLoaderTest {
    private static final String id = "id";
    private static final String expectedDescription = "updated description";
    private static final String displayName = "New Display Name";
    private static final String noChange = "noChange";
    private static final String newKey = "new";
    private static final String keep = "keep";
    private static final String removedKey = "removed";

    PropertyMetadataLoader propertyMetadataLoader = new PropertyMetadataLoader(null, Mockito.mock(DefaultPropertyEnvironmentRepository.class));

    @Test
    void keepOverriddenValues() {
        PropertySet incoming = DataProviderUtil.getPropertySet(id);
        incoming.setDescription(expectedDescription);
        incoming.setDisplayName(displayName);

        Property noChanges = DataProviderUtil.getProperty();
        noChanges.setKey(noChange);

        Property newProp = DataProviderUtil.getProperty();
        newProp.setKey(newKey);

        Property useOverride = DataProviderUtil.getProperty();
        useOverride.setKey(keep);
        useOverride.setValue(null);
        useOverride.setDescription(newKey);
        useOverride.setDefaultValue(newKey);
        useOverride.setHidden(true);
        useOverride.setEditable(true);
        useOverride.setRefreshable(true);

        incoming.setProperties(Set.of(noChanges, newProp, useOverride));


        PropertySet current = DataProviderUtil.getPropertySet(id);
        current.setDescription("Old description");
        current.setDisplayName("Old Display Name");

        Property removed = DataProviderUtil.getProperty();
        removed.setKey(removedKey);
        removed.setValue(removedKey);

        Property keepOverride = DataProviderUtil.getProperty();
        keepOverride.setKey(keep);
        keepOverride.setValue(keep);
        keepOverride.setDescription("old");
        keepOverride.setDefaultValue("old");

        current.setProperties(Set.of(noChanges, removed, keepOverride));

        propertyMetadataLoader.keepOverriddenValues(List.of(incoming), List.of(current));

        Assertions.assertThat(incoming.getId()).isEqualTo(id);
        Assertions.assertThat(incoming.getDescription()).isEqualTo(expectedDescription);
        Assertions.assertThat(incoming.getDisplayName()).isEqualTo(displayName);

        Assertions.assertThat(getProperty(incoming, noChange)).isPresent().contains(noChanges);
        Assertions.assertThat(getProperty(incoming, newKey)).isPresent().contains(newProp);
        Assertions.assertThat(getProperty(incoming, removedKey)).isEmpty();

        Property keptValue = getProperty(incoming, keep).orElseGet(() -> Assertions.fail("Missing keep property"));

        // Verify only the value is used from the current property
        Assertions.assertThat(keptValue.getValue()).isEqualTo(keep);
        Assertions.assertThat(keptValue.getDescription()).isEqualTo(newKey);
        Assertions.assertThat(keptValue.getDefaultValue()).isEqualTo(newKey);
        Assertions.assertThat(keptValue.isEditable()).isTrue();
        Assertions.assertThat(keptValue.isRefreshable()).isTrue();
        Assertions.assertThat(keptValue.isHidden()).isTrue();
    }

    Optional<Property> getProperty(PropertySet propertySet, String key) {
        return propertySet.getProperties().stream().filter(p -> key.equals(p.getKey())).findFirst();
    }

}