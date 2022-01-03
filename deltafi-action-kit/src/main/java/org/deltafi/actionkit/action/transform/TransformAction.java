package org.deltafi.actionkit.action.transform;

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.generated.client.RegisterTransformSchemaGraphQLQuery;
import org.deltafi.core.domain.generated.client.RegisterTransformSchemaProjectionRoot;
import org.deltafi.core.domain.generated.types.TransformActionSchemaInput;

public abstract class TransformAction<P extends ActionParameters> extends Action<P> {
    public TransformAction(Class<P> actionParametersClass) {
        super(actionParametersClass);
    }

    public abstract String getConsumes();

    public abstract String getProduces();

    @Override
    protected BaseProjectionNode getRegistrationProjection() {
        return new RegisterTransformSchemaProjectionRoot().id();
    }

    @Override
    public GraphQLQuery getRegistrationQuery() {
        TransformActionSchemaInput paramInput = TransformActionSchemaInput.newBuilder()
            .id(getClassCanonicalName())
            .paramClass(getParamClass())
            .actionKitVersion(getVersion())
            .schema(getDefinition())
            .consumes(getConsumes())
            .produces(getProduces())
            .build();
        return RegisterTransformSchemaGraphQLQuery.newRequest().actionSchema(paramInput).build();
    }
}
