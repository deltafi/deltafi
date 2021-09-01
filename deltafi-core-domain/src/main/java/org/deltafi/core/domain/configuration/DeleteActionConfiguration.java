package org.deltafi.core.domain.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NONE
)
public class DeleteActionConfiguration extends org.deltafi.core.domain.generated.types.DeleteActionConfiguration implements ActionConfiguration {

}