package org.deltafi.ingress.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.graphql.dgs.client.GraphQLError;
import com.netflix.graphql.dgs.client.GraphQLResponse;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import io.minio.ObjectWriteResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.metric.MetricLogger;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.storage.s3.ObjectStorageService;
import org.deltafi.common.trace.DeltafiSpan;
import org.deltafi.common.trace.ZipkinService;
import org.deltafi.core.domain.generated.client.IngressGraphQLQuery;
import org.deltafi.core.domain.generated.client.IngressProjectionRoot;
import org.deltafi.core.domain.generated.types.IngressInput;
import org.deltafi.core.domain.generated.types.KeyValueInput;
import org.deltafi.core.domain.generated.types.ObjectReferenceInput;
import org.deltafi.core.domain.generated.types.SourceInfoInput;
import org.deltafi.ingress.exceptions.DeltafiException;
import org.deltafi.ingress.exceptions.DeltafiGraphQLException;
import org.deltafi.ingress.exceptions.DeltafiMetadataException;

import javax.enterprise.context.ApplicationScoped;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.deltafi.common.constant.DeltaFiConstants.MINIO_BUCKET;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class DeltaFileService {
    public static final String INGRESS_ACTION = "IngressAction";

    private final GraphQLClientService graphQLClientService;
    private final ObjectStorageService objectStorageService;
    private final ZipkinService zipkinService;
    private final ObjectMapper objectMapper;

    public String ingressData(InputStream inputStream, String filename, String flow, String metadata) throws ObjectStorageException, DeltafiGraphQLException, DeltafiException, DeltafiMetadataException {
        OffsetDateTime now = OffsetDateTime.now();
        String did = UUID.randomUUID().toString();

        ObjectWriteResponse response = objectStorageService.putObject(MINIO_BUCKET, objectName(did, filename), inputStream, -1L);
        ObjectReferenceInput objectReferenceInput = toObjectReferenceInput(response);
        SourceInfoInput sourceInfoInput = SourceInfoInput.newBuilder().filename(filename).flow(flow).metadata(fromMetadataString(metadata)).build();
        IngressInput ingressInput = IngressInput.newBuilder().did(did).sourceInfo(sourceInfoInput).objectReference(objectReferenceInput).created(now).build();

        ingressDeltaFile(ingressInput);

        return did;
    }

    private String objectName(String did, String incomingName) {
        String fileName = Objects.isNull(incomingName) ? "ingress-unknown-incomingName" : "ingress-" + incomingName;
        return did + "/" + fileName;
    }

    public ObjectReferenceInput toObjectReferenceInput(ObjectWriteResponse response) {
        return ObjectReferenceInput.newBuilder()
                .bucket(response.bucket())
                .name(response.object())
                .size(objectStorageService.getObjectSize(response))
                .offset(0L).build();
    }

    public List<KeyValueInput> fromMetadataString(String metadata) throws DeltafiMetadataException {
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

    public KeyValueInput toKeyValueInput(Map.Entry<String, JsonNode> entry) {
        JsonNode node = entry.getValue();
        String value = node.isTextual() ? node.asText() : node.toString();
        return KeyValueInput.newBuilder().key(entry.getKey()).value(value).build();
    }

    private void ingressDeltaFile(IngressInput ingressInput) throws DeltafiException {
        try {
            sendIngressInput(ingressInput);
            logMetrics(ingressInput, "files_in", "bytes_in");
            sendTrace(ingressInput);
        } catch (DeltafiGraphQLException deltafiException) {
            handleUnrecoverableError(ingressInput);
            throw deltafiException;
        } catch (Exception exception) {
            log.error("Ingress failed for event: {}", ingressInput, exception);
            handleUnrecoverableError(ingressInput);
            throw new DeltafiException(exception.getMessage());
        }
    }

    private void sendIngressInput(IngressInput ingressInput) throws DeltafiGraphQLException {
        GraphQLResponse response = graphQLClientService.executeGraphQLQuery(toGraphQlRequest(ingressInput));
        if (response.hasErrors()) {
            throw new DeltafiGraphQLException(response.getErrors().stream().map(GraphQLError::getMessage).collect(Collectors.joining(",")));
        }
    }

    private GraphQLQueryRequest toGraphQlRequest(IngressInput ingressInput) {
        IngressGraphQLQuery ingressGraphQLQuery = IngressGraphQLQuery.newRequest().input(ingressInput).build();
        IngressProjectionRoot projectionRoot = new IngressProjectionRoot().did();
        return new GraphQLQueryRequest(ingressGraphQLQuery, projectionRoot);
    }

    private void sendTrace(IngressInput ingressInput) {
        DeltafiSpan span = zipkinService.createChildSpan(ingressInput.getDid(), INGRESS_ACTION,
                ingressInput.getSourceInfo().getFilename(), ingressInput.getSourceInfo().getFlow(),
                ingressInput.getCreated());
        zipkinService.markSpanComplete(span);
    }

    private void handleUnrecoverableError(IngressInput ingressInput) {
        logMetrics(ingressInput, "files_dropped", "bytes_dropped");
        removeInvalidObject(ingressInput.getObjectReference());
    }

    private void removeInvalidObject(ObjectReferenceInput objectReference) {
        if (log.isWarnEnabled()) {
            log.warn("Removing object: {} from bucket: {}", objectReference.getName(), objectReference.getBucket());
        }
        objectStorageService.removeObject(objectReference.getBucket(), objectReference.getName());
    }

    private void logMetrics(IngressInput ingressInput, String fileMetric, String byteMetric) {
        MetricLogger.logMetric("ingress", ingressInput.getDid(), ingressInput.getSourceInfo().getFlow(), fileMetric, 1,
                Map.of("filename", ingressInput.getSourceInfo().getFilename()));
        MetricLogger.logMetric("ingress", ingressInput.getDid(), ingressInput.getSourceInfo().getFlow(),
                byteMetric, ingressInput.getObjectReference().getSize(),
                Map.of("filename", ingressInput.getSourceInfo().getFilename()));
    }
}