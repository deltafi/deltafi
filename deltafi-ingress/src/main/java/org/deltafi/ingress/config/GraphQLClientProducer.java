package org.deltafi.ingress.config;

import com.netflix.graphql.dgs.client.DefaultGraphQLClient;
import com.netflix.graphql.dgs.client.GraphQLClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Singleton;
import javax.ws.rs.Produces;
import java.net.http.HttpClient;

@Singleton
public class GraphQLClientProducer {

    @ConfigProperty(name = "deltafi.dgs.url")
    String deltafiDgsUrl;

    @Produces
    @ApplicationScoped
    public GraphQLClient getGraphQLClient() {
        return new DefaultGraphQLClient(deltafiDgsUrl);
    }

    @Produces
    @ApplicationScoped
    public HttpClient getHttpClient() {
        return HttpClient.newHttpClient();
    }

}
