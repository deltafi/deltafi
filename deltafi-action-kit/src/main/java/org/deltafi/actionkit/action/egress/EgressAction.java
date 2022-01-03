package org.deltafi.actionkit.action.egress;

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import org.deltafi.actionkit.action.Action;
import org.deltafi.core.domain.generated.client.RegisterEgressSchemaGraphQLQuery;
import org.deltafi.core.domain.generated.client.RegisterEgressSchemaProjectionRoot;
import org.deltafi.core.domain.generated.types.EgressActionSchemaInput;

public abstract class EgressAction<P extends EgressActionParameters> extends Action<P> {
    public EgressAction(Class<P> actionParametersClass) {
        super(actionParametersClass);
    }

    @Override
    public GraphQLQuery getRegistrationQuery() {
        EgressActionSchemaInput paramInput = EgressActionSchemaInput.newBuilder()
                .id(getClassCanonicalName())
                .paramClass(getParamClass())
                .actionKitVersion(getVersion())
                .schema(getDefinition())
                .build();
        return RegisterEgressSchemaGraphQLQuery.newRequest().actionSchema(paramInput).build();
    }

    @Override
    protected BaseProjectionNode getRegistrationProjection() {
        return new RegisterEgressSchemaProjectionRoot().id();
    }
}
