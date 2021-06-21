package org.deltafi.action.egress;

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import org.deltafi.action.Result;
import org.deltafi.dgs.generated.client.EgressGraphQLQuery;
import org.deltafi.dgs.generated.client.EgressProjectionRoot;
import org.jetbrains.annotations.NotNull;

public class EgressResult extends Result {

    @SuppressWarnings("CdiInjectionPointsInspection")
    EgressResult(EgressAction action, String did) {
        super(action, did);
    }

    @NotNull
    @Override
    public GraphQLQuery toQuery() {
        EgressGraphQLQuery.Builder builder = EgressGraphQLQuery.newRequest()
                .did(did)
                .fromEgressAction(name);

        return builder.build();
    }

    public BaseProjectionNode getProjection() {
        return new EgressProjectionRoot()
                .did()
                .actions().errorMessage()
                .parent();
    }
}