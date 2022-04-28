package org.deltafi.core.domain.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.deltafi.core.domain.api.types.ActionSchema;
import org.deltafi.core.domain.api.types.TransformActionSchema;

import java.util.ArrayList;
import java.util.List;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NONE
)
@SuppressWarnings("unused")
public class TransformActionConfiguration extends org.deltafi.core.domain.generated.types.TransformActionConfiguration implements ActionConfiguration {

    @Override
    public List<String> validate(ActionSchema actionSchema) {
        List<String> errors = new ArrayList<>();

        if (actionSchema instanceof TransformActionSchema) {
            TransformActionSchema schema = (TransformActionSchema) actionSchema;
            if (!ActionConfiguration.equalOrAny(schema.getConsumes(), this.getConsumes())) {
                errors.add("The action configuration consumes value must be: " + schema.getConsumes());
            }
            if (!ActionConfiguration.equalOrAny(schema.getProduces(), this.getProduces())) {
                errors.add("The action configuration produces value must be: " + schema.getProduces());
            }
        } else {
            errors.add("Action: " + getType() + " is not registered as a TransformAction") ;
        }

        return errors;
    }

}
