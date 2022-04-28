package org.deltafi.core.domain.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.deltafi.core.domain.api.types.ActionSchema;
import org.deltafi.core.domain.api.types.FormatActionSchema;

import java.util.ArrayList;
import java.util.List;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NONE
)
public class FormatActionConfiguration extends org.deltafi.core.domain.generated.types.FormatActionConfiguration implements ActionConfiguration {

    public FormatActionConfiguration() {
        setRequiresDomains(new ArrayList<>());
        setRequiresEnrichment(new ArrayList<>());
    }

    @Override
    public List<String> validate(ActionSchema actionSchema) {
        List<String> errors = new ArrayList<>();

        if (actionSchema instanceof FormatActionSchema) {
            FormatActionSchema schema = (FormatActionSchema) actionSchema;
            if (!ActionConfiguration.equalOrAny(schema.getRequiresDomains(), this.getRequiresDomains())) {
                errors.add("The action configuration requiresDomains value must be: " + schema.getRequiresDomains());
            }
            if (!ActionConfiguration.equalOrAny(schema.getRequiresEnrichment(), this.getRequiresEnrichment())) {
                errors.add("The action configuration requiresEnrichment value must be: " + schema.getRequiresEnrichment());
            }
        } else {
            errors.add("Action: " + getType() + " is not registered as a FormatAction");
        }

        return errors;
    }
}
