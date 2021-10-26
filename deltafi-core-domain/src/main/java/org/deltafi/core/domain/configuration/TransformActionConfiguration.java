package org.deltafi.core.domain.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isBlank;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NONE
)
@SuppressWarnings("unused")
public class TransformActionConfiguration extends org.deltafi.core.domain.generated.types.TransformActionConfiguration implements ActionConfiguration {

    @Override
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        if (isBlank(this.getConsumes())) {
            errors.add("Required property consumes is not set");
        }

        if (isBlank(this.getProduces())) {
            errors.add("Required property produces is not set");
        }

        return errors;
    }

}