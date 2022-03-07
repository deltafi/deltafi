package org.deltafi.actionkit.service;

import com.netflix.graphql.dgs.client.GraphQLClient;
import com.netflix.graphql.dgs.client.GraphQLResponse;
import com.netflix.graphql.dgs.client.RequestExecutor;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.exception.DgsPostException;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Collections;

@ApplicationScoped
@Slf4j
public class HttpDomainGatewayService implements DomainGatewayService{

    @Inject
    GraphQLClient graphQLClient;

    @Inject
    RequestExecutor requestExecutor;

    HttpDomainGatewayService() {
        log.debug(this.getClass().getSimpleName() + " instantiated");
    }

    @SuppressWarnings("unused")
    public GraphQLResponse submit(GraphQLQueryRequest request) {
        boolean retried = false;
        GraphQLResponse response = null;
        while(response == null) {
            try {
                response = graphQLClient.executeQuery(request.serialize(), Collections.emptyMap(), requestExecutor);
            } catch (DgsPostException e) {
                log.error("Exception in GraphQL submission");
                try {
                    Thread.sleep(200, 0);
                } catch (Exception ignored) {}
                if (!retried) {
                    log.warn("Retrying DGS submission due to DGS outage");
                    retried = true;
                }
            }
        }
        if (response.hasErrors()) {
            StringBuilder errorMessage = new StringBuilder("Error in DGS submission:\n");
            errorMessage.append("\nOriginal query:\n")
                        .append(request.serialize()).append("\n\n");
            for(var err:response.getErrors()) {
                errorMessage.append(err.getMessage()).append("\n");
            }
            log.error(errorMessage.toString().trim());
            throw new DgsPostException(errorMessage.toString());
        }

        return response;
    }
}