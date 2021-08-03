package org.deltafi.dgs.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.ArrayList;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NONE
)
public class IngressFlowConfiguration extends org.deltafi.dgs.generated.types.IngressFlowConfiguration implements DeltaFiConfiguration {

    public IngressFlowConfiguration() {
        // loadActions variable is a list of candidate LoadActions that may operate on data in this flow
        setLoadActions(new ArrayList<>());
        // transformActions variable is an ordered list of the TransformActions that each data item will flow through
        setTransformActions(new ArrayList<>());
    }

}
