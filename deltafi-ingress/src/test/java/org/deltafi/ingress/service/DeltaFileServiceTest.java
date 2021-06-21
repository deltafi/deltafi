package org.deltafi.ingress.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.client.GraphQLResponse;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import io.minio.messages.Event;
import io.minio.messages.NotificationRecords;
import org.deltafi.common.trace.DeltafiSpan;
import org.deltafi.common.trace.ZipkinService;
import org.deltafi.dgs.generated.types.DeltaFile;
import org.deltafi.dgs.generated.types.ObjectReferenceInput;
import org.deltafi.dgs.generated.types.SourceInfoInput;
import org.deltafi.ingress.domain.IngressInputHolder;
import org.deltafi.ingress.exceptions.DeltafiGraphQLException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static org.deltafi.ingress.service.DeltaFileService.NIFI_ATTRIBUTES_KEY;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class DeltaFileServiceTest {

    public static final String BUCKET_NAME = "incoming";
    public static final String OBJECT_NAME = "objectName";
    public static final long SIZE = 10;

    @InjectMocks
    DeltaFileService deltaFileService;

    @Mock
    GraphQLClientService graphQLClientService;

    @Mock
    MinioService minioService;

    @Mock
    ZipkinService zipkinService;

    @Spy
    @SuppressWarnings("unused")
    ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void processNotification() {
        NotificationRecords notificationRecords = buildMockNotification("{\"filename\":\"stix.json\", \"flow\":\"stix-flow\"}");
        DeltaFile deltaFile = new DeltaFile();
        deltaFile.setDid("abc");

        GraphQLResponse response = Mockito.mock(GraphQLResponse.class);
        Mockito.when(response.hasErrors()).thenReturn(false);
        Mockito.when(graphQLClientService.executeGraphQLQuery(Mockito.any(GraphQLQueryRequest.class), Mockito.any())).thenReturn(response);
        Mockito.when(response.extractValueAsObject(Mockito.eq("ingress"), Mockito.any(TypeRef.class))).thenReturn(deltaFile);
        Mockito.when(zipkinService.createChildSpan(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(DeltafiSpan.newSpanBuilder().build());
        deltaFileService.processNotificationRecords(notificationRecords);

        // All required fields were set, graphQL request should have fired
        Mockito.verify(graphQLClientService).executeGraphQLQuery(Mockito.any(GraphQLQueryRequest.class), Mockito.any());
    }

    @Test
    void buildSourceInfo() {
        Event event = buildMockEvent("{\"filename\":\"stix.json\", \"flow\":\"stix-flow\", \"extra\": \"info\"}");
        SourceInfoInput sourceInfoInput = deltaFileService.buildSourceInfoInput(event);

        assertEquals("stix.json", sourceInfoInput.getFilename());
        assertEquals("stix-flow", sourceInfoInput.getFlow());
        assertEquals(1, sourceInfoInput.getMetadata().size());
        assertEquals("extra", sourceInfoInput.getMetadata().get(0).getKey());
        assertEquals("info", sourceInfoInput.getMetadata().get(0).getValue());
    }

    @Test
    void buildObjectReferenceInput() {
        Event event = buildMockEvent("{\"filename\":\"stix.json\", \"flow\":\"stix-flow\", \"extra\": \"info\"}");
        ObjectReferenceInput objectReferenceInput = deltaFileService.buildObjectReferenceInput(event);

        assertEquals(BUCKET_NAME, objectReferenceInput.getBucket());
        assertEquals(OBJECT_NAME, objectReferenceInput.getName());
        assertEquals(0, objectReferenceInput.getOffset());
        assertEquals(SIZE, objectReferenceInput.getSize());
    }

    @SuppressWarnings("unused")
    static Stream<String> metadata() {
        return Stream.of(null, "{\"extra\":\"no flow attribute\"}", "{\"bad_json\"}");
    }

    @ParameterizedTest
    @MethodSource("metadata")
    void processNotification_missingMetadata(String metaData) {
        NotificationRecords notificationRecords = buildMockNotification(metaData);

        deltaFileService.processNotificationRecords(notificationRecords);

        Mockito.verify(minioService).removeObject(BUCKET_NAME, OBJECT_NAME);
        Mockito.verifyNoInteractions(graphQLClientService);
    }

    @Test
    void processNotificationRecords_null() {
        assertAll(() -> deltaFileService.processNotificationRecords(null));
    }

    @SuppressWarnings("unused")
    static Stream<String> responses() {
        return Stream.of(internalErrorResponse(), multiErrorResponse());
    }

    @ParameterizedTest
    @MethodSource("responses")
    void handleDgsErrorResponse_unrecoverable(String response) {
        GraphQLResponse graphQLResponse = new GraphQLResponse(response);
        IngressInputHolder ingressInputHolder = new IngressInputHolder(buildMockEvent(""));
        deltaFileService.handleDgsResponseErrors(ingressInputHolder, graphQLResponse);

        Mockito.verify(minioService).removeObject(BUCKET_NAME, OBJECT_NAME);
    }

    @Test
    void handleDgsErrorResponse_recoverable() {
        GraphQLResponse graphQLResponse = new GraphQLResponse(unavailableResponse());
        Event event = buildMockEvent("");

        IngressInputHolder ingressInputHolder = new IngressInputHolder(event);
        Assertions.assertThrows(DeltafiGraphQLException.class, () -> deltaFileService.handleDgsResponseErrors(ingressInputHolder, graphQLResponse));
    }

    private static String internalErrorResponse() {
        return "{\n" +
                "  \"errors\": [\n" +
                "    {\n" +
                "      \"message\": \"This custom thing went wrong!\",\n" +
                "      \"locations\": [],\n" +
                "      \"path\": [\n" +
                "        \"hello\"\n" +
                "      ],\n" +
                "      \"extensions\": {\n" +
                "        \"errorType\": \"INTERNAL\",\n" +
                "        \"debugInfo\": {\n" +
                "          \"somefield\": \"somevalue\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  ],\n" +
                "  \"data\": {\n" +
                "    \"hello\": null\n" +
                "  }\n" +
                "}";
    }

    private static String unavailableResponse() {
        return "{\n" +
                "  \"errors\": [\n" +
                "    {\n" +
                "      \"message\": \"This custom thing went wrong!\",\n" +
                "      \"locations\": [],\n" +
                "      \"path\": [\n" +
                "        \"hello\"\n" +
                "      ],\n" +
                "      \"extensions\": {\n" +
                "        \"errorType\": \"UNAVAILABLE\",\n" +
                "        \"debugInfo\": {\n" +
                "          \"somefield\": \"somevalue\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  ],\n" +
                "  \"data\": {\n" +
                "    \"hello\": null\n" +
                "  }\n" +
                "}";
    }

    private static String multiErrorResponse() {
        return "{\n" +
                "  \"errors\": [\n" +
                "    {\n" +
                "      \"message\": \"This custom thing went wrong!\",\n" +
                "      \"locations\": [],\n" +
                "      \"path\": [\n" +
                "        \"hello\"\n" +
                "      ],\n" +
                "      \"extensions\": {\n" +
                "        \"errorType\": \"UNAVAILABLE\",\n" +
                "        \"debugInfo\": {\n" +
                "          \"somefield\": \"somevalue\"\n" +
                "        }\n" +
                "      }\n" +
                "    },\n" +
                "    {\n" +
                "      \"message\": \"This other custom thing went wrong!\",\n" +
                "      \"locations\": [],\n" +
                "      \"path\": [\n" +
                "        \"hello\"\n" +
                "      ],\n" +
                "      \"extensions\": {\n" +
                "        \"errorType\": \"INTERNAL\",\n" +
                "        \"debugInfo\": {\n" +
                "          \"somefield\": \"somevalue\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  ],\n" +
                "  \"data\": {\n" +
                "    \"hello\": null\n" +
                "  }\n" +
                "}";
    }

    private static NotificationRecords buildMockNotification(String userMetaData) {
        NotificationRecords notificationRecords = Mockito.mock(NotificationRecords.class);

        Event event = buildMockEvent(userMetaData);
        lenient().when(notificationRecords.events()).thenReturn(singletonList(event));
        return notificationRecords;
    }

    private static Event buildMockEvent(String userMetadataString) {
        Event event = Mockito.mock(Event.class);
        lenient().when(event.bucketName()).thenReturn(BUCKET_NAME);
        lenient().when(event.objectName()).thenReturn(OBJECT_NAME);
        lenient().when(event.objectSize()).thenReturn(SIZE);

        Map<String, String> userMetadata = Objects.nonNull(userMetadataString) ? Map.of(NIFI_ATTRIBUTES_KEY, userMetadataString) : null;
        lenient().when(event.userMetadata()).thenReturn(userMetadata);
        return event;
    }

}