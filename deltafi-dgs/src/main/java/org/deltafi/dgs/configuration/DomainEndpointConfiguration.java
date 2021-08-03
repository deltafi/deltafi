package org.deltafi.dgs.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NONE
)
public class DomainEndpointConfiguration extends org.deltafi.dgs.generated.types.DomainEndpointConfiguration implements DeltaFiConfiguration {

}
