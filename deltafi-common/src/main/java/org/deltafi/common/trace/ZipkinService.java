package org.deltafi.common.trace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PreDestroy;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;

import static java.util.Objects.nonNull;

@Slf4j
public class ZipkinService {
    public static final String ROOT_PARENT_ID = "0";
    public static final String ROOT_SPAN_NAME = "deltafile-flow";
    public static final String SERVER = "SERVER";
    public static final String DID = "did";
    public static final String FLOW = "flow";

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Queue<DeltafiSpan> spansQueue = new ConcurrentLinkedQueue<>();

    private final ZipkinRestClient zipkinRestClient;
    private final ZipkinConfig zipkinConfig;

    public ZipkinService() {
        zipkinRestClient = null;
        zipkinConfig = null;
    }

    public ZipkinService(ZipkinRestClient zipkinRestClient, ZipkinConfig zipkinConfig) {
        this.zipkinRestClient = zipkinRestClient;
        this.zipkinConfig = zipkinConfig;

        if (zipkinConfig.isEnabled()) {
            scheduler.scheduleAtFixedRate(this::sendQueuedSpans, zipkinConfig.getInitialDelayInMilli(), zipkinConfig.getSendFrequencyInMilli(), TimeUnit.MILLISECONDS);
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }

    /**
     * Create a span that represents the entire lifecycle of the given deltaFile.
     * This should be fired when the DeltaFile hits a terminal state.
     *
     * @param did - DeltaFile id
     * @param created - time of the deltaFile creation
     * @param fileName - original fileName
     * @param flow - flow that this deltaFile belongs too
     */
    public void createAndSendRootSpan(String did, OffsetDateTime created, String fileName, String flow) {
        DeltafiSpan rootSpan = DeltafiSpan.newSpanBuilder()
                .traceId(traceId(did))
                .localEndpoint(fileName)
                .timestamp(created.toInstant())
                .id(hexIdFromUuid(did))
                .name(ROOT_SPAN_NAME)
                .parentId(ROOT_PARENT_ID)
                .tags(Map.of(DID, did, FLOW, flow))
                .kind(SERVER)
                .build();
        markSpanComplete(rootSpan);
    }

    /**
     * Create a span that represents the lifecycle of the deltaFile
     * going through the given action.
     *
     * @param did - DeltaFile id
     * @param actionName - Action acting on the deltaFile in this span
     * @param fileName - original fileName
     * @param flow - flow that this deltaFile belongs too
     * @return - the span for the deltaFile going through the action
     */
    public DeltafiSpan createChildSpan(String did, String actionName, String fileName, String flow) {
        return createChildSpan(did, actionName, fileName, flow, null);
    }

    /**
     * Create a span that represents the lifecycle of the deltaFile
     * going through the given action.
     *
     * @param did - DeltaFile id
     * @param actionName - Action acting on the deltaFile in this span
     * @param fileName - original fileName
     * @param flow - flow that this deltaFile belongs too
     * @param created - optional timestamp
     * @return - the span for the deltaFile going through the action
     */
    public DeltafiSpan createChildSpan(String did, String actionName, String fileName, String flow, OffsetDateTime created) {
        DeltafiSpan.Builder builder = DeltafiSpan.newSpanBuilder()
                .localEndpoint(fileName)
                .traceId(traceId(did))
                .id(longToHex(uniqueId()))
                .name(actionName)
                .parentId(hexIdFromUuid(did))
                .tags(Map.of(DID, did, FLOW, flow))
                .kind(SERVER);
        if (created != null) {
            builder.timestamp(created.toInstant());
        }

        return builder.build();
    }

    /**
     * Set the duration of the span based on the current time
     * and send the span to the zipkin backend.
     *
     * @param span - span to finish and send
     */
    public void markSpanComplete(DeltafiSpan span) {
        span.endSpan();
        spansQueue.add(span);
        if (zipkinConfig.isEnabled() && spansQueue.size() >= zipkinConfig.getMaxBatchSize()) {
            sendQueuedSpans();
        }
    }

    public void sendQueuedSpans() {
        if (spansQueue.isEmpty()) {
            return;
        }

        String spansAsJson = spansToJson();
        if (nonNull(spansAsJson) && !spansAsJson.isBlank()) {
            zipkinRestClient.sendSpan(spansAsJson);
        }
    }


    String spansToJson() {
        List<DeltafiSpan> toSend = new ArrayList<>();

        for (int cnt = 0; cnt < zipkinConfig.getMaxBatchSize() && !spansQueue.isEmpty(); cnt++) {
            toSend.add(spansQueue.poll());
        }

        try {
            return objectMapper.writeValueAsString(toSend);
        } catch (JsonProcessingException e) {
            log.error("Failed to send the following spans to Zipkin, {}", toSend, e);
        }
        return null;
    }

    private String traceId(String did) {
        return did.replace("-", "");
    }

    private String hexIdFromUuid(String uuidString) {
        UUID uuid = UUID.fromString(uuidString);
        long mostSignificantBits = uuid.getMostSignificantBits();
        return longToHex(mostSignificantBits);
    }

    private String longToHex(long longValue) {
        return Long.toHexString(longValue);
    }

    private long uniqueId() {
        return ThreadLocalRandom.current().nextLong();
    }

}
