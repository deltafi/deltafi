package org.deltafi.dgs.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NONE
)
public class EgressActionConfiguration extends org.deltafi.dgs.generated.types.EgressActionConfiguration implements ActionConfiguration {

}
