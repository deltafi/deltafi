package org.deltafi.core.domain.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.deltafi.core.domain.api.types.ActionSchema;
import org.deltafi.core.domain.api.types.ValidateActionSchema;

import java.util.ArrayList;
import java.util.List;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NONE
)
public class ValidateActionConfiguration extends org.deltafi.core.domain.generated.types.ValidateActionConfiguration implements ActionConfiguration {

    @Override
    public List<String> validate(ActionSchema actionSchema) {
        List<String> errors = new ArrayList<>();

        if (!(actionSchema instanceof ValidateActionSchema)) {
            errors.add("Action: " + getType() + " is not registered as a ValidateAction");
        }

        return errors;
    }

}
