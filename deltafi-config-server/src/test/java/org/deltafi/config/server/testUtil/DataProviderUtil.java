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
package org.deltafi.config.server.testUtil;

import org.deltafi.config.server.api.domain.Property;
import org.deltafi.config.server.api.domain.PropertySet;

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
