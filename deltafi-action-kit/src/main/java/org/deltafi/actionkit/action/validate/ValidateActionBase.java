package org.deltafi.actionkit.action.validate;

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.generated.client.RegisterValidateSchemaGraphQLQuery;
import org.deltafi.core.domain.generated.client.RegisterValidateSchemaProjectionRoot;
import org.deltafi.core.domain.generated.types.ValidateActionSchemaInput;

public abstract class ValidateActionBase<P extends ActionParameters> extends Action<P> {
    public ValidateActionBase(Class<P> actionParametersClass) {
        super(actionParametersClass);
    }

    @Override
    public GraphQLQuery getRegistrationQuery() {
        ValidateActionSchemaInput paramInput = ValidateActionSchemaInput.newBuilder()
                .id(getClassCanonicalName())
                .paramClass(getParamClass())
                .actionKitVersion(getVersion())
                .schema(getDefinition())
                .build();
        return RegisterValidateSchemaGraphQLQuery.newRequest().actionSchema(paramInput).build();
    }

    @Override
    protected BaseProjectionNode getRegistrationProjection() {
        return new RegisterValidateSchemaProjectionRoot().id();
    }
}
