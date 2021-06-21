package org.deltafi.config;

import com.netflix.graphql.dgs.client.DefaultGraphQLClient;
import com.netflix.graphql.dgs.client.GraphQLClient;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Singleton;
import javax.ws.rs.Produces;

@Slf4j
@Singleton
public class GraphQLClientConfig {
    @ConfigProperty(name = "deltafi.dgs.url")
    String dgsUrl;

    @Produces
    @ApplicationScoped
    public GraphQLClient getGraphQLClient() {
        log.info("deltafi.dgs.url: " + dgsUrl);
        return new DefaultGraphQLClient(dgsUrl);
    }
}