package org.deltafi.action.load;

import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery;
import org.deltafi.action.Result;
import org.deltafi.dgs.generated.client.LoadGraphQLQuery;
import org.deltafi.dgs.generated.client.LoadProjectionRoot;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class LoadResult extends Result {

    @SuppressWarnings("CdiInjectionPointsInspection")
    public LoadResult(LoadAction action, String did) {
        super(action, did);
    }

    protected final List<String> domains = new ArrayList<>();

    @NotNull
    @Override
    public GraphQLQuery toQuery() {
        LoadGraphQLQuery.Builder builder = LoadGraphQLQuery.newRequest()
                .did(did)
                .domains(domains)
                .fromLoadAction(name);

        return builder.build();
    }

    public BaseProjectionNode getProjection() {
        return new LoadProjectionRoot()
                .did()
                .actions().errorMessage()
                .parent();
    }

    public void addDomain(@NotNull String domain) {
        domains.add(domain);
    }
}