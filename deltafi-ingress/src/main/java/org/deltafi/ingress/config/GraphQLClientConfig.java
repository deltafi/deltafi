package org.deltafi.ingress.config;

import com.netflix.graphql.dgs.client.DefaultGraphQLClient;
import com.netflix.graphql.dgs.client.GraphQLClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.inject.Produces;

public class GraphQLClientConfig {
    @ConfigProperty(name = "deltafi.dgs.url")
    String deltafiDgsUrl;

    @Produces
    public GraphQLClient graphQLClient() {
        return new DefaultGraphQLClient(deltafiDgsUrl);
    }
}
