package org.deltafi.core.domain.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.ArrayList;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NONE
)
public class EgressFlowConfiguration extends org.deltafi.core.domain.generated.types.EgressFlowConfiguration implements DeltaFiConfiguration {

    public EgressFlowConfiguration() {
        setValidateActions(new ArrayList<>());
        setIncludeIngressFlows(new ArrayList<>());
        setExcludeIngressFlows(new ArrayList<>());
    }

}