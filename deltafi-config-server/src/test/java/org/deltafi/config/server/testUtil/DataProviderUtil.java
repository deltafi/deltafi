package org.deltafi.config.server.testUtil;

import org.deltafi.config.server.domain.Property;
import org.deltafi.config.server.domain.PropertySet;

public class DataProviderUtil {

    public static PropertySet getPropertySet(String name) {
        PropertySet propertySet = new PropertySet();
        propertySet.setId(name);
        propertySet.setDisplayName(name);
        propertySet.setDescription("some property set");
        return propertySet;
    }

    public static PropertySet getPropertySetWithProperty(String name) {
        PropertySet propertySet = getPropertySet(name);
        propertySet.getProperties().add(getProperty());
        return propertySet;
    }

    public static Property getProperty() {
        return Property.builder()
                .key("a")
                .value("a-value")
                .defaultValue("a-value")
                .description("some description")
                .editable(true)
                .build();
    }

}
