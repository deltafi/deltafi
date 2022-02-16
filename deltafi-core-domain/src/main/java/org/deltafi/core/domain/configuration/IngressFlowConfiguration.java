package org.deltafi.core.domain.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.ArrayList;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NONE
)
public class IngressFlowConfiguration extends org.deltafi.core.domain.generated.types.IngressFlowConfiguration implements DeltaFiConfiguration {

    public IngressFlowConfiguration() {
        // transformActions variable is an ordered list of the TransformActions that each data item will flow through
        setTransformActions(new ArrayList<>());
    }

}
