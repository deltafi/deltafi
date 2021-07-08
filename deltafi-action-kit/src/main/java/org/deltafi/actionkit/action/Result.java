package org.deltafi.actionkit.action;

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;

abstract public class Result {
    protected final String name;
    protected final String did;

    @SuppressWarnings("CdiInjectionPointsInspection")
    public Result(Action action, String did) {
        name = action.name();
        this.did = did;
    }

    abstract public GraphQLQuery toQuery();
    abstract public BaseProjectionNode getProjection();

}