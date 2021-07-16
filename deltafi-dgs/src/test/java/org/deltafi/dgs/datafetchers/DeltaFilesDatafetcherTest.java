package org.deltafi.dgs.datafetchers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import org.deltafi.dgs.configuration.IngressFlowConfiguration;
import org.deltafi.dgs.generated.client.ActionEventGraphQLQuery;
import org.deltafi.dgs.generated.types.*;
import org.deltafi.dgs.services.DeltaFiConfigService;
import org.deltafi.dgs.services.StateMachine;
import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.dgs.generated.DgsConstants;
import org.deltafi.dgs.generated.client.DeltaFileGraphQLQuery;
import org.deltafi.dgs.generated.client.DeltaFileProjectionRoot;
import org.deltafi.dgs.services.DeltaFilesService;
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
import static org.deltafi.dgs.Util.equalIgnoringDates;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    final String fileType = "theType";
    final String filename = "theFilename";
    final String flow = "theFlow";
    final Integer size = 500;
    final String objectName = "theName";
    final String objectBucket = "theBucket";
    final List<KeyValueInput> metadata = Arrays.asList(new KeyValueInput("k1", "v1"), new KeyValueInput("k2", "v2"));
    final ObjectReferenceInput objectReferenceInput = new ObjectReferenceInput(objectName, objectBucket, 0, size);
    final SourceInfoInput sourceInfoInput = new SourceInfoInput(filename, flow, metadata);
    final String did = UUID.randomUUID().toString();
    final IngressInput ingressInput = new IngressInput(sourceInfoInput, objectReferenceInput, OffsetDateTime.now());
    final ActionEventInput ingressEvent = ActionEventInput.newBuilder()
            .did(did)
            .action("IngressAction")
            .time(OffsetDateTime.now())
            .type(ActionEventType.INGRESS)
            .ingress(ingressInput)
            .build();

    final DeltaFileProjectionRoot deltaFileProjectionRoot = new DeltaFileProjectionRoot()
            .did()
            .stage()
            .actions()
                .name()
                .created()
                .modified()
                .errorMessage()
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
              .did()
              .enrichmentTypes()
              .parent()
            .domains()
              .did()
              .domainTypes()
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
        IngressFlowConfiguration flowConfiguration = new IngressFlowConfiguration();
        flowConfiguration.setType(fileType);
        Mockito.when(deltaFiConfigService.getIngressFlow(flow)).thenReturn(Optional.of(flowConfiguration));
    }

    @Test
    void addDeltaFile() {
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
                ActionEventGraphQLQuery.newRequest().event(ingressEvent).build(),
                deltaFileProjectionRoot
        );
        DeltaFile deltaFile = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + DgsConstants.MUTATION.ActionEvent,
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
        assertTrue(deltaFile.getEnrichment().getEnrichmentTypes().isEmpty());
        assertTrue(deltaFile.getDomains().getDomainTypes().isEmpty());
        assertTrue(deltaFile.getFormattedData().isEmpty());
    }

    @Test
    void addDeltaFileNoMetadata() {
        sourceInfoInput.setMetadata(Collections.emptyList());

        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
                ActionEventGraphQLQuery.newRequest().event(ingressEvent).build(),
                deltaFileProjectionRoot
        );
        DeltaFile deltaFile = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + DgsConstants.MUTATION.ActionEvent,
                DeltaFile.class);
        assertThat(deltaFile.getDid()).isEqualTo(UUID.fromString(deltaFile.getDid()).toString());
        assertThat(deltaFile.getSourceInfo().getFlow()).isEqualTo(flow);
        assertTrue(deltaFile.getSourceInfo().getMetadata().isEmpty());
        assertThat(deltaFile.getProtocolStack().get(0).getType()).isEqualTo(fileType);
        assertThat(deltaFile.getProtocolStack().get(0).getObjectReference().getName()).isEqualTo(objectName);
        assertThat(deltaFile.getProtocolStack().get(0).getObjectReference().getBucket()).isEqualTo(objectBucket);
        assertThat(deltaFile.getProtocolStack().get(0).getObjectReference().getOffset()).isZero();
        assertThat(deltaFile.getProtocolStack().get(0).getObjectReference().getSize()).isEqualTo(size);
        assertTrue(deltaFile.getEnrichment().getEnrichmentTypes().isEmpty());
        assertTrue(deltaFile.getDomains().getDomainTypes().isEmpty());
        assertTrue(deltaFile.getFormattedData().isEmpty());
    }

    @Test void deltaFile() {
        DeltaFile expected = deltaFilesService.addDeltaFile(ingressEvent);

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
}
