package org.deltafi.core.domain.converters;

import org.deltafi.core.domain.types.EgressFlow;
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
