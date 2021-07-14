package org.deltafi.actionkit.action.filter;

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.Result;
import org.deltafi.dgs.generated.client.FilterGraphQLQuery;
import org.deltafi.dgs.generated.client.FilterProjectionRoot;

public class FilterResult extends Result {

    final String filterMessage;

    @SuppressWarnings("CdiInjectionPointsInspection")
    public FilterResult(Action action, String did, String filterMessage) {
        super(action, did);
        this.filterMessage = filterMessage;
    }

    @Override
    public GraphQLQuery toQuery() {
        return FilterGraphQLQuery.newRequest()
                .did(did)
                .fromAction(name)
                .message(filterMessage)
                .build();
    }

    @Override
    public BaseProjectionNode getProjection() {
        return new FilterProjectionRoot()
                .did()
                .stage()
                .actions()
                    .name()
                    .errorMessage()
                    .state()
                .parent();
    }
}