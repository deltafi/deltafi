package org.deltafi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.netflix.graphql.dgs.client.GraphQLClient;
import com.netflix.graphql.dgs.client.GraphQLResponse;
import com.netflix.graphql.dgs.client.RequestExecutor;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import io.quarkus.runtime.StartupEvent;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.action.Action;
import org.deltafi.action.Result;
import org.deltafi.coerce.StringCoercing;
import org.deltafi.exception.DgsPostException;
import org.deltafi.serializers.DeltaFileDeserializer;
import org.deltafi.types.DeltaFile;

import graphql.schema.Coercing;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;


@ApplicationScoped
@Slf4j
public class DomainGatewayService {

    private static final Map<Class<?>, Coercing<?, ?>> BLOCK_QUOTE = Map.of(String.class, new StringCoercing());

    public static final String DATA_PATH = "data";

    @SuppressWarnings("CdiInjectionPointsInspection")
    @Inject
    GraphQLClient graphQLClient;

    @Inject
    RequestExecutor requestExecutor;

    final ObjectMapper mapper = new ObjectMapper();

    // Guarantee instantiation if not injected...
    @SuppressWarnings("EmptyMethod")
    void startup(@Observes StartupEvent event) {}

    static DomainGatewayService instance;

    @SuppressWarnings("unused")
    static public DomainGatewayService instance() { return instance; }

    DomainGatewayService() {
        log.debug(this.getClass().getSimpleName() + " instantiated");
        instance = this;
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        SimpleModule module = new SimpleModule();
        module.addDeserializer(DeltaFile.class, new DeltaFileDeserializer());
        mapper.registerModule(module);
    }

    List<DeltaFile> getDeltaFilesFor(Action action, Integer limit) {
        var request = new GraphQLQueryRequest(action.getFeedQuery(limit), action.getProjection(), BLOCK_QUOTE);
        if (log.isDebugEnabled()) log.debug("Executing query:\n" + request.serialize());

        GraphQLResponse response;
        try {
            response = graphQLClient.executeQuery(request.serialize(), Collections.emptyMap(), requestExecutor);
        } catch(DgsPostException e) {
            return Collections.emptyList();
        }

        log.debug("Response:\n" + response.getJson());

        if (response.hasErrors()) {
            StringBuilder errorMessage = new StringBuilder("Feed query has errors: \n");
            for(var err:response.getErrors()) {
                errorMessage.append(err.getMessage()).append("\n");
            }
            log.error(errorMessage.toString());
            return Collections.emptyList();
        }

        return parseResponse(response, action);
    }

    /**
     * Manually parse the response with the customized ObjectMapper
     * so any custom mixins are picked up
     * @param response the response
     * @param action the action
     * @return the list of DeltaFiles
     */
    public List<DeltaFile> parseResponse(GraphQLResponse response, Action action) {
        JsonNode jsonNode;
        try {
            jsonNode = mapper.readTree(response.getJson());
        } catch (JsonProcessingException e) {
            log.error("Could not parse the DGS GraphqlResponse json", e);
            return Collections.emptyList();
        }
        JsonNode deltaFileNode = jsonNode.get(DATA_PATH).get(action.getFeedPath());
        return mapper.convertValue(deltaFileNode, new TypeReference<>() {});
    }

    public GraphQLResponse submit(Result result) {
        return submit(new GraphQLQueryRequest(result.toQuery(), result.getProjection(), BLOCK_QUOTE));
    }

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
            log.error("Query has errors");
            for(var err:response.getErrors()) {
                errorMessage.append(err.getMessage()).append("\n");
            }
            log.error(errorMessage.toString().trim());
            throw new DgsPostException(errorMessage.toString());
        }

        return response;
    }

}