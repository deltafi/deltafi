package org.deltafi.actionkit.action.error;

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.Result;
import org.deltafi.dgs.generated.client.ErrorGraphQLQuery;
import org.deltafi.dgs.generated.client.ErrorProjectionRoot;

public class ErrorResult extends Result {

    final String errorMessage;

    public ErrorResult(Action action, String did, String errorMessage) {
        super(action, did);
        this.errorMessage = errorMessage;
    }

    @Override
    public GraphQLQuery toQuery() {
        return ErrorGraphQLQuery.newRequest()
                .did(did)
                .fromAction(name)
                .message(errorMessage)
                .build();
    }

    @Override
    public BaseProjectionNode getProjection() {
        return new ErrorProjectionRoot()
                .did()
                .stage()
                .actions()
                    .name()
                    .errorMessage()
                    .state()
                .parent();
    }
}