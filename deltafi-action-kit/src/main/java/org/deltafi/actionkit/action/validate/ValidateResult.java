package org.deltafi.actionkit.action.validate;

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import org.deltafi.actionkit.action.Result;
import org.deltafi.dgs.generated.client.ValidateGraphQLQuery;
import org.deltafi.dgs.generated.client.ValidateProjectionRoot;
import org.jetbrains.annotations.NotNull;

public class ValidateResult extends Result {

    @SuppressWarnings("CdiInjectionPointsInspection")
    public ValidateResult(ValidateAction action, String did) {
        super(action, did);
    }

    @NotNull
    @Override
    public GraphQLQuery toQuery() {
        ValidateGraphQLQuery.Builder builder = ValidateGraphQLQuery.newRequest()
                .did(did)
                .fromValidateAction(name);

        return builder.build();
    }

    public BaseProjectionNode getProjection() {
        return new ValidateProjectionRoot()
                .did()
                .actions().errorMessage()
                .parent();
    }
}
