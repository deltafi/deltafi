package org.deltafi.actionkit.action.egress;

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.ActionType;
import org.deltafi.core.domain.generated.client.RegisterEgressSchemaGraphQLQuery;
import org.deltafi.core.domain.generated.client.RegisterEgressSchemaProjectionRoot;
import org.deltafi.core.domain.generated.types.EgressActionSchemaInput;

public abstract class EgressActionBase<P extends EgressActionParameters> extends Action<P> {
    public EgressActionBase(Class<P> actionParametersClass) {
        super(ActionType.EGRESS, actionParametersClass);
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
