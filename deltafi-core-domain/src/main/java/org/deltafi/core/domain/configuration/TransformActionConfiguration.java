package org.deltafi.core.domain.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NONE
)
@SuppressWarnings("unused")
public class TransformActionConfiguration extends org.deltafi.core.domain.generated.types.TransformActionConfiguration implements ActionConfiguration {

}