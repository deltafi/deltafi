package org.deltafi.ingress.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.messages.Event;
import io.minio.messages.NotificationRecords;
import io.quarkiverse.loggingjson.providers.KeyValueStructuredArgument;
import org.deltafi.common.metric.Metric;
import org.deltafi.common.metric.MetricBuilder;
import org.deltafi.common.metric.MetricType;
import org.deltafi.common.metric.Tag;
import org.deltafi.common.trace.DeltafiSpan;
import org.deltafi.common.trace.ZipkinService;
import org.deltafi.dgs.generated.types.*;
import org.deltafi.ingress.domain.IngressInputHolder;
import org.deltafi.ingress.exceptions.DeltafiMetadataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

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

    static final String NIFI_ATTRIBUTES_KEY = "X-Amz-Meta-Attributes";
    public static final String INGRESS_ACTION = "IngressAction";

    final RedisService redisService;
    final MinioService minioService;
    final ZipkinService zipkinService;
    final ObjectMapper objectMapper;

    private static final TypeReference<Map<String, String>> KEY_VALUE_LIST_TYPE = new TypeReference<>(){};

    @SuppressWarnings("CdiInjectionPointsInspection")
    public DeltaFileService(RedisService redisService, MinioService minioService, ZipkinService zipkinService, ObjectMapper objectMapper) {
        this.redisService = redisService;
        this.minioService = minioService;
        this.zipkinService = zipkinService;
        this.objectMapper = objectMapper;
    }

    public void processNotificationRecords(NotificationRecords notificationRecords) {
        if (isNull(notificationRecords) || isNull(notificationRecords.events())) {
            return;
        }

        log.debug("Received {} events", notificationRecords.events().size());

        for(Event event : notificationRecords.events()) {
            OffsetDateTime now = OffsetDateTime.now();
            Optional<IngressInputHolder> ingressInputHolder = toIngressInputHolder(event);
            ingressInputHolder.ifPresent(inputHolder -> ingressDeltaFile(inputHolder, now));
        }
    }

    /**
     * Create an entry containing the original event and the graphQL mutation to run ingress.
     * If there is an unrecoverable error, handle the error and return an Optional.empty to
     * skip of this event.
     *
     * @param event - event describing the object added to MinIO
     * @return - wrapper around the original event, SourceInfoInput and ObjectReferenceInput
     */
    private Optional<IngressInputHolder> toIngressInputHolder(Event event) {
        try {
            SourceInfoInput sourceInfoInput = buildSourceInfoInput(event);
            ObjectReferenceInput objectReferenceInput = buildObjectReferenceInput(event);
            return Optional.of(new IngressInputHolder(event, sourceInfoInput, objectReferenceInput));
        } catch (DeltafiMetadataException exception) {
            log.error("Failed to extract metadata, entry will be removed from storage:, {}", event, exception);
            handleUnrecoverableError(new IngressInputHolder(event));
            return Optional.empty();
        }
    }

    private void ingressDeltaFile(IngressInputHolder ingressInputHolder, OffsetDateTime startTime) {
        try {
            redisService.ingress(ingressInputHolder.getIngressInput());
            sendTrace(ingressInputHolder, startTime);
            logInMetrics(ingressInputHolder);
        } catch (Throwable exception) {
            log.error("Ingress failed for event: {}", ingressInputHolder.getEvent(), exception);
            handleUnrecoverableError(ingressInputHolder);
        }
    }

    private void sendTrace(IngressInputHolder ingressInputHolder, OffsetDateTime startTime) {
        DeltafiSpan span = zipkinService.createChildSpan(ingressInputHolder.getIngressInput().getDid(), INGRESS_ACTION, ingressInputHolder.getIngressInput().getSourceInfo().getFilename(), ingressInputHolder.getIngressInput().getSourceInfo().getFlow(), startTime);
        zipkinService.markSpanComplete(span);
    }

    SourceInfoInput buildSourceInfoInput(Event event) {
        Map<String, String> metadata = extractMetadata(event);
        String filename = pullOutFilename(metadata);
        String flow = pullOutFlowName(metadata);

        List<KeyValueInput> metadataList = metadata.entrySet().stream().map(entry -> new KeyValueInput(entry.getKey(), entry.getValue())).collect(Collectors.toList());
        return new SourceInfoInput.Builder()
                .filename(filename)
                .flow(flow)
                .metadata(metadataList).build();
    }

    ObjectReferenceInput buildObjectReferenceInput(Event event) {
        return new ObjectReferenceInput.Builder().bucket(event.bucketName())
                .name(event.objectName()).offset(0).size((int) event.objectSize()).build();
    }

    private void handleUnrecoverableError(IngressInputHolder ingressInputHolder) {
        logDroppedMetrics(ingressInputHolder);
        removeInvalidObject(ingressInputHolder.getEvent());
    }

    private void removeInvalidObject(Event event) {
        if (log.isWarnEnabled()) {
            log.warn("Removing object: {} from bucket: {}", event.objectName(), event.bucketName());
        }
        minioService.removeObject(event.bucketName(), event.objectName());
    }

    private Map<String, String> extractMetadata(Event event) {
        return convertToMetadata(extractMetadataString(event));
    }

    private Map<String, String> convertToMetadata(String attributes) {
        try {
            return objectMapper.readValue(attributes, KEY_VALUE_LIST_TYPE);
        } catch (JsonProcessingException e) {
            throw new DeltafiMetadataException("Metadata was not parsable", e);
        }
    }

    private String extractMetadataString(Event event) {
        if (isNull(event.userMetadata()) || !event.userMetadata().containsKey(NIFI_ATTRIBUTES_KEY)) {
            throw new DeltafiMetadataException("Required event userMetadata key " + NIFI_ATTRIBUTES_KEY + " was missing");
        }
        return event.userMetadata().get(NIFI_ATTRIBUTES_KEY);
    }

    private String pullOutFilename(Map<String, String> metadata) {
        String value = metadata.remove(FILENAME);
        if (isNull(value)) {
            throw new DeltafiMetadataException("Missing the required attribute: " + FILENAME);
        }
        return value;
    }

    private String pullOutFlowName(Map<String, String> metadata) {
        String value = metadata.remove(FLOW);
        if (isNull(value)) {
            throw new DeltafiMetadataException("Missing the required attribute: " + FLOW);
        }
        return value;
    }

    private void logInMetrics(IngressInputHolder ingressInputHolder) {
        Tag filename = getFilenameTag(ingressInputHolder);
        Tag flow = getFlowTag(ingressInputHolder);
        Tag didTag = new Tag("did", ingressInputHolder.getIngressInput().getDid());
        logForMetrics(FILES_IN, 1, flow);
        logForMetrics(BYTES_IN, ingressInputHolder.getEvent().objectSize(), filename, flow, didTag);
    }

    private void logDroppedMetrics(IngressInputHolder ingressInputHolder) {
        Tag filename = getFilenameTag(ingressInputHolder);
        Tag flow = getFlowTag(ingressInputHolder);
        logForMetrics(FILES_DROPPED, 1, flow);
        logForMetrics(BYTES_DROPPED, ingressInputHolder.getEvent().objectSize(), filename, flow);
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

    private Tag getFilenameTag(IngressInputHolder ingressInputHolder) {
        String filename;
        if (Objects.nonNull(ingressInputHolder.getIngressInput()) && Objects.nonNull(ingressInputHolder.getIngressInput().getSourceInfo())) {
            filename = Objects.requireNonNullElse(ingressInputHolder.getIngressInput().getSourceInfo().getFilename(), "UNKNOWN");
        } else {
            filename = "UNKNOWN";
        }

        return new Tag(FILENAME, filename);
    }

    private Tag getFlowTag(IngressInputHolder ingressInputHolder) {
        String flow;
        if (Objects.nonNull(ingressInputHolder.getIngressInput()) && Objects.nonNull(ingressInputHolder.getIngressInput().getSourceInfo())) {
            flow = Objects.requireNonNullElse(ingressInputHolder.getIngressInput().getSourceInfo().getFlow(), "UNKNOWN");
        } else {
            flow = "UNKNOWN";
        }

        return new Tag(FLOW, flow);
    }
}