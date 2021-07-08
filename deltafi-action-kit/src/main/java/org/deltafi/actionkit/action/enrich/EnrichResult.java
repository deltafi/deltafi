package org.deltafi.actionkit.action.enrich;

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import org.deltafi.actionkit.action.Result;
import org.deltafi.dgs.generated.client.EnrichGraphQLQuery;
import org.deltafi.dgs.generated.client.EnrichProjectionRoot;
import org.jetbrains.annotations.NotNull;

public class EnrichResult extends Result {

    @SuppressWarnings("CdiInjectionPointsInspection")
    public EnrichResult(EnrichAction action, String did) {
        super(action, did);
    }

    @NotNull
    @Override
    public GraphQLQuery toQuery() {
        // TODO: Add enrichments...types don't seem quite right.
        EnrichGraphQLQuery.Builder builder = EnrichGraphQLQuery.newRequest()
                .did(did)
                .fromEnrichAction(name);

        return builder.build();
    }

    public BaseProjectionNode getProjection() {
        return new EnrichProjectionRoot()
                .did()
                .actions()
                .errorMessage()
                .parent();
    }
}