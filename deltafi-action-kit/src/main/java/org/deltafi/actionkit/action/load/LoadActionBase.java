package org.deltafi.actionkit.action.load;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.ActionType;
import org.deltafi.core.domain.generated.types.ActionRegistrationInput;
import org.deltafi.core.domain.generated.types.LoadActionSchemaInput;

public abstract class LoadActionBase<P extends ActionParameters> extends Action<P> {
    public LoadActionBase(Class<P> actionParametersClass) {
        super(ActionType.LOAD, actionParametersClass);
    }

    public abstract String getConsumes();

    @Override
    public void registerSchema(ActionRegistrationInput actionRegistrationInput) {
        LoadActionSchemaInput input = LoadActionSchemaInput.newBuilder()
                .id(getClassCanonicalName())
                .paramClass(getParamClass())
                .schema(getDefinition())
                .consumes(getConsumes())
                .build();
        actionRegistrationInput.getLoadActions().add(input);
    }
}
