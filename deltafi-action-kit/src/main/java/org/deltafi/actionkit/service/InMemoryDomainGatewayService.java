package org.deltafi.actionkit.service;

import com.netflix.graphql.dgs.client.GraphQLResponse;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import io.quarkus.arc.profile.UnlessBuildProfile;

import javax.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@ApplicationScoped
@UnlessBuildProfile("prod")
public class InMemoryDomainGatewayService implements DomainGatewayService{
    BlockingQueue<String> requests = new LinkedBlockingQueue<>();

    public GraphQLResponse submit(GraphQLQueryRequest request) {
        requests.add(request.serialize());
        return new GraphQLResponse("{}");
    }

    public List<String> getUnsentQueries() {
        List<String> results = new ArrayList<>();
        requests.drainTo(results);
        return results;
    }

    public void clear() {
        requests.clear();
    }
}
