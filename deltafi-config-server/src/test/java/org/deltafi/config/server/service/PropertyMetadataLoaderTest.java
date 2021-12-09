package org.deltafi.config.server.service;

import org.assertj.core.api.Assertions;
import org.deltafi.config.server.api.domain.Property;
import org.deltafi.config.server.api.domain.PropertySet;
import org.deltafi.config.server.testUtil.DataProviderUtil;
import org.junit.jupiter.api.Test;

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

    PropertyMetadataLoader propertyMetadataLoader = new PropertyMetadataLoader(null);

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