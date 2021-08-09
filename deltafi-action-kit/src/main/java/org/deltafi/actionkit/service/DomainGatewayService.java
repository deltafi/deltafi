package org.deltafi.actionkit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netflix.graphql.dgs.client.GraphQLClient;
import com.netflix.graphql.dgs.client.GraphQLResponse;
import com.netflix.graphql.dgs.client.RequestExecutor;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import io.quarkus.runtime.StartupEvent;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.exception.DgsPostException;
import org.deltafi.dgs.api.types.DeltaFile;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.util.Collections;

@ApplicationScoped
@Slf4j
public class DomainGatewayService {

    public static final String DATA_PATH = "data";

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    GraphQLClient graphQLClient;

    @Inject
    RequestExecutor requestExecutor;

    final ObjectMapper mapper = new ObjectMapper();

    @SuppressWarnings("EmptyMethod")
    void startup(@Observes StartupEvent event) {
        // Guarantee instantiation if not injected...
    }

    static DomainGatewayService instance;

    @SuppressWarnings("unused")
    public static DomainGatewayService instance() { return instance; }

    DomainGatewayService() {
        log.debug(this.getClass().getSimpleName() + " instantiated");
        instance = this;
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(MapperFeature.DEFAULT_VIEW_INCLUSION, true);

        mapper.registerModule(new JavaTimeModule());
    }

    /**
     * Manually parse the response with the customized ObjectMapper
     * so any custom mixins are picked up
     * @param response the response
     * @return the list of DeltaFiles
     */
    @SuppressWarnings("unused")
    public DeltaFile parseResponse(GraphQLResponse response) {
        JsonNode jsonNode;
        try {
            jsonNode = mapper.readTree(response.getJson());
        } catch (JsonProcessingException e) {
            log.error("Could not parse the DGS GraphqlResponse json", e);
            return null;
        }

        try {
            JsonNode deltaFileNode = jsonNode.get(DATA_PATH).get("deltaFile");
            return mapper.convertValue(deltaFileNode, DeltaFile.class);
        } catch (Throwable t) {
            log.error("Could not convert json to DeltaFile: ", t);
            throw new RuntimeException("Could not convert json to DeltaFile", t);
        }
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