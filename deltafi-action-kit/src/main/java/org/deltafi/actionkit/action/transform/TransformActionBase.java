package org.deltafi.actionkit.action.transform;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.ActionType;
import org.deltafi.core.domain.generated.types.ActionRegistrationInput;
import org.deltafi.core.domain.generated.types.TransformActionSchemaInput;

public abstract class TransformActionBase<P extends ActionParameters> extends Action<P> {
    public TransformActionBase(Class<P> actionParametersClass) {
        super(ActionType.TRANSFORM, actionParametersClass);
    }

    public abstract String getConsumes();

    public abstract String getProduces();

    @Override
    public void registerSchema(ActionRegistrationInput actionRegistrationInput) {
        TransformActionSchemaInput input = TransformActionSchemaInput.newBuilder()
                .id(getClassCanonicalName())
                .paramClass(getParamClass())
                .schema(getDefinition())
                .consumes(getConsumes())
                .produces(getProduces())
                .build();
        actionRegistrationInput.getTransformActions().add(input);
    }
}
