package org.deltafi.actionkit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.netflix.graphql.dgs.client.GraphQLClient;
import com.netflix.graphql.dgs.client.GraphQLResponse;
import com.netflix.graphql.dgs.client.RequestExecutor;
import com.netflix.graphql.dgs.client.codegen.BaseProjectionNode;
import com.netflix.graphql.dgs.client.codegen.BaseSubProjectionNode;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import io.quarkus.runtime.StartupEvent;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.coerce.StringCoercing;
import org.deltafi.dgs.generated.client.DeltaFileGraphQLQuery;
import org.deltafi.dgs.generated.client.DeltaFileProjectionRoot;
import org.deltafi.actionkit.exception.DgsPostException;
import org.deltafi.actionkit.serializers.DeltaFileDeserializer;
import org.deltafi.actionkit.types.DeltaFile;
import org.deltafi.dgs.generated.client.DeltaFile_DomainsProjection;
import org.deltafi.dgs.generated.client.DeltaFile_EnrichmentProjection;

import graphql.schema.Coercing;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

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

    /**
     * Manually parse the response with the customized ObjectMapper
     * so any custom mixins are picked up
     * @param response the response
     * @return the list of DeltaFiles
     */
    public DeltaFile parseResponse(GraphQLResponse response) {
        JsonNode jsonNode;
        try {
            jsonNode = mapper.readTree(response.getJson());
        } catch (JsonProcessingException e) {
            log.error("Could not parse the DGS GraphqlResponse json", e);
            return null;
        }
        JsonNode deltaFileNode = jsonNode.get(DATA_PATH).get("deltaFile");
        return mapper.convertValue(deltaFileNode, DeltaFile.class);
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

    public DeltaFile federate(DeltaFile deltaFile, Map<String, BaseProjectionNode> domainProjections, Map<String, BaseProjectionNode> enrichmentProjections) {
        DeltaFileProjectionRoot root = null;
        boolean foundOne = false;

        for (String domain : domainProjections.keySet()) {
            if (deltaFile.getDomains().getDomainTypes().contains(domain)) {
                if (Objects.isNull(root)) {
                    root = new DeltaFileProjectionRoot().did();
                }

                if (!root.getFields().containsKey("domains")) {
                    DeltaFile_DomainsProjection projection = new DeltaFile_DomainsProjection(null, null).did();
                    root.getFields().put("domains", projection);
                }

                foundOne = true;
                Map<String, Object> map = ((BaseSubProjectionNode<?,?>) root.getFields().get("domains")).getFields();
                map.put(domain, domainProjections.get(domain));
            }
        }

        for (String enrichment : enrichmentProjections.keySet()) {
            if (deltaFile.getEnrichment().getEnrichmentTypes().contains(enrichment)) {
                if (Objects.isNull(root)) {
                    root = new DeltaFileProjectionRoot().did();
                }

                if (!root.getFields().containsKey("enrichment")) {
                    DeltaFile_EnrichmentProjection projection = new DeltaFile_EnrichmentProjection(null, null).did();
                    root.getFields().put("enrichment", projection);
                }

                foundOne = true;
                Map<String, Object> map = ((BaseSubProjectionNode<?,?>) root.getFields().get("enrichment")).getFields();
                map.put(enrichment, enrichmentProjections.get(enrichment));
            }
        }

        if (foundOne) {
            DeltaFileGraphQLQuery query = DeltaFileGraphQLQuery.newRequest().did(deltaFile.getDid()).build();
            GraphQLQueryRequest request = new GraphQLQueryRequest(query, root, BLOCK_QUOTE);
            GraphQLResponse response = submit(request);
            DeltaFile appendedFile = parseResponse(response);
            deltaFile.setDomainDetails(appendedFile.getDomainDetails());
            deltaFile.setEnrichmentDetails(appendedFile.getEnrichmentDetails());
        }

        return deltaFile;
    }

}
