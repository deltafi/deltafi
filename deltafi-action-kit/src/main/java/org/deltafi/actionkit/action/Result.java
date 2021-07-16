package org.deltafi.actionkit.action;

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import org.deltafi.dgs.generated.types.ActionEventInput;
import org.deltafi.dgs.generated.types.ActionEventType;

import java.time.OffsetDateTime;

abstract public class Result {
    protected final String name;
    protected final String did;

    // temporary hack: this should probably be handled with subclasses but GRAPHQL responses are going away soon
    public enum ResultType {
        GRAPHQL,
        QUEUE
    }

    @SuppressWarnings("CdiInjectionPointsInspection")
    public Result(Action action, String did) {
        name = action.name();
        this.did = did;
    }

    abstract public ResultType resultType();
    abstract public ActionEventType actionEventType();

    // ResultType graphql
    public GraphQLQuery toQuery() { return null; }
    public BaseProjectionNode getProjection() { return null; };

    // ResultType queue
    public ActionEventInput toEvent() {
        return ActionEventInput.newBuilder()
                .did(did)
                .action(name)
                .time(OffsetDateTime.now())
                .type(actionEventType())
                .build();
    }
}