package org.deltafi.dgs.configuration;

import org.deltafi.dgs.api.types.JsonMap;

public interface ActionConfiguration extends DeltaFiConfiguration {
    String getType();
    JsonMap getParameters();
    void setParameters(JsonMap jsonMap);
}
