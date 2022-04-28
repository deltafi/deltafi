package org.deltafi.actionkit.action.delete;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.ActionType;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.generated.types.ActionRegistrationInput;
import org.deltafi.core.domain.generated.types.DeleteActionSchemaInput;

public abstract class DeleteActionBase extends Action<ActionParameters> {
    protected DeleteActionBase() {
        super(ActionType.DELETE, ActionParameters.class);
    }

    @Override
    public void registerSchema(ActionRegistrationInput actionRegistrationInput) {
        DeleteActionSchemaInput input = DeleteActionSchemaInput.newBuilder()
                .id(getClassCanonicalName())
                .paramClass(getParamClass())
                .schema(getDefinition())
                .build();
        actionRegistrationInput.getDeleteActions().add(input);
    }
}
