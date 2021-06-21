package org.deltafi.common.trace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static java.util.Objects.nonNull;

public class ZipkinService {

    private static final Logger log = LoggerFactory.getLogger(ZipkinService.class);

    public static final String ROOT_PARENT_ID = "0";
    public static final String ROOT_SPAN_NAME = "deltafile-flow";
    public static final String SERVER = "SERVER";
    public static final String DID = "did";
    public static final String FLOW = "flow";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ZipkinRestClient zipkinRestClient;
    private final boolean enabled;

    public ZipkinService() {
        zipkinRestClient = null;
        enabled = false;
    }

    public ZipkinService(ZipkinRestClient zipkinRestClient, boolean enabled) {
        this.zipkinRestClient = zipkinRestClient;
        this.enabled = enabled;
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
        finishAndSendSpan(rootSpan);
    }

    /**
     * Create a span that represents the lifecycle of the deltaFile
     * going through the given action.
     *
     * @param did - DeltaFile id
     * @param actionName - Action acting on the deltaFile in this span
     * @param fileName - original fileName
     * @param flow -  flow that this deltaFile belongs too
     * @return - the span for the deltaFile going through the action
     */
    public DeltafiSpan createChildSpan(String did, String actionName, String fileName, String flow) {
        return DeltafiSpan.newSpanBuilder()
                .localEndpoint(fileName)
                .traceId(traceId(did))
                .id(longToHex(uniqueId()))
                .name(actionName)
                .parentId(hexIdFromUuid(did))
                .tags(Map.of(DID, did, FLOW, flow))
                .kind(SERVER)
                .build();
    }

    /**
     * Set the duration of the span based on the current time
     * and send the span to the zipkin backend.
     *
     * @param span - span to finish and send
     */
    public void finishAndSendSpan(DeltafiSpan span) {
        span.endSpan();

        String spanJson = spanToJson(span);
        if (enabled && nonNull(spanJson) && !spanJson.isBlank()) {
            zipkinRestClient.sendSpan(spanJson);
        }
    }


    String spanToJson(DeltafiSpan span) {
        try {
            // zipkin only accepts lists so wrap this in an array first
            return objectMapper.writeValueAsString(Collections.singletonList(span));
        } catch (JsonProcessingException e) {
            log.error("Failed to send {} to Zipkin", span, e);
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
