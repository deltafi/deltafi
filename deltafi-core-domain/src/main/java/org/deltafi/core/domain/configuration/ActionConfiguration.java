package org.deltafi.core.domain.configuration;

import org.deltafi.core.domain.api.types.JsonMap;

public interface ActionConfiguration extends DeltaFiConfiguration {
    String getType();
    JsonMap getParameters();
    void setParameters(JsonMap jsonMap);
}