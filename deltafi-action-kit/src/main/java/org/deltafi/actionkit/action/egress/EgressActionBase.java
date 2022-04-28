package org.deltafi.actionkit.action.egress;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.ActionType;
import org.deltafi.core.domain.generated.types.ActionRegistrationInput;
import org.deltafi.core.domain.generated.types.EgressActionSchemaInput;

public abstract class EgressActionBase<P extends EgressActionParameters> extends Action<P> {
    public EgressActionBase(Class<P> actionParametersClass) {
        super(ActionType.EGRESS, actionParametersClass);
    }

    @Override
    public void registerSchema(ActionRegistrationInput actionRegistrationInput) {
        EgressActionSchemaInput input = EgressActionSchemaInput.newBuilder()
                .id(getClassCanonicalName())
                .paramClass(getParamClass())
                .schema(getDefinition())
                .build();
        actionRegistrationInput.getEgressActions().add(input);
    }
}
