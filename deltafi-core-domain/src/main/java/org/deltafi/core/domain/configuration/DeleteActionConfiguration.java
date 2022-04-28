package org.deltafi.core.domain.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.deltafi.core.domain.api.types.ActionSchema;
import org.deltafi.core.domain.api.types.DeleteActionSchema;

import java.util.Collections;
import java.util.List;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NONE
)
public class DeleteActionConfiguration extends org.deltafi.core.domain.generated.types.DeleteActionConfiguration implements ActionConfiguration {

    public DeleteActionConfiguration(String name, String type) {
        super(name, null, type, Collections.emptyMap());
    } 

    @Override
    public List<String> validate(ActionSchema actionSchema) {
        if (!(actionSchema instanceof DeleteActionSchema)) {
            return List.of("Action: " + getType() + " is not registered as a DeleteAction");
        }
        return Collections.emptyList();
    }
}