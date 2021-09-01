package org.deltafi.core.domain.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NONE
)
public class EgressActionConfiguration extends org.deltafi.core.domain.generated.types.EgressActionConfiguration implements ActionConfiguration {

}