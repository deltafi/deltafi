package org.deltafi.ingress.config;

import com.netflix.graphql.dgs.client.DefaultGraphQLClient;
import com.netflix.graphql.dgs.client.GraphQLClient;
import org.deltafi.common.properties.GraphqlClientProperties;

import javax.enterprise.inject.Produces;

public class GraphQLClientConfig {
    @Produces
    public GraphQLClient graphQLClient(GraphqlClientProperties graphqlProperties) {
        return new DefaultGraphQLClient(graphqlProperties.getCoreDomain());
    }
}
