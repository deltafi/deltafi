package org.deltafi.core.domain.datafetchers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import org.deltafi.common.content.ContentReference;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.DeltaFiles;
import org.deltafi.core.domain.api.types.KeyValue;
import org.deltafi.core.domain.api.types.SourceInfo;
import org.deltafi.core.domain.configuration.IngressFlowConfiguration;
import org.deltafi.core.domain.generated.DgsConstants;
import org.deltafi.core.domain.generated.client.*;
import org.deltafi.core.domain.generated.types.*;
import org.deltafi.core.domain.services.DeltaFiConfigService;
import org.deltafi.core.domain.services.DeltaFilesService;
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

@SpringBootTest
@TestPropertySource(properties = {"enableScheduling=false"})
class DeltaFilesDatafetcherTest {

    @Autowired
    DgsQueryExecutor dgsQueryExecutor;

    @Autowired
    DeltaFilesService deltaFilesService;

    @Autowired
    StateMachine stateMachine;

    @MockBean
    DeltaFiConfigService deltaFiConfigService;

    final String fileType = "theType";
    final String filename = "theFilename";
    final String flow = "theFlow";
    final Long size = 500L;
    final String objectUuid = "theUuid";
    final String objectUuid2 = "theUuid2";
    final String did = UUID.randomUUID().toString();
    final String did2 = UUID.randomUUID().toString();
    final List<KeyValue> metadata = Arrays.asList(new KeyValue("k1", "v1"), new KeyValue("k2", "v2"));
    String mediaType = "plain/text";
    final ContentReference contentReference = new ContentReference(objectUuid, 0, size, did, mediaType);
    final ContentReference contentReference2 = new ContentReference(objectUuid2, 0, size, did, mediaType);
    final SourceInfo sourceInfo = new SourceInfo(filename, flow, metadata);
    final IngressInput ingressInput = new IngressInput(did, sourceInfo, contentReference, OffsetDateTime.now());
    final IngressInput ingressInput2 = new IngressInput(did2, sourceInfo, contentReference2, OffsetDateTime.now());

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
                .action()
                .contentReference()
                .uuid()
                .offset()
                .size()
                .did()
                .mediaType()
                .parent()
                .metadata()
                .key()
                .value()
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
                .name()
                .value()
                .mediaType()
                .parent()
                .domains()
                .name()
                .value()
                .mediaType()
                .parent()
                .formattedData()
                .filename()
                .formatAction()
                .contentReference()
                .uuid()
                .offset()
                .size()
                .did()
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
              .action()
              .contentReference()
                  .uuid()
                  .offset()
                  .size()
                  .did()
                  .mediaType()
                  .parent()
              .metadata()
                .key()
                .value()
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
                .name()
                .value()
                .mediaType()
                .parent()
            .domains()
                .name()
                .value()
                .mediaType()
                .parent()
            .formattedData()
                .filename()
                .formatAction()
                .contentReference()
                    .uuid()
                    .offset()
                    .size()
                    .did()
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
        assertThat(deltaFile.getProtocolStack().get(0).getContentReference().getUuid()).isEqualTo(objectUuid);
        assertThat(deltaFile.getProtocolStack().get(0).getContentReference().getOffset()).isZero();
        assertThat(deltaFile.getProtocolStack().get(0).getContentReference().getSize()).isEqualTo(size);
        assertThat(deltaFile.getProtocolStack().get(0).getContentReference().getDid()).isEqualTo(did);
        assertTrue(deltaFile.getEnrichment().isEmpty());
        assertTrue(deltaFile.getDomains().isEmpty());
        assertTrue(deltaFile.getFormattedData().isEmpty());
    }

    @Test
    void addDeltaFileNoMetadata() {
        sourceInfo.setMetadata(Collections.emptyList());

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
        assertThat(deltaFile.getProtocolStack().get(0).getContentReference().getUuid()).isEqualTo(objectUuid);
        assertThat(deltaFile.getProtocolStack().get(0).getContentReference().getOffset()).isZero();
        assertThat(deltaFile.getProtocolStack().get(0).getContentReference().getSize()).isEqualTo(size);
        assertThat(deltaFile.getProtocolStack().get(0).getContentReference().getDid()).isEqualTo(did);
        assertTrue(deltaFile.getEnrichment().isEmpty());
        assertTrue(deltaFile.getDomains().isEmpty());
        assertTrue(deltaFile.getFormattedData().isEmpty());
    }

    @Test
    void deltaFile() {
        DeltaFile expected = deltaFilesService.ingress(ingressInput);

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
                .deltaFiles(List.of(deltaFilesService.ingress(ingressInput)))
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
    void retry() {
        DeltaFile input = deltaFilesService.ingress(ingressInput);
        DeltaFile second = deltaFilesService.ingress(ingressInput2);

        DeltaFile deltaFile = deltaFilesService.getDeltaFile(input.getDid());
        deltaFile.queueNewAction("ActionName");
        deltaFile.errorAction("ActionName", "blah", "blah");
        deltaFilesService.advanceAndSave(deltaFile);

        DeltaFile erroredFile = deltaFilesService.getDeltaFile(input.getDid());
        assertEquals(2, erroredFile.getActions().size());

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
                new RetryGraphQLQuery.Builder()
                        .dids(List.of(input.getDid(), second.getDid(), "badDid"))
                        .build(),
                new RetryProjectionRoot().did().success().error()
        );

        List<RetryResult> results = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + DgsConstants.MUTATION.Retry,
                new TypeRef<>() {}
        );

        assertEquals(3, results.size());

        assertEquals(input.getDid(), results.get(0).getDid());
        assertTrue(results.get(0).getSuccess());
        assertNull(results.get(0).getError());

        assertEquals(second.getDid(), results.get(1).getDid());
        assertFalse(results.get(1).getSuccess());
        assertEquals("DeltaFile with did " + second.getDid() + " had no errors", results.get(1).getError());

        assertEquals("badDid", results.get(2).getDid());
        assertFalse(results.get(2).getSuccess());
        assertEquals("DeltaFile with did badDid not found", results.get(2).getError());

        DeltaFile afterRetryFile = deltaFilesService.getDeltaFile(input.getDid());
        assertEquals(2, afterRetryFile.getActions().size());
        assertEquals(ActionState.COMPLETE, afterRetryFile.getActions().get(0).getState());
        assertEquals(ActionState.RETRIED, afterRetryFile.getActions().get(1).getState());
        // StateMachine won't find any pending (no real flow config), nor any errored actions
        assertEquals(DeltaFileStage.COMPLETE, afterRetryFile.getStage());
    }
}
