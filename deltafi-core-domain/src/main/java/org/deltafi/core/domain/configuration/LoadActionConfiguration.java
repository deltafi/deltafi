package org.deltafi.core.domain.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.List;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NONE
)
public class LoadActionConfiguration extends org.deltafi.core.domain.generated.types.LoadActionConfiguration implements ActionConfiguration {

    @Override
    public List<String> validate() {
        return StringUtils.isBlank(this.getConsumes()) ?
                List.of("Required property consumes is not set") : Collections.emptyList();
    }
}
