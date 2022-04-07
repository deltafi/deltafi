package org.deltafi.actionkit.action.delete;

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.ActionType;
import org.deltafi.core.domain.generated.client.RegisterDeleteSchemaGraphQLQuery;
import org.deltafi.core.domain.generated.client.RegisterDeleteSchemaProjectionRoot;
import org.deltafi.core.domain.generated.types.DeleteActionSchemaInput;

public abstract class DeleteActionBase extends Action<ActionParameters> {
    public DeleteActionBase() {
        super(ActionType.DELETE, ActionParameters.class);
    }

    @Override
    public GraphQLQuery getRegistrationQuery() {
        DeleteActionSchemaInput paramInput = DeleteActionSchemaInput.newBuilder()
                .id(getClassCanonicalName())
                .paramClass(getParamClass())
                .actionKitVersion(getVersion())
                .schema(getDefinition())
                .build();
        return RegisterDeleteSchemaGraphQLQuery.newRequest().actionSchema(paramInput).build();
    }

    @Override
    protected BaseProjectionNode getRegistrationProjection() {
        return new RegisterDeleteSchemaProjectionRoot().id();
    }
}
