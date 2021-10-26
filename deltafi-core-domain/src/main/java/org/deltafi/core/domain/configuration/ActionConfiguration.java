package org.deltafi.core.domain.configuration;

import org.deltafi.core.domain.api.types.JsonMap;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public interface ActionConfiguration extends DeltaFiConfiguration {
    String getType();
    JsonMap getParameters();
    void setParameters(JsonMap jsonMap);
    /**
     * Validates this action configuration.
     *
     * @return a List of validation errors or an empty list if there are no errors
     */
    default List<String> validate() {
        return Collections.emptyList();
    }
    static boolean missingRequiredList(List<?> list) {
        return Objects.isNull(list) || list.isEmpty();
    }
}