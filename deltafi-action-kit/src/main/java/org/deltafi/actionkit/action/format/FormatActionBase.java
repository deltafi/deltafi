package org.deltafi.actionkit.action.format;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.ActionType;
import org.deltafi.core.domain.generated.types.ActionRegistrationInput;
import org.deltafi.core.domain.generated.types.FormatActionSchemaInput;

import java.util.Collections;
import java.util.List;

public abstract class FormatActionBase<P extends ActionParameters> extends Action<P> {
    public FormatActionBase(Class<P> actionParametersClass) {
        super(ActionType.FORMAT, actionParametersClass);
    }

    public abstract List<String> getRequiresDomains();

    public List<String> getRequiresEnrichment() {
        return Collections.emptyList();
    }

    @Override
    public void registerSchema(ActionRegistrationInput actionRegistrationInput) {
        FormatActionSchemaInput input = FormatActionSchemaInput.newBuilder()
                .id(getClassCanonicalName())
                .paramClass(getParamClass())
                .schema(getDefinition())
                .requiresDomains(getRequiresDomains())
                .requiresEnrichment(getRequiresEnrichment())
                .build();
        actionRegistrationInput.getFormatActions().add(input);
    }
}
