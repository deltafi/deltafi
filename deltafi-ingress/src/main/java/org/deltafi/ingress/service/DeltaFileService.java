package org.deltafi.ingress.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.graphql.dgs.client.GraphQLError;
import com.netflix.graphql.dgs.client.GraphQLResponse;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.metric.MetricLogger;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.trace.ZipkinService;
import org.deltafi.core.domain.api.types.KeyValue;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.generated.client.IngressGraphQLQuery;
import org.deltafi.core.domain.generated.client.IngressProjectionRoot;
import org.deltafi.core.domain.api.types.Content;
import org.deltafi.core.domain.generated.types.IngressInput;
import org.deltafi.ingress.exceptions.DeltafiException;
import org.deltafi.ingress.exceptions.DeltafiGraphQLException;
import org.deltafi.ingress.exceptions.DeltafiMetadataException;

import javax.enterprise.context.ApplicationScoped;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.deltafi.common.constant.DeltaFiConstants.INGRESS_ACTION;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class DeltaFileService {
    private final GraphQLClientService graphQLClientService;
    private final ContentStorageService contentStorageService;
    private final ZipkinService zipkinService;
    private final ObjectMapper objectMapper;

    private static final IngressProjectionRoot PROJECTION_ROOT = new IngressProjectionRoot().did();

    public String ingressData(InputStream inputStream, String sourceFileName, String flow, String metadataString, String mediaType)
            throws ObjectStorageException, DeltafiException, DeltafiMetadataException {
        String did = UUID.randomUUID().toString();
        OffsetDateTime created = OffsetDateTime.now();

        ContentReference contentReference = contentStorageService.save(did, inputStream, mediaType);
        List<Content> content = Collections.singletonList(Content.newBuilder().contentReference(contentReference).name(sourceFileName).build());

        try {
            sendToIngressGraphQl(did, sourceFileName, flow, metadataString, content, created);
        } catch (Exception e) {
            contentStorageService.delete(contentReference);
            throw e;
        }

        logMetric(did, sourceFileName, flow, "files_in", 1);
        logMetric(did, sourceFileName, flow, "bytes_in", contentReference.getSize());

        sendTrace(did, sourceFileName, flow, created);

        return did;
    }

    private KeyValue toKeyValueInput(Map.Entry<String, JsonNode> entry) {
        JsonNode node = entry.getValue();
        String value = node.isTextual() ? node.asText() : node.toString();
        return new KeyValue(entry.getKey(), value);
    }

    private void sendToIngressGraphQl(String did, String sourceFileName, String flow, String metadataString,
                                      List<Content> content, OffsetDateTime created) throws DeltafiMetadataException, DeltafiException {
        IngressInput ingressInput = IngressInput.newBuilder()
                .did(did)
                .sourceInfo(new SourceInfo(sourceFileName, flow, fromMetadataString(metadataString)))
                .content(content)
                .created(created)
                .build();

        GraphQLResponse response;
        try {
            response = graphQLClientService.executeGraphQLQuery(toGraphQlRequest(ingressInput));
        } catch (DeltafiGraphQLException e) {
            logMetric(did, sourceFileName, flow, "files_dropped", 1);
            throw e;
        } catch (Exception e) {
            logMetric(did, sourceFileName, flow, "files_dropped", 1);
            throw new DeltafiException(e.getMessage());
        }

        if (response.hasErrors()) {
            logMetric(did, sourceFileName, flow, "files_dropped", 1);
            throw new DeltafiGraphQLException(
                    response.getErrors().stream().map(GraphQLError::getMessage).collect(Collectors.joining(",")));
        }
    }

    List<KeyValue> fromMetadataString(String metadata) throws DeltafiMetadataException {
        if (Objects.isNull(metadata)) {
            return Collections.emptyList();
        }

        try {
            Map<String, JsonNode> keyValueMap = objectMapper.readValue(metadata, new TypeReference<>() {});
            return keyValueMap.entrySet().stream().map(this::toKeyValueInput).collect(Collectors.toList());
        } catch (JsonProcessingException e) {
            throw new DeltafiMetadataException("Could not parse metadata, metadata must be a JSON Object" + e.getMessage());
        }
    }

    private GraphQLQueryRequest toGraphQlRequest(IngressInput ingressInput) {
        IngressGraphQLQuery ingressGraphQLQuery = IngressGraphQLQuery.newRequest().input(ingressInput).build();
        return new GraphQLQueryRequest(ingressGraphQLQuery, PROJECTION_ROOT);
    }

    private void logMetric(String did, String fileName, String flow, String metric, long value) {
        Map<String, String> tags = Map.of("filename", fileName, "action", INGRESS_ACTION);
        MetricLogger.logMetric("ingress", did, flow, metric, value, tags);
    }

    private void sendTrace(String did, String fileName, String flow, OffsetDateTime created) {
        if (zipkinService.isEnabled()) {
            zipkinService.markSpanComplete(zipkinService.createChildSpan(did, INGRESS_ACTION, fileName, flow, created));
        }
    }
}