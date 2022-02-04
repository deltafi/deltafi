package org.deltafi.core.domain.configuration;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public interface ActionConfiguration extends DeltaFiConfiguration {
    String getType();
    Map<String, Object> getParameters();
    void setParameters(Map<String, Object> parameters);
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