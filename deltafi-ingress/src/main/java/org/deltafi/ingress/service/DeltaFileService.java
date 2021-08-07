package org.deltafi.ingress.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.graphql.dgs.client.GraphQLError;
import com.netflix.graphql.dgs.client.GraphQLResponse;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import io.minio.ObjectWriteResponse;
import io.quarkiverse.loggingjson.providers.KeyValueStructuredArgument;
import org.deltafi.common.metric.Metric;
import org.deltafi.common.metric.MetricBuilder;
import org.deltafi.common.metric.MetricType;
import org.deltafi.common.metric.Tag;
import org.deltafi.common.trace.DeltafiSpan;
import org.deltafi.common.trace.ZipkinService;
import org.deltafi.dgs.generated.client.IngressGraphQLQuery;
import org.deltafi.dgs.generated.client.IngressProjectionRoot;
import org.deltafi.dgs.generated.types.IngressInput;
import org.deltafi.dgs.generated.types.KeyValueInput;
import org.deltafi.dgs.generated.types.ObjectReferenceInput;
import org.deltafi.dgs.generated.types.SourceInfoInput;
import org.deltafi.ingress.exceptions.DeltafiException;
import org.deltafi.ingress.exceptions.DeltafiGraphQLException;
import org.deltafi.ingress.exceptions.DeltafiMetadataException;
import org.deltafi.ingress.exceptions.DeltafiMinioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class DeltaFileService {

    private static final Logger log = LoggerFactory.getLogger(DeltaFileService.class);

    private static final String FILENAME = "filename";
    private static final String FLOW = "flow";
    public static final String METRIC_KEY = "metric";
    public static final String METRIC_SOURCE = "ingress";
    private static final String FILES_IN = "files_in";
    private static final String BYTES_IN = "bytes_in";
    private static final String FILES_DROPPED = "files_dropped";
    private static final String BYTES_DROPPED = "bytes_dropped";
    public static final String INGRESS_ACTION = "IngressAction";

    final GraphQLClientService graphQLClientService;
    final MinioService minioService;
    final ZipkinService zipkinService;
    final ObjectMapper objectMapper;

    @SuppressWarnings("CdiInjectionPointsInspection")
    public DeltaFileService(GraphQLClientService graphQLClientService, MinioService minioService, ZipkinService zipkinService, ObjectMapper objectMapper) {
        this.graphQLClientService = graphQLClientService;
        this.minioService = minioService;
        this.zipkinService = zipkinService;
        this.objectMapper = objectMapper;
    }

    public String ingressData(InputStream inputStream, String filename, String flow, String metadata) throws DeltafiMinioException, DeltafiGraphQLException, DeltafiException, DeltafiMetadataException {
        OffsetDateTime now = OffsetDateTime.now();
        String did = UUID.randomUUID().toString();

        ObjectWriteResponse response = minioService.putObject(did, filename, inputStream, -1L);
        ObjectReferenceInput objectReferenceInput = toObjectReferenceInput(response);
        SourceInfoInput sourceInfoInput = SourceInfoInput.newBuilder().filename(filename).flow(flow).metadata(fromMetadataString(metadata)).build();
        IngressInput ingressInput = IngressInput.newBuilder().did(did).sourceInfo(sourceInfoInput).objectReference(objectReferenceInput).created(now).build();

        ingressDeltaFile(ingressInput);

        return did;
    }

    public ObjectReferenceInput toObjectReferenceInput(ObjectWriteResponse response) {
        return ObjectReferenceInput.newBuilder()
                .bucket(response.bucket())
                .name(response.object())
                .size(minioService.getObjectSize(response))
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

    public KeyValueInput toKeyValueInput(Map.Entry<String,JsonNode> entry) {
        JsonNode node = entry.getValue();
        String value = node.isTextual() ? node.asText() : node.toString();
        return KeyValueInput.newBuilder().key(entry.getKey()).value(value).build();
    }

    private void ingressDeltaFile(IngressInput ingressInput) throws DeltafiException {
        try {
            sendIngressInput(ingressInput);
            logInMetrics(ingressInput);
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
        DeltafiSpan span = zipkinService.createChildSpan(ingressInput.getDid(), INGRESS_ACTION, getFilename(ingressInput), getFlow(ingressInput), ingressInput.getCreated());
        zipkinService.markSpanComplete(span);
    }

    private void handleUnrecoverableError(IngressInput ingressInput) {
        logDroppedMetrics(ingressInput);
        removeInvalidObject(ingressInput.getObjectReference());
    }

    private void removeInvalidObject(ObjectReferenceInput objectReference) {
        if (log.isWarnEnabled()) {
            log.warn("Removing object: {} from bucket: {}", objectReference.getName(), objectReference.getBucket());
        }
        minioService.removeObject(objectReference.getBucket(), objectReference.getName());
    }


    private void logInMetrics(IngressInput ingressInput) {
        logMetrics(ingressInput, FILES_IN, BYTES_IN);
    }

    private void logDroppedMetrics(IngressInput ingressInput) {
        logMetrics(ingressInput, FILES_DROPPED, BYTES_DROPPED);
    }

    private void logMetrics(IngressInput ingressInput, String fileMetric, String byteMetric) {
        Tag filename = getFilenameTag(ingressInput);
        Tag flow = getFlowTag(ingressInput);
        Tag didTag = new Tag("did", ingressInput.getDid());
        logForMetrics(fileMetric, 1, flow, didTag);
        logForMetrics(byteMetric, ingressInput.getObjectReference().getSize(), filename, flow, didTag);
    }

    private void logForMetrics(String name, long value, Tag... tags) {
        Metric metric = new MetricBuilder()
                .setType(MetricType.COUNTER)
                .setName(name)
                .setValue(value)
                .setSource(METRIC_SOURCE)
                .addTags(tags)
                .createMetric();
        log.info("{}", KeyValueStructuredArgument.kv(METRIC_KEY, metric));
    }

    private Tag getFilenameTag(IngressInput ingressInput) {
        return new Tag(FILENAME, getFilename(ingressInput));
    }

    private Tag getFlowTag(IngressInput ingressInput) {
        return new Tag(FLOW, getFlow(ingressInput));
    }

    private String getFilename(IngressInput ingressInput) {
        return ingressInput.getSourceInfo().getFilename();
    }

    private String getFlow(IngressInput ingressInput) {
        return ingressInput.getSourceInfo().getFlow();
    }
}