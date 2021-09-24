package org.deltafi.actionkit.config;

import com.netflix.graphql.dgs.client.DefaultGraphQLClient;
import com.netflix.graphql.dgs.client.GraphQLClient;

import javax.enterprise.inject.Produces;

public class GraphQLClientConfig {
    @Produces
    public GraphQLClient graphQLClient(DeltafiConfig config) {
        return new DefaultGraphQLClient(config.dgs().url());
    }
}