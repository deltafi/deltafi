package org.deltafi.actionkit.action.load;

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.generated.client.RegisterLoadSchemaGraphQLQuery;
import org.deltafi.core.domain.generated.client.RegisterLoadSchemaProjectionRoot;
import org.deltafi.core.domain.generated.types.LoadActionSchemaInput;

public abstract class LoadAction<P extends ActionParameters> extends Action<P> {
    public LoadAction(Class<P> actionParametersClass) {
        super(actionParametersClass);
    }

    public abstract String getConsumes();

    @Override
    protected BaseProjectionNode getRegistrationProjection() {
        return new RegisterLoadSchemaProjectionRoot().id();
    }

    @Override
    public GraphQLQuery getRegistrationQuery() {
        LoadActionSchemaInput paramInput = LoadActionSchemaInput.newBuilder()
            .id(getClassCanonicalName())
            .paramClass(getParamClass())
            .actionKitVersion(getVersion())
            .schema(getDefinition())
            .consumes(getConsumes())
            .build();
        return RegisterLoadSchemaGraphQLQuery.newRequest().actionSchema(paramInput).build();
    }

}
