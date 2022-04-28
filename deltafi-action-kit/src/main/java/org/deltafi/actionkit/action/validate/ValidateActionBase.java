package org.deltafi.actionkit.action.validate;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.ActionType;
import org.deltafi.core.domain.generated.types.ActionRegistrationInput;
import org.deltafi.core.domain.generated.types.ValidateActionSchemaInput;

public abstract class ValidateActionBase<P extends ActionParameters> extends Action<P> {
    public ValidateActionBase(Class<P> actionParametersClass) {
        super(ActionType.VALIDATE, actionParametersClass);
    }

    @Override
    public void registerSchema(ActionRegistrationInput actionRegistrationInput) {
        ValidateActionSchemaInput input = ValidateActionSchemaInput.newBuilder()
                .id(getClassCanonicalName())
                .paramClass(getParamClass())
                .schema(getDefinition())
                .build();
        actionRegistrationInput.getValidateActions().add(input);
    }
}
