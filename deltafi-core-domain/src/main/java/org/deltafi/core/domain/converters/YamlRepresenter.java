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
package org.deltafi.core.domain.converters;

import org.deltafi.core.domain.types.EgressFlow;
import org.deltafi.core.domain.types.EnrichFlow;
import org.deltafi.core.domain.types.IngressFlow;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;

import static java.util.Objects.isNull;

/**
 * Representer that ignores null fields, empty fields and date fields
 */
public class YamlRepresenter extends Representer {

    public YamlRepresenter() {
        super();
        this.addClassTag(IngressFlow.class, Tag.MAP);
        this.addClassTag(EgressFlow.class, Tag.MAP);
        this.addClassTag(EnrichFlow.class, Tag.MAP);
    }

    @Override
    protected NodeTuple representJavaBeanProperty(Object javaBean, Property property,
                                                  Object propertyValue, Tag customTag) {
        return ignorable(propertyValue) ? null : super.representJavaBeanProperty(javaBean, property, propertyValue, customTag);
    }

    boolean ignorable(Object propertyValue) {
        return isEmpty(propertyValue) || isDate(propertyValue);
    }

    boolean isDate(Object propertyValue) {
        return propertyValue instanceof OffsetDateTime;
    }

    boolean isEmpty(Object propertyValue) {
        if (isNull(propertyValue)) {
            return true;
        }

        if (propertyValue instanceof Collection) {
            return ((Collection<?>) propertyValue).isEmpty();
        } else if (propertyValue instanceof Map) {
            return ((Map<?, ?>) propertyValue).isEmpty();
        }

        return false;
    }
}
