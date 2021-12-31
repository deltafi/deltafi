package org.deltafi.core.domain.datafetchers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.DeltaFiles;
import org.deltafi.core.domain.configuration.IngressFlowConfiguration;
import org.deltafi.core.domain.exceptions.ActionConfigException;
import org.deltafi.core.domain.generated.DgsConstants;
import org.deltafi.core.domain.generated.client.*;
import org.deltafi.core.domain.generated.types.*;
import org.deltafi.core.domain.services.DeltaFiConfigService;
import org.deltafi.core.domain.services.DeltaFilesService;
import org.deltafi.core.domain.services.RedisService;
import org.deltafi.core.domain.services.StateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.deltafi.core.domain.Util.equalIgnoringDates;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@TestPropertySource(properties = "enableScheduling=false")
class DeltaFilesDatafetcherTest {

    @Autowired
    DgsQueryExecutor dgsQueryExecutor;

    @Autowired
    DeltaFilesService deltaFilesService;

    @Autowired
    StateMachine stateMachine;

    @MockBean
    DeltaFiConfigService deltaFiConfigService;

    @MockBean
    RedisService redisService;

    final String fileType = "theType";
    final String filename = "theFilename";
    final String flow = "theFlow";
    final Long size = 500L;
    final String objectName = "theName";
    final String objectBucket = "theBucket";
    final List<KeyValueInput> metadata = Arrays.asList(new KeyValueInput("k1", "v1"), new KeyValueInput("k2", "v2"));
    final ObjectReferenceInput objectReferenceInput = new ObjectReferenceInput(objectName, objectBucket, 0, size);
    final SourceInfoInput sourceInfoInput = new SourceInfoInput(filename, flow, metadata);
    final String did = UUID.randomUUID().toString();
    final IngressInput ingressInput = new IngressInput(did, sourceInfoInput, objectReferenceInput, OffsetDateTime.now());

    final DeltaFilesProjectionRoot deltaFilesProjectionRoot = new DeltaFilesProjectionRoot()
            .deltaFiles()
                .did()
                .stage()
                .parent()
                .actions()
                .name()
                .created()
                .modified()
                .errorCause()
                .errorContext()
                .state()
                .parent()
                .parent()
                .protocolStack()
                .type()
                .metadata()
                .key()
                .value()
                .parent()
                .objectReference()
                .name()
                .bucket()
                .offset()
                .size()
                .parent()
                .parent()
                .sourceInfo()
                .filename()
                .flow()
                .metadata()
                .key()
                .value()
                .parent()
                .parent()
                .enrichment()
                .key()
                .value()
                .parent()
                .domains()
                .key()
                .value()
                .parent()
                .formattedData()
                .filename()
                .formatAction()
                .objectReference()
                .bucket()
                .offset()
                .name()
                .size()
                .parent()
                .parent()
            .parent()
            .offset()
            .count()
            .totalCount();

    final DeltaFileProjectionRoot deltaFileProjectionRoot = new DeltaFileProjectionRoot()
            .did()
            .stage()
                .parent()
            .actions()
                .name()
                .created()
                .modified()
                .errorCause()
                .errorContext()
                .state()
                    .parent()
                .parent()
            .protocolStack()
              .type()
              .metadata()
                .key()
                .value()
                .parent()
            .objectReference()
                .name()
                .bucket()
                .offset()
                .size()
                .parent()
              .parent()
            .sourceInfo()
              .filename()
              .flow()
              .metadata()
                .key()
                .value()
                .parent()
              .parent()
            .enrichment()
                .key()
                .value()
                .parent()
            .domains()
                .key()
                .value()
                .parent()
            .formattedData()
                .filename()
                .formatAction()
                .objectReference()
                    .bucket()
                    .offset()
                    .name()
                    .size()
                    .parent()
                .parent();

    @BeforeEach
    public void setup() {
        deltaFilesService.deleteAllDeltaFiles();
        IngressFlowConfiguration flowConfiguration = new IngressFlowConfiguration();
        flowConfiguration.setType(fileType);
        Mockito.when(deltaFiConfigService.getIngressFlow(flow)).thenReturn(Optional.of(flowConfiguration));
    }

    @Test
    void addDeltaFile() {
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
                IngressGraphQLQuery.newRequest().input(ingressInput).build(),
                deltaFileProjectionRoot
        );
        DeltaFile deltaFile = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + DgsConstants.MUTATION.Ingress,
                DeltaFile.class);
        assertThat(deltaFile.getDid()).isEqualTo(UUID.fromString(deltaFile.getDid()).toString());
        assertThat(deltaFile.getSourceInfo().getFilename()).isEqualTo(filename);
        assertThat(deltaFile.getSourceInfo().getFlow()).isEqualTo(flow);
        assertThat(deltaFile.getSourceInfo().getMetadata()).isEqualTo(new ObjectMapper().convertValue(metadata, new TypeReference<List<KeyValue>>(){}));
        assertThat(deltaFile.getProtocolStack().get(0).getType()).isEqualTo(fileType);
        assertThat(deltaFile.getProtocolStack().get(0).getObjectReference().getName()).isEqualTo(objectName);
        assertThat(deltaFile.getProtocolStack().get(0).getObjectReference().getBucket()).isEqualTo(objectBucket);
        assertThat(deltaFile.getProtocolStack().get(0).getObjectReference().getOffset()).isZero();
        assertThat(deltaFile.getProtocolStack().get(0).getObjectReference().getSize()).isEqualTo(size);
        assertTrue(deltaFile.getEnrichment().isEmpty());
        assertTrue(deltaFile.getDomains().isEmpty());
        assertTrue(deltaFile.getFormattedData().isEmpty());
    }

    @Test
    void addDeltaFileNoMetadata() {
        sourceInfoInput.setMetadata(Collections.emptyList());

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
                IngressGraphQLQuery.newRequest().input(ingressInput).build(),
                deltaFileProjectionRoot
        );
        DeltaFile deltaFile = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + DgsConstants.MUTATION.Ingress,
                DeltaFile.class);
        assertThat(deltaFile.getDid()).isEqualTo(UUID.fromString(deltaFile.getDid()).toString());
        assertThat(deltaFile.getSourceInfo().getFlow()).isEqualTo(flow);
        assertTrue(deltaFile.getSourceInfo().getMetadata().isEmpty());
        assertThat(deltaFile.getProtocolStack().get(0).getType()).isEqualTo(fileType);
        assertThat(deltaFile.getProtocolStack().get(0).getObjectReference().getName()).isEqualTo(objectName);
        assertThat(deltaFile.getProtocolStack().get(0).getObjectReference().getBucket()).isEqualTo(objectBucket);
        assertThat(deltaFile.getProtocolStack().get(0).getObjectReference().getOffset()).isZero();
        assertThat(deltaFile.getProtocolStack().get(0).getObjectReference().getSize()).isEqualTo(size);
        assertTrue(deltaFile.getEnrichment().isEmpty());
        assertTrue(deltaFile.getDomains().isEmpty());
        assertTrue(deltaFile.getFormattedData().isEmpty());
    }

    @Test
    void deltaFile() {
        DeltaFile expected = deltaFilesService.addDeltaFile(ingressInput);

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
                new DeltaFileGraphQLQuery.Builder().did(expected.getDid()).build(),
                deltaFileProjectionRoot
        );

        DeltaFile actual = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + DgsConstants.QUERY.DeltaFile,
                DeltaFile.class
        );

        assertTrue(equalIgnoringDates(expected, actual));
    }

    @Test
    void deltaFiles() {
        DeltaFiles expected = DeltaFiles.newBuilder()
                .offset(0)
                .count(1)
                .totalCount(1)
                .deltaFiles(List.of(deltaFilesService.addDeltaFile(ingressInput)))
                .build();

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
                new DeltaFilesGraphQLQuery.Builder()
                        .offset(0)
                        .limit(5)
                        .filter(DeltaFilesFilter.newBuilder().createdBefore(OffsetDateTime.now()).build())
                        .orderBy(DeltaFileOrder.newBuilder().field("created").direction(DeltaFileDirection.DESC).build())
                        .build(),
                deltaFilesProjectionRoot
        );

        DeltaFiles actual = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + DgsConstants.QUERY.DeltaFiles,
                DeltaFiles.class
        );

        assertEquals(expected.getOffset(), actual.getOffset());
        assertEquals(expected.getCount(), actual.getCount());
        assertEquals(expected.getTotalCount(), actual.getTotalCount());
        assertTrue(equalIgnoringDates(expected.getDeltaFiles().get(0), actual.getDeltaFiles().get(0)));
    }

    @Test
    void retry() throws ActionConfigException {
        DeltaFile input = deltaFilesService.addDeltaFile(ingressInput);
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
                new RetryGraphQLQuery.Builder()
                        .dids(List.of(input.getDid(), "badDid"))
                        .build(),
                new RetryProjectionRoot().did().success().error()
        );

        List<RetryResult> results = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + DgsConstants.MUTATION.Retry,
                new TypeRef<>() {}
        );

        assertEquals(2, results.size());

        assertEquals(input.getDid(), results.get(0).getDid());
        assertTrue(results.get(0).getSuccess());
        assertNull(results.get(0).getError());

        assertEquals("badDid", results.get(1).getDid());
        assertFalse(results.get(1).getSuccess());
        assertEquals("DeltaFile with did badDid not found", results.get(1).getError());

        Mockito.verify(redisService).enqueue(any(), any());
    }
}