package org.deltafi.actionkit.action.filter;

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.Result;
import org.deltafi.dgs.generated.client.FilterGraphQLQuery;
import org.deltafi.dgs.generated.client.FilterProjectionRoot;
import org.deltafi.dgs.generated.types.ActionEventType;

public class FilterResult extends Result {

    final String filterMessage;

    @SuppressWarnings("CdiInjectionPointsInspection")
    public FilterResult(String name, String did, String filterMessage) {
        super(name, did);
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
                    .errorCause()
                    .state()
                .parent();
    }

    @Override final public ResultType resultType() { return ResultType.GRAPHQL; }

    @Override
    final public ActionEventType actionEventType() { return ActionEventType.FILTER; }
}