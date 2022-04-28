package org.deltafi.core.domain.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.deltafi.core.domain.api.types.ActionSchema;
import org.deltafi.core.domain.api.types.LoadActionSchema;

import java.util.ArrayList;
import java.util.List;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NONE
)
public class LoadActionConfiguration extends org.deltafi.core.domain.generated.types.LoadActionConfiguration implements ActionConfiguration {

    @Override
    public List<String> validate(ActionSchema actionSchema) {
        List<String> errors = new ArrayList<>();

        if (actionSchema instanceof LoadActionSchema) {
            LoadActionSchema schema = (LoadActionSchema) actionSchema;
            if (!ActionConfiguration.equalOrAny(schema.getConsumes(), this.getConsumes())) {
                errors.add("The action configuration consumes value must be: " + schema.getConsumes());
            }
        } else {
            errors.add("Action: " + getType() + " is not registered as a LoadAction");
        }

        return errors;
    }
}
