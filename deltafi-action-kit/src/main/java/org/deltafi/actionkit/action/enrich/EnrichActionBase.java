package org.deltafi.actionkit.action.enrich;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.ActionType;
import org.deltafi.core.domain.generated.types.ActionRegistrationInput;
import org.deltafi.core.domain.generated.types.EnrichActionSchemaInput;

import java.util.Collections;
import java.util.List;

public abstract class EnrichActionBase<P extends ActionParameters> extends Action<P> {
    public EnrichActionBase(Class<P> actionParametersClass) {
        super(ActionType.ENRICH, actionParametersClass);
    }

    public abstract List<String> getRequiresDomains();

    public List<String> getRequiresEnrichment() {
        return Collections.emptyList();
    }

    @Override
    public void registerSchema(ActionRegistrationInput actionRegistrationInput) {
        EnrichActionSchemaInput input = EnrichActionSchemaInput.newBuilder()
                .id(getClassCanonicalName())
                .paramClass(getParamClass())
                .schema(getDefinition())
                .requiresDomains(getRequiresDomains())
                .requiresEnrichment(getRequiresEnrichment())
                .build();
        actionRegistrationInput.getEnrichActions().add(input);
    }
}
