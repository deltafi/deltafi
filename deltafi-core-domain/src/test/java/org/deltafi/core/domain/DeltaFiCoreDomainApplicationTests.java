/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.deltafi.core.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.resource.Resource;
import org.deltafi.core.domain.api.types.DeleteActionSchema;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.api.types.EgressActionSchema;
import org.deltafi.core.domain.api.types.EnrichActionSchema;
import org.deltafi.core.domain.api.types.FormatActionSchema;
import org.deltafi.core.domain.api.types.LoadActionSchema;
import org.deltafi.core.domain.api.types.TransformActionSchema;
import org.deltafi.core.domain.api.types.ValidateActionSchema;
import org.deltafi.core.domain.api.types.*;
import org.deltafi.core.domain.configuration.EgressActionConfiguration;
import org.deltafi.core.domain.configuration.FormatActionConfiguration;
import org.deltafi.core.domain.configuration.LoadActionConfiguration;
import org.deltafi.core.domain.configuration.TransformActionConfiguration;
import org.deltafi.core.domain.configuration.*;
import org.deltafi.core.domain.datafetchers.FlowPlanDatafetcherTestHelper;
import org.deltafi.core.domain.delete.DeleteRunner;
import org.deltafi.core.domain.generated.DgsConstants;
import org.deltafi.core.domain.generated.client.*;
import org.deltafi.core.domain.generated.types.ConfigType;
import org.deltafi.core.domain.generated.types.DeltaFiConfiguration;
import org.deltafi.core.domain.generated.types.*;
import org.deltafi.core.domain.plugin.Plugin;
import org.deltafi.core.domain.plugin.PluginRepository;
import org.deltafi.core.domain.repo.*;
import org.deltafi.core.domain.services.*;
import org.deltafi.core.domain.types.PluginVariables;
import org.deltafi.core.domain.types.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static graphql.Assert.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.deltafi.common.constant.DeltaFiConstants.INGRESS_ACTION;
import static org.deltafi.common.test.TestConstants.MONGODB_CONTAINER;
import static org.deltafi.core.domain.Util.equalIgnoringDates;
import static org.deltafi.core.domain.datafetchers.ActionSchemaDatafetcherTestHelper.*;
import static org.deltafi.core.domain.datafetchers.DeltaFilesDatafetcherTestHelper.*;
import static org.deltafi.core.domain.plugin.PluginDataFetcherTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;

@SpringBootTest
@TestPropertySource(properties = {"deltafi.deltaFileTtl=3d", "enableScheduling=false"})
@Testcontainers
class DeltaFiCoreDomainApplicationTests {

	@Container
	public static MongoDBContainer mongoDBContainer = new MongoDBContainer(MONGODB_CONTAINER);

	@DynamicPropertySource
	static void setProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
	}

	@Autowired
	DgsQueryExecutor dgsQueryExecutor;

	@Autowired
    DeltaFilesService deltaFilesService;

	@Autowired
    DeltaFiProperties deltaFiProperties;

	@Autowired
    DeleteRunner deleteRunner;

	@Autowired
	DeltaFileRepo deltaFileRepo;

	@Autowired
	ActionSchemaRepo actionSchemaRepo;

	@Autowired
	PluginRepository pluginRepository;

    @Autowired
    IngressFlowPlanService ingressFlowPlanService;

    @Autowired
    IngressFlowService ingressFlowService;

	@Autowired
	IngressFlowRepo ingressFlowRepo;

    @Autowired
    EgressFlowPlanService egressFlowPlanService;

    @Autowired
    EgressFlowService egressFlowService;

	@Autowired
	EgressFlowRepo egressFlowRepo;

	@Autowired
	IngressFlowPlanRepo ingressFlowPlanRepo;

	@Autowired
	EgressFlowPlanRepo egressFlowPlanRepo;

	@Autowired
	PluginVariableRepo pluginVariableRepo;

	@Captor
	ArgumentCaptor<List<ActionInput>> actionInputListCaptor;

	static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

	// mongo eats microseconds, jump through hoops
	private final OffsetDateTime MONGO_NOW =  OffsetDateTime.of(LocalDateTime.ofEpochSecond(OffsetDateTime.now().toInstant().toEpochMilli(), 0, ZoneOffset.UTC), ZoneOffset.UTC);

	@TestConfiguration
	public static class Configuration {
		@Bean
		public RedisService redisService() {
			RedisService redisService = Mockito.mock(RedisService.class);
			try {
				// Allows the ActionEventScheduler to not hold up other scheduled tasks (by default, Spring Boot uses a
				// single thread for all scheduled tasks). Throwing an exception here breaks it out of its tight loop.
				Mockito.when(redisService.dgsFeed()).thenThrow(new RuntimeException());
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			}
			return redisService;
		}
	}

	@Autowired
	RedisService redisService;

	final static List <KeyValue> loadSampleMetadata = Arrays.asList(
			new KeyValue("loadSampleType", "load-sample-type"),
			new KeyValue("loadSampleVersion", "2.2"));
	final static List <KeyValue> loadWrongMetadata = Arrays.asList(
			new KeyValue("loadSampleType", "wrong-sample-type"),
			new KeyValue("loadSampleVersion", "2.2"));
	final static List <KeyValue> transformSampleMetadata = Arrays.asList(
			new KeyValue("sampleType", "sample-type"),
			new KeyValue("sampleVersion", "2.1"));

	@BeforeEach
	void setup() {
		actionSchemaRepo.deleteAll();
		deltaFileRepo.deleteAll();
		deltaFiProperties.getDelete().setOnCompletion(false);
		loadConfig();

		Mockito.clearInvocations(redisService);
	}

	void loadConfig() {
		loadIngressConfig();
		loadEgressConfig();
	}

	void loadIngressConfig() {
		org.deltafi.core.domain.configuration.LoadActionConfiguration lc = new org.deltafi.core.domain.configuration.LoadActionConfiguration();
		lc.setName("SampleLoadAction");
		lc.setConsumes("json-utf8-sample");
		org.deltafi.core.domain.configuration.TransformActionConfiguration tc = new org.deltafi.core.domain.configuration.TransformActionConfiguration();
		tc.setName("Utf8TransformAction");
		TransformActionConfiguration tc2 = new TransformActionConfiguration();
		tc2.setName("SampleTransformAction");

		IngressFlow sampleIngressFlow = buildRunningFlow("sample", lc, List.of(tc, tc2));
		IngressFlow retryFlow = buildRunningFlow("theFlow", lc, null);
		IngressFlow childFlow = buildRunningFlow("childFlow", lc, List.of(tc2));

		ingressFlowRepo.saveAll(List.of(sampleIngressFlow, retryFlow, childFlow));
	}

    void loadEgressConfig() {
		org.deltafi.core.domain.configuration.EnrichActionConfiguration sampleEnrich = new org.deltafi.core.domain.configuration.EnrichActionConfiguration();
		sampleEnrich.setName("SampleEnrichAction");
		sampleEnrich.setRequiresDomains(List.of("sample"));
		sampleEnrich.setRequiresMetadataKeyValues(List.of(new KeyValue("loadSampleType", "load-sample-type")));

		org.deltafi.core.domain.configuration.ValidateActionConfiguration authValidate = new org.deltafi.core.domain.configuration.ValidateActionConfiguration();
		authValidate.setName("AuthorityValidateAction");
		org.deltafi.core.domain.configuration.ValidateActionConfiguration sampleValidate = new org.deltafi.core.domain.configuration.ValidateActionConfiguration();
		sampleValidate.setName("SampleValidateAction");

		org.deltafi.core.domain.configuration.FormatActionConfiguration sampleFormat = new org.deltafi.core.domain.configuration.FormatActionConfiguration();
		sampleFormat.setName("sample.SampleFormatAction");
		sampleFormat.setRequiresDomains(List.of("sample"));
		sampleFormat.setRequiresEnrichment(List.of("sampleEnrichment"));

		org.deltafi.core.domain.configuration.EgressActionConfiguration sampleEgress = new org.deltafi.core.domain.configuration.EgressActionConfiguration();
		sampleEgress.setName("SampleEgressAction");

		EgressFlow sampleEgressFlow = buildRunningFlow("sample", sampleFormat, sampleEgress);
		sampleEgressFlow.setEnrichActions(List.of(sampleEnrich));
		sampleEgressFlow.setValidateActions(List.of(authValidate, sampleValidate));

		FormatActionConfiguration errorFormat = new FormatActionConfiguration();
		errorFormat.setName("ErrorFormatAction");
		errorFormat.setRequiresDomains(List.of("error"));
		EgressActionConfiguration errorEgress = new EgressActionConfiguration();
		errorEgress.setName("ErrorEgressAction");
		EgressFlow errorFlow = buildRunningFlow("error", errorFormat, errorEgress);

		egressFlowRepo.saveAll(List.of(sampleEgressFlow, errorFlow));
    }

	@Test
	void contextLoads() {
		assertTrue(true);
		ConfigQueryInput input = ConfigQueryInput.newBuilder().configType(ConfigType.INGRESS_FLOW).build();
		assertFalse(ingressFlowService.getConfigs(input).isEmpty());
	}

	@Test
	void deletePoliciesScheduled() {
		assertThat(deleteRunner.getDeletePolicies().size()).isEqualTo(1);
		assertThat(deleteRunner.getDeletePolicies().get(0).getName()).isEqualTo("twoSecondsAfterComplete");
	}

	private String graphQL(String filename) throws IOException {
		return new String(Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("full-flow/" + filename + ".graphql")).readAllBytes());
	}

	DeltaFile postIngressDeltaFile(String did) {
		DeltaFile deltaFile = Util.emptyDeltaFile(did, "flow");
		deltaFile.queueAction("Utf8TransformAction");
		deltaFile.setSourceInfo(new SourceInfo("input.txt", "sample", List.of(new KeyValue("AuthorizedBy", "XYZ"))));
		Content content = Content.newBuilder().contentReference(new ContentReference("objectName", 0, 500, did, "application/octet-stream")).build();
		deltaFile.getProtocolStack().add(new ProtocolLayer("json", INGRESS_ACTION, List.of(content), null));
		return deltaFile;
	}

	@Test
	void test01Ingress() throws IOException {
		String did = UUID.randomUUID().toString();
		DeltaFile deltaFileFromDgs = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("01.ingress"), did),
				"data." + DgsConstants.MUTATION.Ingress,
				DeltaFile.class);

		assertEquals(did, deltaFileFromDgs.getDid());

		verifyActionEventResults(postIngressDeltaFile(did), "Utf8TransformAction");
	}

	DeltaFile postTransformUtf8DeltaFile(String did) {
		DeltaFile deltaFile = postIngressDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.INGRESS);
		deltaFile.completeAction("Utf8TransformAction");
		deltaFile.queueAction("SampleTransformAction");
		Content content = Content.newBuilder().name("file.json").contentReference(new ContentReference("utf8ObjectName", 0, 500, did, "application/octet-stream")).build();
		deltaFile.getProtocolStack().add(new ProtocolLayer("json-utf8", "Utf8TransformAction", List.of(content), null));
		return deltaFile;
	}

	@Test
	void test03TransformUtf8() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postIngressDeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("03.transformUtf8"), did),
				"data." + DgsConstants.MUTATION.ActionEvent,
				DeltaFile.class);

		verifyActionEventResults(postTransformUtf8DeltaFile(did), "SampleTransformAction");
	}

	DeltaFile postTransformDeltaFile(String did) {
		DeltaFile deltaFile = postTransformUtf8DeltaFile(did);
		deltaFile.setStage(DeltaFileStage.INGRESS);
		deltaFile.completeAction("SampleTransformAction");
		deltaFile.queueAction("SampleLoadAction");
		Content content = Content.newBuilder().contentReference(new ContentReference("objectName", 0, 500, did, "application/octet-stream")).build();
		deltaFile.getProtocolStack().add(new ProtocolLayer("json-utf8-sample", "SampleTransformAction", List.of(content), transformSampleMetadata));
		return deltaFile;
	}

	@Test
	void test05Transform() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postTransformUtf8DeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("05.transform"), did),
				"data." + DgsConstants.MUTATION.ActionEvent,
				DeltaFile.class);

		verifyActionEventResults(postTransformDeltaFile(did), "SampleLoadAction");
	}

	DeltaFile postTransformHadErrorDeltaFile(String did) {
		DeltaFile deltaFile = postTransformUtf8DeltaFile(did);
		deltaFile.setStage(DeltaFileStage.ERROR);
		deltaFile.errorAction("SampleTransformAction", "transform failed", "message");
		/*
		 * Even though this action is being used as an ERROR, fake its
		 * protocol layer results so that we can verify the State Machine
		 * will still recognize that Transform actions are incomplete,
		 * and not attempt to queue the Load action, too.
		 */
		Content content = Content.newBuilder().contentReference(new ContentReference("objectName", 0, 500, did, "application/octet-stream")).build();
		deltaFile.getProtocolStack().add(new ProtocolLayer("json-utf8-sample", "SampleTransformAction", List.of(content), transformSampleMetadata));
		return deltaFile;
	}

	DeltaFile postRetryTransformDeltaFile(String did) {
		DeltaFile deltaFile = postTransformHadErrorDeltaFile(did);
		deltaFile.retryErrors();
		deltaFile.setStage(DeltaFileStage.INGRESS);
		return deltaFile;
	}

	@Test
	void test06Retry() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postTransformHadErrorDeltaFile(did));

		List<RetryResult> retryResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("06.retry"), did),
				"data." + DgsConstants.MUTATION.Retry,
				new TypeRef<>() {});

		assertEquals(1, retryResults.size());
		assertEquals(did, retryResults.get(0).getDid());
		assertTrue(retryResults.get(0).getSuccess());

		verifyActionEventResults(postRetryTransformDeltaFile(did), "SampleTransformAction");
	}

	DeltaFile postLoadDeltaFile(String did) {
		DeltaFile deltaFile = postTransformDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.EGRESS);
		deltaFile.queueAction("SampleEnrichAction");
		deltaFile.completeAction("SampleLoadAction");
		deltaFile.addDomain("sample", "sampleDomain", "application/octet-stream");
		Content content = Content.newBuilder().contentReference(new ContentReference("objectName", 0, 500, did, "application/octet-stream")).build();
		deltaFile.getProtocolStack().add(new ProtocolLayer("json-utf8-sample-load", "SampleLoadAction", List.of(content), loadSampleMetadata));
		return deltaFile;
	}

	@Test
	void test08Load() throws IOException {
		String did = UUID.randomUUID().toString();
		DeltaFile postTransform = postTransformDeltaFile(did);
		deltaFileRepo.save(postTransform);

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("08.load"), did),
				"data." + DgsConstants.MUTATION.ActionEvent,
				DeltaFile.class);

		verifyActionEventResults(postLoadDeltaFile(did), "SampleEnrichAction");
	}

	DeltaFile post09LoadDeltaFile(String did) {
		DeltaFile deltaFile = postTransformDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.COMPLETE);
		deltaFile.completeAction("SampleLoadAction");
		deltaFile.addDomain("sample", "sampleDomain", "application/octet-stream");
		Content content = Content.newBuilder().contentReference(new ContentReference("objectName", 0, 500, did, "application/octet-stream")).build();
		deltaFile.getProtocolStack().add(new ProtocolLayer("json-utf8-sample-load", "SampleLoadAction", List.of(content), loadWrongMetadata));
		return deltaFile;
	}

	@Test
	void test09LoadWrongMetadata() throws IOException {
		// Test is similar to 08.load, but has the wrong metadata value, which
		// results in the enrich action not being run, and cascades through.
		String did = UUID.randomUUID().toString();
		DeltaFile postTransform = postTransformDeltaFile(did);
		deltaFileRepo.save(postTransform);

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("09.load"), did),
				"data." + DgsConstants.MUTATION.ActionEvent,
				DeltaFile.class);

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertTrue(Util.equalIgnoringDates(post09LoadDeltaFile(did), deltaFile));
	}

	@Test
	void test10Split() throws IOException {
		String did = UUID.randomUUID().toString();
		DeltaFile postTransform = postTransformDeltaFile(did);
		deltaFileRepo.save(postTransform);

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("10.split"), did),
				"data." + DgsConstants.MUTATION.ActionEvent,
				DeltaFile.class);

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertEquals(DeltaFileStage.COMPLETE, deltaFile.getStage());
		assertEquals(2, deltaFile.getChildDids().size());
		assertEquals(ActionState.SPLIT, deltaFile.getActions().get(deltaFile.getActions().size()-1).getState());

		List<DeltaFile> children = deltaFilesService.getDeltaFiles(0, 50, DeltaFilesFilter.newBuilder().dids(deltaFile.getChildDids()).build(), DeltaFileOrder.newBuilder().field("created").direction(DeltaFileDirection.ASC).build()).getDeltaFiles();
		assertEquals(2, children.size());

		DeltaFile child1 = children.get(0);
		assertEquals(DeltaFileStage.INGRESS, child1.getStage());
		assertEquals(Collections.singletonList(deltaFile.getDid()), child1.getParentDids());
		assertEquals("file1", child1.getSourceInfo().getFilename());
		assertEquals(0, child1.getLastProtocolLayerContent().get(0).getContentReference().getOffset());
		assertEquals(2, child1.getLastProtocolLayerContent().size());

		DeltaFile child2 = children.get(1);
		assertEquals(DeltaFileStage.INGRESS, child2.getStage());
		assertEquals(Collections.singletonList(deltaFile.getDid()), child2.getParentDids());
		assertEquals("file2", child2.getSourceInfo().getFilename());
		assertEquals(250, child2.getLastProtocolLayerContent().get(0).getContentReference().getOffset());
		assertEquals(1, child2.getLastProtocolLayerContent().size());

		Mockito.verify(redisService).enqueue(actionInputListCaptor.capture());
		List<ActionInput> actionInputs = actionInputListCaptor.getValue();
		assertThat(actionInputs).hasSize(2);

		Util.assertEqualsIgnoringDates(child1.forQueue("SampleTransformAction"), actionInputs.get(0).getDeltaFile());
		Util.assertEqualsIgnoringDates(child2.forQueue("SampleTransformAction"), actionInputs.get(1).getDeltaFile());
	}

	DeltaFile postEnrichDeltaFile(String did) {
		DeltaFile deltaFile = postLoadDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.EGRESS);
		deltaFile.queueAction("sample.SampleFormatAction");
		deltaFile.completeAction("SampleEnrichAction");
		deltaFile.addEnrichment("sampleEnrichment", "enrichmentData");
		return deltaFile;
	}

	@Test
	void test11Enrich() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postLoadDeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("11.enrich"), did),
				"data." + DgsConstants.MUTATION.ActionEvent,
				DeltaFile.class);

		verifyActionEventResults(postEnrichDeltaFile(did), "sample.SampleFormatAction");
	}

	DeltaFile postFormatDeltaFile(String did) {
		DeltaFile deltaFile = postEnrichDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.EGRESS);
		deltaFile.queueActionsIfNew(Arrays.asList("AuthorityValidateAction", "SampleValidateAction"));
		deltaFile.completeAction("sample.SampleFormatAction");
		deltaFile.getFormattedData().add(FormattedData.newBuilder()
				.formatAction("sample.SampleFormatAction")
				.filename("output.txt")
				.metadata(Arrays.asList(new KeyValue("key1", "value1"), new KeyValue("key2", "value2")))
				.contentReference(new ContentReference("formattedObjectName", 0, 1000, did, "application/octet-stream"))
				.egressActions(Collections.singletonList("SampleEgressAction"))
				.validateActions(List.of("AuthorityValidateAction", "SampleValidateAction"))
				.build());
		return deltaFile;
	}

	@Test
	void test13Format() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postEnrichDeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("13.format"), did),
				"data." + DgsConstants.MUTATION.ActionEvent,
				DeltaFile.class);

		verifyActionEventResults(postFormatDeltaFile(did), "AuthorityValidateAction", "SampleValidateAction");
	}

	@Test
	void test14FormatMany() throws IOException {
		String did = UUID.randomUUID().toString();
		DeltaFile postEnrich = postEnrichDeltaFile(did);
		deltaFileRepo.save(postEnrich);

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("14.formatMany"), did),
				"data." + DgsConstants.MUTATION.ActionEvent,
				DeltaFile.class);

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertEquals(DeltaFileStage.COMPLETE, deltaFile.getStage());
		assertEquals(2, deltaFile.getChildDids().size());
		assertEquals(ActionState.SPLIT, deltaFile.getActions().get(deltaFile.getActions().size()-1).getState());

		List<DeltaFile> children = deltaFilesService.getDeltaFiles(0, 50, DeltaFilesFilter.newBuilder().dids(deltaFile.getChildDids()).build(), DeltaFileOrder.newBuilder().field("created").direction(DeltaFileDirection.ASC).build()).getDeltaFiles();
		assertEquals(2, children.size());

		DeltaFile child1 = children.get(0);
		assertEquals(DeltaFileStage.EGRESS, child1.getStage());
		assertEquals(Collections.singletonList(deltaFile.getDid()), child1.getParentDids());
		assertEquals("input.txt", child1.getSourceInfo().getFilename());
		assertEquals(0, child1.getFormattedData().get(0).getContentReference().getOffset());

		DeltaFile child2 = children.get(1);
		assertEquals(DeltaFileStage.EGRESS, child2.getStage());
		assertEquals(Collections.singletonList(deltaFile.getDid()), child2.getParentDids());
		assertEquals("input.txt", child2.getSourceInfo().getFilename());
		assertEquals(250, child2.getFormattedData().get(0).getContentReference().getOffset());

		Mockito.verify(redisService).enqueue(actionInputListCaptor.capture());
		assertEquals(4, actionInputListCaptor.getValue().size());
		assertEquals(child1.getDid(), actionInputListCaptor.getValue().get(0).getActionContext().getDid());
		assertEquals(child1.getDid(), actionInputListCaptor.getValue().get(1).getActionContext().getDid());
		assertEquals(child2.getDid(), actionInputListCaptor.getValue().get(2).getActionContext().getDid());
		assertEquals(child2.getDid(), actionInputListCaptor.getValue().get(3).getActionContext().getDid());
	}

	DeltaFile postValidateDeltaFile(String did) {
		DeltaFile deltaFile = postFormatDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.EGRESS);
		deltaFile.completeAction("SampleValidateAction");
		return deltaFile;
	}

	@Test
	void test15Validate() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postFormatDeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("15.validate"), did),
				"data." + DgsConstants.MUTATION.ActionEvent,
				DeltaFile.class);

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertTrue(Util.equalIgnoringDates(postValidateDeltaFile(did), deltaFile));

		Mockito.verify(redisService, never()).enqueue(any());
		assertTrue(Util.equalIgnoringDates(postValidateDeltaFile(did), deltaFile));
	}

	DeltaFile postErrorDeltaFile(String did) {
		DeltaFile deltaFile = postValidateDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.ERROR);
		deltaFile.errorAction("AuthorityValidateAction", "Authority XYZ not recognized", "Dead beef feed face cafe");
		return deltaFile;
	}

	@Test
	void test17Error() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postValidateDeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("17.error"), did),
				"data." + DgsConstants.MUTATION.ActionEvent,
				DeltaFile.class);

		DeltaFile actual = deltaFilesService.getDeltaFile(did);
		DeltaFile expected = postErrorDeltaFile(did);
		assertTrue(Util.equalIgnoringDates(expected, actual));

		// ensure an error deltaFile was created
		assertNotEquals(actual, deltaFilesService.getLastCreatedDeltaFiles(1).get(0));

        mockRedisEnqueue("ErrorFormatAction");
	}

	DeltaFile postRetryDeltaFile(String did) {
		DeltaFile deltaFile = postErrorDeltaFile(did);
		deltaFile.retryErrors();
		deltaFile.setStage(DeltaFileStage.EGRESS);
		return deltaFile;
	}

	@Test
	void test18Retry() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postErrorDeltaFile(did));

		List<RetryResult> retryResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("18.retry"), did),
				"data." + DgsConstants.MUTATION.Retry,
				new TypeRef<>() {});

		assertEquals(2, retryResults.size());
		assertEquals(did, retryResults.get(0).getDid());
		assertTrue(retryResults.get(0).getSuccess());
		assertFalse(retryResults.get(1).getSuccess());

		verifyActionEventResults(postRetryDeltaFile(did), "AuthorityValidateAction");
	}

	@Test
	void test19RetryClearsAcknowledged() throws IOException {
		String did = UUID.randomUUID().toString();
		DeltaFile postErrorDeltaFile = postErrorDeltaFile(did);
		postErrorDeltaFile.setErrorAcknowledged(OffsetDateTime.now());
		postErrorDeltaFile.setErrorAcknowledgedReason("some reason");
		deltaFileRepo.save(postErrorDeltaFile);

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("18.retry"), did),
				"data." + DgsConstants.MUTATION.Retry,
				new TypeRef<>() {});

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertNull(deltaFile.getErrorAcknowledged());
		assertNull(deltaFile.getErrorAcknowledgedReason());
	}

	@Test
	void test19Acknowledge() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postErrorDeltaFile(did));

		List<AcknowledgeResult> acknowledgeResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("19.acknowledge"), did),
				"data." + DgsConstants.MUTATION.Acknowledge,
				new TypeRef<>() {});

		assertEquals(2, acknowledgeResults.size());
		assertEquals(did, acknowledgeResults.get(0).getDid());
		assertTrue(acknowledgeResults.get(0).getSuccess());
		assertFalse(acknowledgeResults.get(1).getSuccess());

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertNotNull(deltaFile.getErrorAcknowledged());
		assertEquals("apathy", deltaFile.getErrorAcknowledgedReason());
	}

	DeltaFile postValidateAuthorityDeltaFile(String did) {
		DeltaFile deltaFile = postValidateDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.EGRESS);
		deltaFile.queueAction("SampleEgressAction");
		deltaFile.completeAction("AuthorityValidateAction");
		return deltaFile;
	}

	@Test
	void test20ValidateAuthority() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postValidateDeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("20.validateAuthority"), did),
				"data." + DgsConstants.MUTATION.ActionEvent,
				DeltaFile.class);

		verifyActionEventResults(postValidateAuthorityDeltaFile(did), "SampleEgressAction");
	}

	DeltaFile postEgressDeltaFile(String did) {
		DeltaFile deltaFile = postValidateAuthorityDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.COMPLETE);
		deltaFile.completeAction("SampleEgressAction");
		return deltaFile;
	}

	@Test
	void test22Egress() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postValidateAuthorityDeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("22.egress"), did),
				"data." + DgsConstants.MUTATION.ActionEvent,
				DeltaFile.class);

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertTrue(Util.equalIgnoringDates(postEgressDeltaFile(did), deltaFile));

		Mockito.verify(redisService, never()).enqueue(any());
	}

	@Test
	void testFilterEgress() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postValidateAuthorityDeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("filter"), did, "SampleEgressAction"),
				"data." + DgsConstants.MUTATION.ActionEvent,
				DeltaFile.class);

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		Action lastAction = deltaFile.getActions().get(deltaFile.getActions().size()-1);
		assertEquals("SampleEgressAction", lastAction.getName());
		assertEquals(ActionState.FILTERED, lastAction.getState());
		assertEquals(DeltaFileStage.COMPLETE, deltaFile.getStage());

		Mockito.verify(redisService, never()).enqueue(any());
	}

	@Test
	void test22EgressDeleteCompleted() throws IOException {
		deltaFiProperties.getDelete().setOnCompletion(true);

		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postValidateAuthorityDeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("22.egress"), did),
				"data." + DgsConstants.MUTATION.ActionEvent,
				DeltaFile.class);

        mockRedisEnqueue("DeleteAction");
	}

	@Test
	void setDeltaFileTtl() {
		assertEquals(Duration.ofDays(3), deltaFileRepo.getTtlExpiration());
	}

	@Test
	void testRegisterAll() {
		int count = saveAll(dgsQueryExecutor);
		assertEquals(7, count);

		List<ActionSchema> schemas = getSchemas(dgsQueryExecutor);
		assertEquals(7, schemas.size());

		boolean foundDelete = false;
		boolean foundEgress = false;
		boolean foundEnrich = false;
		boolean foundFormat = false;
		boolean foundLoad = false;
		boolean foundTransform = false;
		boolean foundValidate = false;

		for (ActionSchema schema : schemas) {
			if (schema instanceof DeleteActionSchema) {
				checkDeleteSchema((DeleteActionSchema) schema);
				foundDelete = true;

			} else if (schema instanceof EgressActionSchema) {
				checkEgressSchema((EgressActionSchema) schema);
				foundEgress = true;

			} else if (schema instanceof EnrichActionSchema) {
				checkEnrichSchema((EnrichActionSchema) schema);
				foundEnrich = true;

			} else if (schema instanceof FormatActionSchema) {
				checkFormatSchema((FormatActionSchema) schema);
				foundFormat = true;

			} else if (schema instanceof LoadActionSchema) {
				checkLoadSchema((LoadActionSchema) schema);
				foundLoad = true;

			} else if (schema instanceof TransformActionSchema) {
				checkTransformSchema((TransformActionSchema) schema);
				foundTransform = true;

			} else if (schema instanceof ValidateActionSchema) {
				checkValidateSchema((ValidateActionSchema) schema);
				foundValidate = true;
			}
		}

		assertTrue(foundDelete);
		assertTrue(foundEgress);
		assertTrue(foundEnrich);
		assertTrue(foundFormat);
		assertTrue(foundLoad);
		assertTrue(foundTransform);
		assertTrue(foundValidate);
	}

	private void checkActionCommonFields(ActionSchema schema) {
		assertEquals(PARAM_CLASS, schema.getParamClass());
		assertNotNull(schema.getLastHeard());
	}

	@Test
	void testRegisterDelete() {
		assertEquals(1, saveDelete(dgsQueryExecutor));
		List<ActionSchema> schemas = getSchemas(dgsQueryExecutor);
		assertEquals(1, schemas.size());
		checkDeleteSchema((DeleteActionSchema) schemas.get(0));
	}

	private void checkDeleteSchema(DeleteActionSchema schema) {
		checkActionCommonFields(schema);
		assertEquals(DELETE_ACTION, schema.getId());
	}

	@Test
	void testRegisterEgress() {
		assertEquals(1, saveEgress(dgsQueryExecutor));
		List<ActionSchema> schemas = getSchemas(dgsQueryExecutor);
		assertEquals(1, schemas.size());
		checkEgressSchema((EgressActionSchema) schemas.get(0));
	}

	private void checkEgressSchema(EgressActionSchema schema) {
		checkActionCommonFields(schema);
		assertEquals(EGRESS_ACTION, schema.getId());
	}

	@Test
	void testRegisterEnrich() {
		assertEquals(1, saveEnrich(dgsQueryExecutor));
		List<ActionSchema> schemas = getSchemas(dgsQueryExecutor);
		assertEquals(1, schemas.size());
		checkEnrichSchema((EnrichActionSchema) schemas.get(0));
	}

	private void checkEnrichSchema(EnrichActionSchema schema) {
		checkActionCommonFields(schema);
		assertEquals(ENRICH_ACTION, schema.getId());
		assertEquals(DOMAIN, schema.getRequiresDomains().get(0));
	}

	@Test
	void testRegisterFormat() {
		assertEquals(1, saveFormat(dgsQueryExecutor));
		List<ActionSchema> schemas = getSchemas(dgsQueryExecutor);
		assertEquals(1, schemas.size());
		checkFormatSchema((FormatActionSchema) schemas.get(0));
	}

	private void checkFormatSchema(FormatActionSchema schema) {
		checkActionCommonFields(schema);
		assertEquals(FORMAT_ACTION, schema.getId());
		assertEquals(DOMAIN, schema.getRequiresDomains().get(0));
	}

	@Test
	void testRegisterLoad() {
		assertEquals(1, saveLoad(dgsQueryExecutor));
		List<ActionSchema> schemas = getSchemas(dgsQueryExecutor);
		assertEquals(1, schemas.size());
		checkLoadSchema((LoadActionSchema) schemas.get(0));
	}

	private void checkLoadSchema(LoadActionSchema schema) {
		checkActionCommonFields(schema);
		assertEquals(LOAD_ACTION, schema.getId());
		assertEquals(CONSUMES, schema.getConsumes());
	}

	@Test
	void testRegisterTransform() {
		assertEquals(1, saveTransform(dgsQueryExecutor));
		List<ActionSchema> schemas = getSchemas(dgsQueryExecutor);
		assertEquals(1, schemas.size());
		checkTransformSchema((TransformActionSchema) schemas.get(0));
	}

	private void checkTransformSchema(TransformActionSchema schema) {
		checkActionCommonFields(schema);
		assertEquals(TRANSFORM_ACTION, schema.getId());
		assertEquals(CONSUMES, schema.getConsumes());
		assertEquals(PRODUCES, schema.getProduces());
	}

	@Test
	void testRegisterValidate() {
		assertEquals(1, saveValidate(dgsQueryExecutor));
		List<ActionSchema> schemas = getSchemas(dgsQueryExecutor);
		assertEquals(1, schemas.size());
		checkValidateSchema((ValidateActionSchema) schemas.get(0));
	}

	private void checkValidateSchema(ValidateActionSchema schema) {
		checkActionCommonFields(schema);
		assertEquals(VALIDATE_ACTION, schema.getId());
	}

	@Test
	void findConfigsTest() {
		String name = "SampleLoadAction";

		ConfigQueryInput configQueryInput = ConfigQueryInput.newBuilder().configType(ConfigType.LOAD_ACTION).name(name).build();

		DeltaFiConfigsProjectionRoot projection = new DeltaFiConfigsProjectionRoot()
				.name()
				.apiVersion()
				.onLoadActionConfiguration()
				.consumes()
				.parent();

		DeltaFiConfigsGraphQLQuery findConfig = DeltaFiConfigsGraphQLQuery.newRequest().configQuery(configQueryInput).build();

		TypeRef<List<DeltaFiConfiguration>> listOfConfigs = new TypeRef<>() {
		};
		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(findConfig, projection);
		List<DeltaFiConfiguration> configs = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQLQueryRequest.serialize(),
				"data." + findConfig.getOperationName(),
				listOfConfigs);

		assertTrue(configs.get(0) instanceof org.deltafi.core.domain.generated.types.LoadActionConfiguration);

		org.deltafi.core.domain.generated.types.LoadActionConfiguration loadActionConfiguration = (org.deltafi.core.domain.generated.types.LoadActionConfiguration) configs.get(0);
		assertEquals(name, loadActionConfiguration.getName());
		assertEquals("json-utf8-sample", loadActionConfiguration.getConsumes());
		Assertions.assertNull(loadActionConfiguration.getType()); // not in the projection should be null
	}

	@Test
	void testGetIngressFlowPlan() {
		clearForFlowTests();
		IngressFlowPlan ingressFlowPlanA = new IngressFlowPlan();
		ingressFlowPlanA.setName("ingressPlan");
		IngressFlowPlan ingressFlowPlanB = new IngressFlowPlan();
		ingressFlowPlanB.setName("b");
		ingressFlowPlanRepo.saveAll(List.of(ingressFlowPlanA, ingressFlowPlanB));
		IngressFlowPlan plan = FlowPlanDatafetcherTestHelper.getIngressFlowPlan(dgsQueryExecutor);
		assertThat(plan.getName()).isEqualTo("ingressPlan");
	}

	@Test
	void testGetEgressFlowPlan() {
		clearForFlowTests();
		EgressFlowPlan egressFlowPlanA = new EgressFlowPlan();
		egressFlowPlanA.setName("egressPlan");
		EgressFlowPlan egressFlowPlanB = new EgressFlowPlan();
		egressFlowPlanB.setName("b");
		egressFlowPlanRepo.saveAll(List.of(egressFlowPlanA, egressFlowPlanB));
		EgressFlowPlan plan = FlowPlanDatafetcherTestHelper.getEgressFlowPlan(dgsQueryExecutor);
		assertThat(plan.getName()).isEqualTo("egressPlan");
	}

	@Test
	void testValidateIngressFlow() {
		clearForFlowTests();
		ingressFlowRepo.save(buildIngressFlow(FlowState.STOPPED));
		IngressFlow ingressFlow = FlowPlanDatafetcherTestHelper.validateIngressFlow(dgsQueryExecutor);
		assertThat(ingressFlow.getFlowStatus()).isNotNull();
	}

	@Test
	void testValidateEgressFlow() {
		clearForFlowTests();
		egressFlowRepo.save(buildEgressFlow(FlowState.STOPPED));
		EgressFlow egressFlow = FlowPlanDatafetcherTestHelper.validateEgressFlow(dgsQueryExecutor);
		assertThat(egressFlow.getFlowStatus()).isNotNull();
	}

	@Test
	void testGetFlows() {
		clearForFlowTests();
		pluginRepository.deleteAll();

		PluginCoordinates pluginCoordinates = PluginCoordinates.builder().artifactId("test-actions").groupId("org.deltafi").version("1.0").build();
		Variable var = Variable.newBuilder().name("var").description("description").defaultValue("value").required(false).build();
		PluginVariables variables = new PluginVariables();
		variables.setSourcePlugin(pluginCoordinates);
		variables.setVariables(List.of(var));

		IngressFlow ingressFlow = new IngressFlow();
		ingressFlow.setName("ingress");
		ingressFlow.setSourcePlugin(pluginCoordinates);

		EgressFlow egressFlow = new EgressFlow();
		egressFlow.setName("egress");
		egressFlow.setSourcePlugin(pluginCoordinates);

		Plugin plugin = new Plugin();
		plugin.setPluginCoordinates(pluginCoordinates);
		pluginRepository.save(plugin);
		pluginVariableRepo.save(variables);
		ingressFlowRepo.save(ingressFlow);
		egressFlowRepo.save(egressFlow);

		List<Flows> flows = FlowPlanDatafetcherTestHelper.getFlows(dgsQueryExecutor);
		assertThat(flows).hasSize(1);
		Flows pluginFlows = flows.get(0);
		assertThat(pluginFlows.getSourcePlugin().getArtifactId()).isEqualTo("test-actions");
		assertThat(pluginFlows.getIngressFlows().get(0).getName()).isEqualTo("ingress");
		assertThat(pluginFlows.getEgressFlows().get(0).getName()).isEqualTo("egress");
	}

	@Test
	void getAllFlows() {
		clearForFlowTests();
		IngressFlow ingressFlow = new IngressFlow();
		ingressFlow.setName("ingressFlow");

		EgressFlow egressFlow = new EgressFlow();
		egressFlow.setName("egressFlow");

		ingressFlowRepo.save(ingressFlow);
		egressFlowRepo.save(egressFlow);

		SystemFlows flows = FlowPlanDatafetcherTestHelper.getAllFlows(dgsQueryExecutor);
		assertThat(flows.getIngress()).hasSize(1).matches(ingressFlows -> "ingressFlow".equals(ingressFlows.get(0).getName()));
		assertThat(flows.getEgress()).hasSize(1).matches(egressFlows -> "egressFlow".equals(egressFlows.get(0).getName()));
	}

	@Test
	void getIngressFlow() {
		clearForFlowTests();
		IngressFlow ingressFlow = new IngressFlow();
		ingressFlow.setName("ingressFlow");
		ingressFlowRepo.save(ingressFlow);

		IngressFlow foundFlow = FlowPlanDatafetcherTestHelper.getIngressFlow(dgsQueryExecutor);
		assertThat(foundFlow).isNotNull();
		assertThat(foundFlow.getName()).isEqualTo("ingressFlow");
	}

	@Test
	void getEgressFlow() {
		clearForFlowTests();
		EgressFlow egressFlow = new EgressFlow();
		egressFlow.setName("egressFlow");
		egressFlowRepo.save(egressFlow);
		EgressFlow foundFlow = FlowPlanDatafetcherTestHelper.getEgressFlow(dgsQueryExecutor);
		assertThat(foundFlow).isNotNull();
		assertThat(foundFlow.getName()).isEqualTo("egressFlow");
	}

	@Test
	void getActionNamesByFamily() {
		clearForFlowTests();

		ingressFlowRepo.save(buildIngressFlow(FlowState.STOPPED));
		egressFlowRepo.save(buildEgressFlow(FlowState.STOPPED));

		List<ActionFamily> actionFamilies = FlowPlanDatafetcherTestHelper.getActionFamilies(dgsQueryExecutor);
		assertThat(actionFamilies.size()).isEqualTo(8);

		assertThat(getActionNames(actionFamilies, "ingress")).hasSize(1).contains("IngressAction");
		assertThat(getActionNames(actionFamilies, "delete")).hasSize(1).contains("DeleteAction");
		assertThat(getActionNames(actionFamilies, "transform")).hasSize(2).contains("Utf8TransformAction", "SampleTransformAction");
		assertThat(getActionNames(actionFamilies, "load")).hasSize(1).contains("SampleLoadAction");
		assertThat(getActionNames(actionFamilies, "enrich")).isEmpty();
		assertThat(getActionNames(actionFamilies, "format")).hasSize(1).contains("sample.SampleFormatAction");
		assertThat(getActionNames(actionFamilies, "validate")).isEmpty();
		assertThat(getActionNames(actionFamilies, "egress")).hasSize(1).contains("SampleEgressAction");
	}

	List<String> getActionNames(List<ActionFamily> actionFamilies, String family) {
		return actionFamilies.stream()
				.filter(actionFamily -> family.equals(actionFamily.getFamily()))
				.map(ActionFamily::getActionNames)
				.flatMap(Collection::stream)
				.collect(Collectors.toList());
	}

	@Test
	void testSaveIngressFlowPlan() {
		clearForFlowTests();
		IngressFlow ingressFlow = FlowPlanDatafetcherTestHelper.saveIngressFlowPlan(dgsQueryExecutor);
		assertThat(ingressFlow).isNotNull();
	}

	@Test
	void testSaveEgressFlowPlan() {
		clearForFlowTests();
		EgressFlow egressFlow = FlowPlanDatafetcherTestHelper.saveEgressFlowPlan(dgsQueryExecutor);
		assertThat(egressFlow).isNotNull();
	}

	@Test
	void testRemoveIngressFlowPlan() {
		clearForFlowTests();
		IngressFlowPlan ingressFlowPlan = new IngressFlowPlan();
		ingressFlowPlan.setName("flowPlan");
		ingressFlowPlanRepo.save(ingressFlowPlan);
		assertTrue(FlowPlanDatafetcherTestHelper.removeIngressFlowPlan(dgsQueryExecutor));
	}

	@Test
	void testRemoveEgressFlowPlan() {
		clearForFlowTests();
		EgressFlowPlan egressFlowPlan = new EgressFlowPlan();
		egressFlowPlan.setName("flowPlan");
		egressFlowPlanRepo.save(egressFlowPlan);
		assertTrue(FlowPlanDatafetcherTestHelper.removeEgressFlowPlan(dgsQueryExecutor));
	}

	@Test
	void testStartIngressFlow() {
		clearForFlowTests();
		ingressFlowRepo.save(buildIngressFlow(FlowState.STOPPED));
		assertTrue(FlowPlanDatafetcherTestHelper.startIngressFlow(dgsQueryExecutor));
	}

	@Test
	void testStopIngressFlow() {
		clearForFlowTests();
		ingressFlowRepo.save(buildIngressFlow(FlowState.RUNNING));
		assertTrue(FlowPlanDatafetcherTestHelper.stopIngressFlow(dgsQueryExecutor));
	}

	@Test
	void testStartEgressFlow() {
		clearForFlowTests();
		egressFlowRepo.save(buildEgressFlow(FlowState.STOPPED));
		assertTrue(FlowPlanDatafetcherTestHelper.startEgressFlow(dgsQueryExecutor));
	}

	@Test
	void testStopEgressFlow() {
		clearForFlowTests();
		egressFlowRepo.save(buildEgressFlow(FlowState.RUNNING));
		assertTrue(FlowPlanDatafetcherTestHelper.stopEgressFlow(dgsQueryExecutor));
	}

	@Test
	void testSavePluginVariables() {
		assertTrue(FlowPlanDatafetcherTestHelper.savePluginVariables(dgsQueryExecutor));
	}

	@Test
	void testSetPluginVariableValues() {
		PluginVariables variables = new PluginVariables();
		variables.setSourcePlugin(FlowPlanDatafetcherTestHelper.PLUGIN_COORDINATES);
		variables.setVariables(List.of(Variable.newBuilder().name("key").value("test").description("description").dataType(DATA_TYPE.STRING).build()));
		pluginVariableRepo.save(variables);
		assertTrue(FlowPlanDatafetcherTestHelper.setPluginVariableValues(dgsQueryExecutor));
	}

	@Test
	void addDeltaFile() {
		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
				IngressGraphQLQuery.newRequest().input(INGRESS_INPUT).build(),
				DELTA_FILE_PROJECTION_ROOT
		);
		DeltaFile deltaFile = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQLQueryRequest.serialize(),
				"data." + DgsConstants.MUTATION.Ingress,
				DeltaFile.class);
		assertThat(deltaFile.getDid()).isEqualTo(UUID.fromString(deltaFile.getDid()).toString());
		assertThat(deltaFile.getSourceInfo().getFilename()).isEqualTo(INGRESS_INPUT.getSourceInfo().getFilename());
		assertThat(deltaFile.getSourceInfo().getFlow()).isEqualTo(INGRESS_INPUT.getSourceInfo().getFlow());
		assertThat(deltaFile.getSourceInfo().getMetadata()).isEqualTo(INGRESS_INPUT.getSourceInfo().getMetadata());
		assertThat(deltaFile.getLastProtocolLayerContent().get(0).getContentReference()).isEqualTo(INGRESS_INPUT.getContent().get(0).getContentReference());
		assertTrue(deltaFile.getEnrichment().isEmpty());
		assertTrue(deltaFile.getDomains().isEmpty());
		assertTrue(deltaFile.getFormattedData().isEmpty());
	}

	@Test
	void addDeltaFileNoMetadata() {
		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
				IngressGraphQLQuery.newRequest().input(INGRESS_INPUT_EMPTY_METADATA).build(),
				DELTA_FILE_PROJECTION_ROOT
		);
		DeltaFile deltaFile = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQLQueryRequest.serialize(),
				"data." + DgsConstants.MUTATION.Ingress,
				DeltaFile.class);
		assertThat(deltaFile.getDid()).isEqualTo(UUID.fromString(deltaFile.getDid()).toString());
		assertThat(deltaFile.getSourceInfo().getFlow()).isEqualTo(INGRESS_INPUT.getSourceInfo().getFlow());
		assertTrue(deltaFile.getSourceInfo().getMetadata().isEmpty());
		assertThat(deltaFile.getLastProtocolLayerContent().get(0).getContentReference()).isEqualTo(INGRESS_INPUT.getContent().get(0).getContentReference());
		assertTrue(deltaFile.getEnrichment().isEmpty());
		assertTrue(deltaFile.getDomains().isEmpty());
		assertTrue(deltaFile.getFormattedData().isEmpty());
	}

	@Test
	void deltaFile() {
		DeltaFile expected = deltaFilesService.ingress(INGRESS_INPUT);

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
				new DeltaFileGraphQLQuery.Builder().did(expected.getDid()).build(),
				DELTA_FILE_PROJECTION_ROOT
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
				.deltaFiles(List.of(deltaFilesService.ingress(INGRESS_INPUT)))
				.build();

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
				new DeltaFilesGraphQLQuery.Builder()
						.limit(5)
						.filter(DeltaFilesFilter.newBuilder().createdBefore(OffsetDateTime.now()).build())
						.orderBy(DeltaFileOrder.newBuilder().field("created").direction(DeltaFileDirection.DESC).build())
						.build(),
				DELTA_FILES_PROJECTION_ROOT
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
		DeltaFile input = deltaFilesService.ingress(INGRESS_INPUT);
		DeltaFile second = deltaFilesService.ingress(INGRESS_INPUT_2);

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(input.getDid());
		deltaFile.errorAction("SampleLoadAction", "blah", "blah");
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
		assertEquals(3, afterRetryFile.getActions().size());
		assertEquals(ActionState.COMPLETE, afterRetryFile.getActions().get(0).getState());
		assertEquals(ActionState.RETRIED, afterRetryFile.getActions().get(1).getState());
		assertEquals(ActionState.QUEUED, afterRetryFile.getActions().get(2).getState());
		// StateMachine will queue the failed loadAction again leaving the DeltaFile in the INGRESS stage
		assertEquals(DeltaFileStage.INGRESS, afterRetryFile.getStage());
	}

	@Test
	void getsPlugins() throws IOException {
		pluginRepository.deleteAll();
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), org.deltafi.core.domain.plugin.Plugin.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), org.deltafi.core.domain.plugin.Plugin.class));

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(PluginsGraphQLQuery.newRequest().build(), PLUGINS_PROJECTION_ROOT);

		List<org.deltafi.core.domain.plugin.Plugin> plugins =
				dgsQueryExecutor.executeAndExtractJsonPathAsObject(graphQLQueryRequest.serialize(),
						"data.plugins[*]", new TypeRef<>() {});

		assertEquals(2, plugins.size());

		validatePlugin1(plugins.get(0));
	}

	@Test
	void registersPlugin() throws IOException {
		pluginRepository.deleteAll();
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), org.deltafi.core.domain.plugin.Plugin.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-3.json"), org.deltafi.core.domain.plugin.Plugin.class));

		PluginInput pluginInput = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), PluginInput.class);
		RegisterPluginGraphQLQuery registerPluginGraphQLQuery = RegisterPluginGraphQLQuery.newRequest().pluginInput(pluginInput).build();

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(registerPluginGraphQLQuery, REGISTER_PLUGIN_PROJECTION_ROOT);

		Result result = dgsQueryExecutor.executeAndExtractJsonPathAsObject(graphQLQueryRequest.serialize(),
				"data." + registerPluginGraphQLQuery.getOperationName(), Result.class);

		assertTrue(result.getSuccess());
		List<org.deltafi.core.domain.plugin.Plugin> plugins = pluginRepository.findAll();
		assertEquals(3, plugins.size());
	}

	@Test
	void registerPluginReplacesExistingPlugin() throws IOException {
		pluginRepository.deleteAll();
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), org.deltafi.core.domain.plugin.Plugin.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-3.json"), org.deltafi.core.domain.plugin.Plugin.class));
		org.deltafi.core.domain.plugin.Plugin existingPlugin =
				OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), org.deltafi.core.domain.plugin.Plugin.class);
		existingPlugin.getPluginCoordinates().setVersion("0.0.9");
		existingPlugin.setDescription("changed");
		pluginRepository.save(existingPlugin);

		PluginInput pluginInput = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), PluginInput.class);
		RegisterPluginGraphQLQuery registerPluginGraphQLQuery = RegisterPluginGraphQLQuery.newRequest().pluginInput(pluginInput).build();

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(registerPluginGraphQLQuery, REGISTER_PLUGIN_PROJECTION_ROOT);

		Result result = dgsQueryExecutor.executeAndExtractJsonPathAsObject(graphQLQueryRequest.serialize(),
				"data." + registerPluginGraphQLQuery.getOperationName(), Result.class);

		assertTrue(result.getSuccess());
		List<org.deltafi.core.domain.plugin.Plugin> plugins = pluginRepository.findAll();
		assertEquals(3, plugins.size());
		assertThat(pluginRepository.findById(existingPlugin.getPluginCoordinates())).isEmpty();
		assertThat(pluginRepository.findById(pluginInput.getPluginCoordinates())).isPresent();
	}

	@Test
	void registerPluginReturnsErrorsOnMissingDependencies() throws IOException {
		pluginRepository.deleteAll();

		PluginInput pluginInput = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), PluginInput.class);
		RegisterPluginGraphQLQuery registerPluginGraphQLQuery = RegisterPluginGraphQLQuery.newRequest().pluginInput(pluginInput).build();

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(registerPluginGraphQLQuery, REGISTER_PLUGIN_PROJECTION_ROOT);

		Result result = dgsQueryExecutor.executeAndExtractJsonPathAsObject(graphQLQueryRequest.serialize(),
				"data." + registerPluginGraphQLQuery.getOperationName(), Result.class);

		assertFalse(result.getSuccess());
		assertEquals(2, result.getErrors().size());
		assertTrue(result.getErrors().contains("Plugin dependency not registered: org.deltafi:plugin-2:1.0.0."));
		assertTrue(result.getErrors().contains("Plugin dependency not registered: org.deltafi:plugin-3:1.0.0."));
		List<org.deltafi.core.domain.plugin.Plugin> plugins = pluginRepository.findAll();
		assertTrue(plugins.isEmpty());
	}

	@Test
	void uninstallPluginSuccess() throws IOException {
		pluginRepository.deleteAll();
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), org.deltafi.core.domain.plugin.Plugin.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-3.json"), org.deltafi.core.domain.plugin.Plugin.class));

		PluginInput pluginInput = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), PluginInput.class);
		UninstallPluginGraphQLQuery uninstallPluginGraphQLQuery =
				UninstallPluginGraphQLQuery.newRequest().dryRun(false)
						.pluginCoordinatesInput(pluginInput.getPluginCoordinates()).build();

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(uninstallPluginGraphQLQuery, UNINSTALL_PLUGIN_PROJECTION_ROOT);

		Result result = dgsQueryExecutor.executeAndExtractJsonPathAsObject(graphQLQueryRequest.serialize(),
				"data." + uninstallPluginGraphQLQuery.getOperationName(), Result.class);

		assertTrue(result.getSuccess());
		assertEquals(1, pluginRepository.count());
		Mockito.verify(redisService).dropQueues(List.of("org.deltafi.test.actions.TestAction1", "org.deltafi.test.actions.TestAction2"));
	}

	@Test
	void uninstallPluginDryRun() throws IOException {
		pluginRepository.deleteAll();
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), org.deltafi.core.domain.plugin.Plugin.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-3.json"), org.deltafi.core.domain.plugin.Plugin.class));

		PluginInput pluginInput = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), PluginInput.class);
		UninstallPluginGraphQLQuery uninstallPluginGraphQLQuery =
				UninstallPluginGraphQLQuery.newRequest().dryRun(true)
								.pluginCoordinatesInput(pluginInput.getPluginCoordinates()).build();

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(uninstallPluginGraphQLQuery, UNINSTALL_PLUGIN_PROJECTION_ROOT);

		Result result = dgsQueryExecutor.executeAndExtractJsonPathAsObject(graphQLQueryRequest.serialize(),
				"data." + uninstallPluginGraphQLQuery.getOperationName(), Result.class);

		assertTrue(result.getSuccess());
		assertEquals(2, pluginRepository.count());
	}

	@Test
	void uninstallPluginNotFound() throws IOException {
		pluginRepository.deleteAll();
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), org.deltafi.core.domain.plugin.Plugin.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-3.json"), org.deltafi.core.domain.plugin.Plugin.class));

		PluginInput pluginInput = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), PluginInput.class);
		UninstallPluginGraphQLQuery uninstallPluginGraphQLQuery =
				UninstallPluginGraphQLQuery.newRequest().dryRun(false)
						.pluginCoordinatesInput(pluginInput.getPluginCoordinates()).build();

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(uninstallPluginGraphQLQuery, UNINSTALL_PLUGIN_PROJECTION_ROOT);

		Result result = dgsQueryExecutor.executeAndExtractJsonPathAsObject(graphQLQueryRequest.serialize(),
				"data." + uninstallPluginGraphQLQuery.getOperationName(), Result.class);

		assertFalse(result.getSuccess());
		assertEquals(1, result.getErrors().size());
		assertEquals("Plugin not found", result.getErrors().get(0));
		assertEquals(2, pluginRepository.count());
	}

	@Test
	void uninstallPluginFails() throws IOException {
		pluginRepository.deleteAll();
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), org.deltafi.core.domain.plugin.Plugin.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), org.deltafi.core.domain.plugin.Plugin.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-3.json"), org.deltafi.core.domain.plugin.Plugin.class));

		PluginInput pluginInput = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), PluginInput.class);
		UninstallPluginGraphQLQuery uninstallPluginGraphQLQuery =
				UninstallPluginGraphQLQuery.newRequest().dryRun(false)
						.pluginCoordinatesInput(pluginInput.getPluginCoordinates()).build();

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(uninstallPluginGraphQLQuery, UNINSTALL_PLUGIN_PROJECTION_ROOT);

		Result result = dgsQueryExecutor.executeAndExtractJsonPathAsObject(graphQLQueryRequest.serialize(),
				"data." + uninstallPluginGraphQLQuery.getOperationName(), Result.class);

		assertFalse(result.getSuccess());
		assertEquals(1, result.getErrors().size());
		assertEquals("The following plugins depend on this plugin: org.deltafi:plugin-1:1.0.0", result.getErrors().get(0));
		assertEquals(3, pluginRepository.count());
	}

    @Test
	void findPluginsWithDependency() throws IOException {
		pluginRepository.deleteAll();
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), org.deltafi.core.domain.plugin.Plugin.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), org.deltafi.core.domain.plugin.Plugin.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-3.json"), org.deltafi.core.domain.plugin.Plugin.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-4.json"), org.deltafi.core.domain.plugin.Plugin.class));

		List<Plugin> matched = pluginRepository.findPluginsWithDependency(
				new PluginCoordinates("org.deltafi", "plugin-3", "1.0.0"));
		assertEquals(2, matched.size());
		if (matched.get(0).getDisplayName().equals("Test Plugin 1")) {
			assertEquals("Test Plugin 4", matched.get(1).getDisplayName());
		} else {
			assertEquals("Test Plugin 1", matched.get(1).getDisplayName());
			assertEquals("Test Plugin 4", matched.get(0).getDisplayName());
		}

		matched = pluginRepository.findPluginsWithDependency(
				new PluginCoordinates("org.deltafi", "plugin-2", "1.0.0"));
		assertEquals(1, matched.size());
		assertEquals("Test Plugin 1", matched.get(0).getDisplayName());

		matched = pluginRepository.findPluginsWithDependency(
				new PluginCoordinates("org.deltafi", "plugin-2", "2.0.0"));
		assertEquals(0, matched.size());
	}

	@Test
	void testExpirationIndexUpdate() {
		final Duration newTtlValue = Duration.ofSeconds(123456);

		List<IndexInfo> oldIndexList = deltaFileRepo.getIndexes();
		deltaFileRepo.setExpirationIndex(newTtlValue);
		List<IndexInfo> newIndexList = deltaFileRepo.getIndexes();

		assertEquals(oldIndexList.size(), newIndexList.size());
		assertEquals(newTtlValue.getSeconds(), deltaFileRepo.getTtlExpiration().getSeconds());
	}

	@Test
	void testReadDids() {
		Set<String> dids = Set.of("a", "b", "c");
		List<DeltaFile> deltaFiles = dids.stream().map(Util::buildDeltaFile).collect(Collectors.toList());
		deltaFileRepo.saveAll(deltaFiles);

		Set<String> didsRead = deltaFileRepo.readDids();

		assertEquals(3, didsRead.size());
		assertTrue(didsRead.containsAll(dids));
	}

	@Test
	void testUpdateForRequeue() {
		Action shouldRequeue = Action.newBuilder().name("hit").modified(MONGO_NOW.minusSeconds(1000)).state(ActionState.QUEUED).build();
		Action shouldStay = Action.newBuilder().name("miss").modified(MONGO_NOW.plusSeconds(1000)).state(ActionState.QUEUED).build();

		DeltaFile hit = Util.buildDeltaFile("did", null, null, MONGO_NOW, MONGO_NOW);
		hit.setActions(Arrays.asList(shouldRequeue, shouldStay));
		deltaFileRepo.save(hit);

		DeltaFile miss = Util.buildDeltaFile("did2", null, null, MONGO_NOW, MONGO_NOW);
		miss.setActions(Arrays.asList(shouldStay, shouldStay));
		deltaFileRepo.save(miss);

		List<DeltaFile> hits = deltaFileRepo.updateForRequeue(MONGO_NOW, 30);

		assertEquals(1, hits.size());
		assertEquals(hit.getDid(), hits.get(0).getDid());

		DeltaFile hitAfter = loadDeltaFile("did");
		DeltaFile missAfter = loadDeltaFile("did2");

		assertEquals(miss, missAfter);
		assertNotEquals(hit.getActions().get(0).getModified(), hitAfter.getActions().get(0).getModified());
		assertEquals(hit.getActions().get(1).getModified(), hitAfter.getActions().get(1).getModified());
	}

	@Test
	void deleteByDidIn() {
		List<DeltaFile> deltaFiles = Stream.of("a", "b", "c").map(Util::buildDeltaFile).collect(Collectors.toList());
		deltaFileRepo.saveAll(deltaFiles);

		assertEquals(3, deltaFileRepo.count());

		deltaFileRepo.deleteByDidIn(Arrays.asList("a", "c"));

		assertEquals(1, deltaFileRepo.count());
		assertEquals("b", deltaFileRepo.findAll().get(0).getDid());
	}

	@Test
	void testMarkForDeleteCreatedBefore() {
		DeltaFile deltaFile1 = Util.buildDeltaFile("1", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = Util.buildDeltaFile("2", null, DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = Util.buildDeltaFile("3", null, DeltaFileStage.INGRESS, OffsetDateTime.now().plusSeconds(2), OffsetDateTime.now().plusSeconds(2));
		deltaFileRepo.save(deltaFile3);

		deltaFileRepo.markForDelete(OffsetDateTime.now().plusSeconds(1), null, null, "policy");

		DeltaFile after = loadDeltaFile(deltaFile1.getDid());
		assertEquals(DeltaFileStage.DELETE, after.getStage());
		after = loadDeltaFile(deltaFile2.getDid());
		assertEquals(DeltaFileStage.DELETE, after.getStage());
		after = loadDeltaFile(deltaFile3.getDid());
		assertEquals(DeltaFileStage.INGRESS, after.getStage());
	}

	@Test
	void testMarkForDeleteCompletedBefore() {
		DeltaFile deltaFile1 = Util.buildDeltaFile("1", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = Util.buildDeltaFile("2", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now().plusSeconds(2));
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = Util.buildDeltaFile("3", null, DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile3);

		deltaFileRepo.markForDelete(null, OffsetDateTime.now().plusSeconds(1), null, "policy");

		DeltaFile after = loadDeltaFile(deltaFile1.getDid());
		assertEquals(DeltaFileStage.DELETE, after.getStage());
		after = loadDeltaFile(deltaFile2.getDid());
		assertEquals(DeltaFileStage.COMPLETE, after.getStage());
		after = loadDeltaFile(deltaFile3.getDid());
		assertEquals(DeltaFileStage.ERROR, after.getStage());
	}

	@Test
	void testMarkForDeleteWithFlow() {
		DeltaFile deltaFile1 = Util.buildDeltaFile("1", "a", DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = Util.buildDeltaFile("2", "b", DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile2);

		deltaFileRepo.markForDelete(OffsetDateTime.now().plusSeconds(1), null, "a", "policy");

		DeltaFile after = loadDeltaFile(deltaFile1.getDid());
		assertEquals(DeltaFileStage.DELETE, after.getStage());
		after = loadDeltaFile(deltaFile2.getDid());
		assertEquals(DeltaFileStage.ERROR, after.getStage());
	}

	@Test
	void testMarkForDelete_alreadyMarkedDeleted() {
		OffsetDateTime oneSecondAgo = OffsetDateTime.now().minusSeconds(1);

		DeltaFile deltaFile1 = Util.buildDeltaFile("1", null, DeltaFileStage.DELETE, oneSecondAgo, oneSecondAgo);
		deltaFileRepo.save(deltaFile1);

		deltaFileRepo.markForDelete(OffsetDateTime.now(), null, null, "policy");

		DeltaFile after = loadDeltaFile(deltaFile1.getDid());
		assertEquals(DeltaFileStage.DELETE, after.getStage());
		assertEquals(oneSecondAgo.toEpochSecond(), after.getModified().toEpochSecond());
	}

	@Test
	void testDeltaFiles_all() {
		DeltaFile deltaFile1 = Util.buildDeltaFile("1", null, DeltaFileStage.COMPLETE, MONGO_NOW.minusSeconds(2), MONGO_NOW.minusSeconds(2));
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = Util.buildDeltaFile("2", null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.plusSeconds(2));
		deltaFileRepo.save(deltaFile2);

		DeltaFiles deltaFiles = deltaFileRepo.deltaFiles(null, 50, new DeltaFilesFilter(), null);
		assertEquals(deltaFiles.getDeltaFiles(), List.of(deltaFile2, deltaFile1));
	}

	@Test
	void testDeltaFiles_limit() {
		DeltaFile deltaFile1 = Util.buildDeltaFile("1", null, DeltaFileStage.COMPLETE, MONGO_NOW.minusSeconds(2), MONGO_NOW.minusSeconds(2));
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = Util.buildDeltaFile("2", null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.plusSeconds(2));
		deltaFileRepo.save(deltaFile2);

		DeltaFiles deltaFiles = deltaFileRepo.deltaFiles(null, 1, new DeltaFilesFilter(), null);
		assertEquals(1, deltaFiles.getCount());
		assertEquals(2, deltaFiles.getTotalCount());

		deltaFiles = deltaFileRepo.deltaFiles(null, 2, new DeltaFilesFilter(), null);
		assertEquals(2, deltaFiles.getCount());
		assertEquals(2, deltaFiles.getTotalCount());

		deltaFiles = deltaFileRepo.deltaFiles(null, 100, new DeltaFilesFilter(), null);
		assertEquals(2, deltaFiles.getCount());
		assertEquals(2, deltaFiles.getTotalCount());

		deltaFiles = deltaFileRepo.deltaFiles(1, 100, new DeltaFilesFilter(), null);
		assertEquals(1, deltaFiles.getCount());
		assertEquals(2, deltaFiles.getTotalCount());
	}

	@Test
	void testDeltaFiles_offset() {
		DeltaFile deltaFile1 = Util.buildDeltaFile("1", null, DeltaFileStage.COMPLETE, MONGO_NOW.minusSeconds(2), MONGO_NOW.plusSeconds(2));
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = Util.buildDeltaFile("2", null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));
		deltaFileRepo.save(deltaFile2);

		DeltaFiles deltaFiles = deltaFileRepo.deltaFiles(0, 50, new DeltaFilesFilter(), null);
		assertEquals(0, deltaFiles.getOffset());
		assertEquals(List.of(deltaFile2, deltaFile1), deltaFiles.getDeltaFiles());

		deltaFiles = deltaFileRepo.deltaFiles(1, 50, new DeltaFilesFilter(), null);
		assertEquals(1, deltaFiles.getOffset());
		assertEquals(List.of(deltaFile1), deltaFiles.getDeltaFiles());

		deltaFiles = deltaFileRepo.deltaFiles(2, 50, new DeltaFilesFilter(), null);
		assertEquals(2, deltaFiles.getOffset());
		assertEquals(Collections.emptyList(), deltaFiles.getDeltaFiles());
	}

	@Test
	void testDeltaFiles_sort() {
		DeltaFile deltaFile1 = Util.buildDeltaFile("1", null, DeltaFileStage.COMPLETE, MONGO_NOW.minusSeconds(2), MONGO_NOW.plusSeconds(2));
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = Util.buildDeltaFile("2", null, DeltaFileStage.ERROR, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));
		deltaFileRepo.save(deltaFile2);

		DeltaFiles deltaFiles = deltaFileRepo.deltaFiles(null, 50, new DeltaFilesFilter(),
				DeltaFileOrder.newBuilder().direction(DeltaFileDirection.ASC).field("created").build());
		assertEquals(List.of(deltaFile1, deltaFile2), deltaFiles.getDeltaFiles());

		deltaFiles = deltaFileRepo.deltaFiles(null, 50, new DeltaFilesFilter(),
				DeltaFileOrder.newBuilder().direction(DeltaFileDirection.DESC).field("created").build());
		assertEquals(List.of(deltaFile2, deltaFile1), deltaFiles.getDeltaFiles());

		deltaFiles = deltaFileRepo.deltaFiles(null, 50, new DeltaFilesFilter(),
				DeltaFileOrder.newBuilder().direction(DeltaFileDirection.ASC).field("modified").build());
		assertEquals(List.of(deltaFile2, deltaFile1), deltaFiles.getDeltaFiles());

		deltaFiles = deltaFileRepo.deltaFiles(null, 50, new DeltaFilesFilter(),
				DeltaFileOrder.newBuilder().direction(DeltaFileDirection.DESC).field("modified").build());
		assertEquals(List.of(deltaFile1, deltaFile2), deltaFiles.getDeltaFiles());
	}

	@Test
	void testDeltaFiles_filter() {
		DeltaFile deltaFile1 = Util.buildDeltaFile("1", null, DeltaFileStage.COMPLETE, MONGO_NOW.minusSeconds(2), MONGO_NOW.plusSeconds(2));
		deltaFile1.setDomains(List.of(new Domain("domain1", null, null)));
		deltaFile1.setEnrichment(List.of(new Enrichment("enrichment1", null, null)));
		deltaFile1.setMarkedForDelete(MONGO_NOW);
		deltaFile1.setSourceInfo(new SourceInfo("filename1", "flow1", List.of(new KeyValue("key1", "value1"), new KeyValue("key2", "value2"))));
		deltaFile1.setActions(List.of(Action.newBuilder().name("action1").build()));
		deltaFile1.setFormattedData(List.of(FormattedData.newBuilder().filename("formattedFilename1").formatAction("formatAction1").metadata(List.of(new KeyValue("formattedKey1", "formattedValue1"), new KeyValue("formattedKey2", "formattedValue2"))).egressActions(List.of("EgressAction1", "EgressAction2")).build()));
		deltaFile1.setErrorAcknowledged(MONGO_NOW);
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = Util.buildDeltaFile("2", null, DeltaFileStage.ERROR, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));
		deltaFile2.setDomains(List.of(new Domain("domain1", null, null), new Domain("domain2", null, null)));
		deltaFile2.setEnrichment(List.of(new Enrichment("enrichment1", null, null), new Enrichment("enrichment2", null, null)));
		deltaFile2.setSourceInfo(new SourceInfo("filename2", "flow2", List.of()));
		deltaFile2.setActions(List.of(Action.newBuilder().name("action1").build(), Action.newBuilder().name("action2").build()));
		deltaFile2.setFormattedData(List.of(FormattedData.newBuilder().filename("formattedFilename2").formatAction("formatAction2").egressActions(List.of("EgressAction1")).build()));
		deltaFileRepo.save(deltaFile2);

		testFilter(DeltaFilesFilter.newBuilder().createdAfter(MONGO_NOW).build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().createdBefore(MONGO_NOW).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().domains(Collections.emptyList()).build(), deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().domains(List.of("domain1")).build(), deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().domains(List.of("domain1", "domain2")).build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().enrichment(Collections.emptyList()).build(), deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().enrichment(List.of("enrichment1")).build(), deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().enrichment(List.of("enrichment1", "enrichment2")).build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().isMarkedForDelete(true).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().isMarkedForDelete(false).build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().modifiedAfter(MONGO_NOW).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().modifiedBefore(MONGO_NOW).build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().stage(DeltaFileStage.COMPLETE).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().sourceInfo(SourceInfoFilter.newBuilder().filename("filename1").build()).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().sourceInfo(SourceInfoFilter.newBuilder().flow("flow2").build()).build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().sourceInfo(SourceInfoFilter.newBuilder().metadata(List.of(new KeyValue("key1", "value1"))).build()).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().sourceInfo(SourceInfoFilter.newBuilder().metadata(List.of(new KeyValue("key1", "value1"), new KeyValue("key2", "value2"))).build()).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().sourceInfo(SourceInfoFilter.newBuilder().metadata(List.of(new KeyValue("key1", "value1"), new KeyValue("key2", "value1"))).build()).build());
		testFilter(DeltaFilesFilter.newBuilder().actions(Collections.emptyList()).build(), deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().actions(List.of("action1")).build(), deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().actions(List.of("action1", "action2")).build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().formattedData(FormattedDataFilter.newBuilder().filename("formattedFilename1").build()).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().formattedData(FormattedDataFilter.newBuilder().formatAction("formatAction2").build()).build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().formattedData(FormattedDataFilter.newBuilder().metadata(List.of(new KeyValue("formattedKey1", "formattedValue1"))).build()).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().formattedData(FormattedDataFilter.newBuilder().metadata(List.of(new KeyValue("formattedKey1", "formattedValue1"), new KeyValue("formattedKey2", "formattedValue2"))).build()).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().formattedData(FormattedDataFilter.newBuilder().metadata(List.of(new KeyValue("formattedKey1", "formattedValue1"), new KeyValue("formattedKey2", "formattedValue1"))).build()).build());
		testFilter(DeltaFilesFilter.newBuilder().formattedData(FormattedDataFilter.newBuilder().egressActions(List.of("EgressAction1")).build()).build(), deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().formattedData(FormattedDataFilter.newBuilder().egressActions(List.of("EgressAction1", "EgressAction2")).build()).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().dids(Collections.emptyList()).build(), deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().dids(Collections.singletonList("1")).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().dids(List.of("1", "3")).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().dids(List.of("1", "2")).build(), deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().dids(List.of("3", "4")).build());
		testFilter(DeltaFilesFilter.newBuilder().errorAcknowledged(true).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().errorAcknowledged(false).build(), deltaFile2);
	}

	@Test
	void deleteIngressFlowPlanByPlugin() {
		clearForFlowTests();
		PluginCoordinates pluginToDelete = PluginCoordinates.builder().groupId("group").artifactId("deltafi-actions").version("1.0.0").build();

		IngressFlowPlan ingressFlowPlanA = new IngressFlowPlan();
		ingressFlowPlanA.setName("a");
		ingressFlowPlanA.setSourcePlugin(pluginToDelete);

		IngressFlowPlan ingressFlowPlanB = new IngressFlowPlan();
		ingressFlowPlanB.setName("b");
		ingressFlowPlanB.setSourcePlugin(pluginToDelete);

		IngressFlowPlan ingressFlowPlanC = new IngressFlowPlan();
		ingressFlowPlanC.setName("c");
		ingressFlowPlanC.setSourcePlugin(PluginCoordinates.builder().groupId("group2").artifactId("deltafi-actions").version("1.0.0").build());
		ingressFlowPlanRepo.saveAll(List.of(ingressFlowPlanA, ingressFlowPlanB, ingressFlowPlanC));
		assertThat(ingressFlowPlanRepo.deleteBySourcePlugin(pluginToDelete)).isEqualTo(2);
		assertThat(ingressFlowPlanRepo.count()).isEqualTo(1);
	}

	@Test
	void deleteIngressFlowByPlugin() {
		clearForFlowTests();
		PluginCoordinates pluginToDelete = PluginCoordinates.builder().groupId("group").artifactId("deltafi-actions").version("1.0.0").build();

		IngressFlow ingressFlowA = new IngressFlow();
		ingressFlowA.setName("a");
		ingressFlowA.setSourcePlugin(pluginToDelete);

		IngressFlow ingressFlowB = new IngressFlow();
		ingressFlowB.setName("b");
		ingressFlowB.setSourcePlugin(pluginToDelete);

		IngressFlow ingressFlowC = new IngressFlow();
		ingressFlowC.setName("c");
		ingressFlowC.setSourcePlugin(PluginCoordinates.builder().groupId("group2").artifactId("deltafi-actions").version("1.0.0").build());
		ingressFlowRepo.saveAll(List.of(ingressFlowA, ingressFlowB, ingressFlowC));
		assertThat(ingressFlowRepo.deleteBySourcePlugin(pluginToDelete)).isEqualTo(2);
		assertThat(ingressFlowRepo.count()).isEqualTo(1);
	}

	@Test
	void deleteEgressFlowPlanByPlugin() {
		clearForFlowTests();
		PluginCoordinates pluginToDelete = PluginCoordinates.builder().groupId("group").artifactId("deltafi-actions").version("1.0.0").build();

		EgressFlowPlan egressFlowPlanA = new EgressFlowPlan();
		egressFlowPlanA.setName("a");
		egressFlowPlanA.setSourcePlugin(pluginToDelete);

		EgressFlowPlan egressFlowPlanB = new EgressFlowPlan();
		egressFlowPlanB.setName("b");
		egressFlowPlanB.setSourcePlugin(pluginToDelete);

		EgressFlowPlan egressFlowPlanC = new EgressFlowPlan();
		egressFlowPlanC.setName("c");
		egressFlowPlanC.setSourcePlugin(PluginCoordinates.builder().groupId("group2").artifactId("deltafi-actions").version("1.0.0").build());
		egressFlowPlanRepo.saveAll(List.of(egressFlowPlanA, egressFlowPlanB, egressFlowPlanC));
		assertThat(egressFlowPlanRepo.deleteBySourcePlugin(pluginToDelete)).isEqualTo(2);
		assertThat(egressFlowPlanRepo.count()).isEqualTo(1);
	}

	@Test
	void deleteEgressFlowByPlugin() {
		clearForFlowTests();
		PluginCoordinates pluginToDelete = PluginCoordinates.builder().groupId("group").artifactId("deltafi-actions").version("1.0.0").build();

		EgressFlow egressFlowA = new EgressFlow();
		egressFlowA.setName("a");
		egressFlowA.setSourcePlugin(pluginToDelete);

		EgressFlow egressFlowB = new EgressFlow();
		egressFlowB.setName("b");
		egressFlowB.setSourcePlugin(pluginToDelete);

		EgressFlow egressFlowC = new EgressFlow();
		egressFlowC.setName("c");
		egressFlowC.setSourcePlugin(PluginCoordinates.builder().groupId("group2").artifactId("deltafi-actions").version("1.0.0").build());
		egressFlowRepo.saveAll(List.of(egressFlowA, egressFlowB, egressFlowC));
		assertThat(egressFlowRepo.deleteBySourcePlugin(pluginToDelete)).isEqualTo(2);
		assertThat(egressFlowRepo.count()).isEqualTo(1);
	}

	private void testFilter(DeltaFilesFilter filter, DeltaFile... expected) {
		DeltaFiles deltaFiles = deltaFileRepo.deltaFiles(null, 50, filter, null);
		assertEquals(new ArrayList<>(Arrays.asList(expected)), deltaFiles.getDeltaFiles());
	}

	@Test
	void testDeltaFilesEndpoint() {
		DeltaFile deltaFile1 = Util.buildDeltaFile("1", "flow1", DeltaFileStage.COMPLETE, MONGO_NOW.minusSeconds(2), MONGO_NOW.minusSeconds(2));
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = Util.buildDeltaFile("2", "flow2", DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.plusSeconds(2));
		deltaFileRepo.save(deltaFile2);

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
				DeltaFilesGraphQLQuery.newRequest()
						.filter(new DeltaFilesFilter())
						.limit(50)
						.offset(null)
						.orderBy(null)
						.build(),
				new DeltaFilesProjectionRoot().count().totalCount().offset().deltaFiles().did().sourceInfo().flow().parent());

		DeltaFiles deltaFiles = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQLQueryRequest.serialize(),
				"data." + DgsConstants.QUERY.DeltaFiles,
				new TypeRef<>() {}
		);

		assertEquals(2, deltaFiles.getCount());
		assertEquals(2, deltaFiles.getTotalCount());
		assertEquals(0, deltaFiles.getOffset());
		assertEquals(deltaFile2.getDid(), deltaFiles.getDeltaFiles().get(0).getDid());
		assertEquals(deltaFile2.getSourceInfo().getFlow(), deltaFiles.getDeltaFiles().get(0).getSourceInfo().getFlow());
		assertEquals(deltaFile1.getDid(), deltaFiles.getDeltaFiles().get(1).getDid());
		assertEquals(deltaFile1.getSourceInfo().getFlow(), deltaFiles.getDeltaFiles().get(1).getSourceInfo().getFlow());
	}

	@Test
	void testFindVariablesIgnoringVersion() {
		PluginCoordinates oldVersion = PluginCoordinates.builder().groupId("org").artifactId("deltafi").version("1").build();
		PluginCoordinates newVersion = PluginCoordinates.builder().groupId("org").artifactId("deltafi").version("2").build();
		PluginVariables variables = new PluginVariables();
		variables.setSourcePlugin(oldVersion);

		pluginVariableRepo.save(variables);

		assertThat(pluginVariableRepo.findById(newVersion)).isEmpty();
		assertThat(pluginVariableRepo.findIgnoringVersion(newVersion.getGroupId(), newVersion.getArtifactId())).isPresent().contains(variables);
	}

	private DeltaFile loadDeltaFile(String did) {
		return deltaFileRepo.findById(did).orElse(null);
	}

    private void mockRedisEnqueue(String actionName) {
        Mockito.verify(redisService).enqueue(Mockito.argThat((input) -> 1 == input.size() && nameMatches(input.get(0), actionName)));
    }

	private void verifyActionEventResults(DeltaFile expected, String ... forActions) {
		DeltaFile afterMutation = deltaFilesService.getDeltaFile(expected.getDid());
		assertTrue(Util.equalIgnoringDates(expected, afterMutation));

		Mockito.verify(redisService).enqueue(actionInputListCaptor.capture());
		List<ActionInput> actionInputs = actionInputListCaptor.getValue();
		assertThat(actionInputs).hasSize(forActions.length);
		for (int i = 0; i < forActions.length; i++) {
			ActionInput actionInput = actionInputs.get(i);
			assertThat(actionInput.getActionContext().getName()).isEqualTo(forActions[i]);
			Util.assertEqualsIgnoringDates(expected.forQueue(forActions[i]), actionInput.getDeltaFile());
		}
	}

    private boolean nameMatches(ActionInput actionInput, String wantedName) {
        return Objects.nonNull(actionInput.getActionContext()) && wantedName.equals(actionInput.getActionContext().getName());
    }

	private IngressFlow buildIngressFlow(FlowState flowState) {
		org.deltafi.core.domain.configuration.LoadActionConfiguration lc = new org.deltafi.core.domain.configuration.LoadActionConfiguration();
		lc.setName("SampleLoadAction");
		lc.setConsumes("json-utf8-sample");
		org.deltafi.core.domain.configuration.TransformActionConfiguration tc = new org.deltafi.core.domain.configuration.TransformActionConfiguration();
		tc.setName("Utf8TransformAction");
		TransformActionConfiguration tc2 = new TransformActionConfiguration();
		tc2.setName("SampleTransformAction");

		return buildFlow("ingressFlow", lc, List.of(tc, tc2), flowState);
	}

	private IngressFlow buildRunningFlow(String name, LoadActionConfiguration loadActionConfiguration, List<TransformActionConfiguration> transforms) {
		return buildFlow(name, loadActionConfiguration, transforms, FlowState.RUNNING);
	}

	private IngressFlow buildFlow(String name, LoadActionConfiguration loadActionConfiguration, List<TransformActionConfiguration> transforms, FlowState flowState) {
		IngressFlow ingressFlow = new IngressFlow();
		ingressFlow.setName(name);
		ingressFlow.getFlowStatus().setState(flowState);
		ingressFlow.setType("json");
		ingressFlow.setLoadAction(loadActionConfiguration);
		ingressFlow.setTransformActions(transforms);
		return ingressFlow;
	}

	private EgressFlow buildEgressFlow(FlowState flowState) {
		org.deltafi.core.domain.configuration.FormatActionConfiguration sampleFormat = new org.deltafi.core.domain.configuration.FormatActionConfiguration();
		sampleFormat.setName("sample.SampleFormatAction");
		sampleFormat.setRequiresDomains(List.of("sample"));
		sampleFormat.setRequiresEnrichment(List.of("sampleEnrichment"));

		org.deltafi.core.domain.configuration.EgressActionConfiguration sampleEgress = new org.deltafi.core.domain.configuration.EgressActionConfiguration();
		sampleEgress.setName("SampleEgressAction");

		return buildFlow("egressFlow", sampleFormat, sampleEgress, flowState);
	}

	private EgressFlow buildRunningFlow(String name, FormatActionConfiguration formatAction, EgressActionConfiguration egressAction) {
		return buildFlow(name, formatAction, egressAction, FlowState.RUNNING);
	}

	private EgressFlow buildFlow(String name, FormatActionConfiguration formatAction, EgressActionConfiguration egressAction, FlowState flowState) {
		EgressFlow egressFlow = new EgressFlow();
		egressFlow.setName(name);
		egressFlow.setFormatAction(formatAction);
		egressFlow.setEgressAction(egressAction);
		egressFlow.getFlowStatus().setState(flowState);
		return egressFlow;
	}

	void clearForFlowTests() {
		ingressFlowRepo.deleteAll();
		ingressFlowPlanRepo.deleteAll();
		egressFlowRepo.deleteAll();
		egressFlowPlanRepo.deleteAll();
		pluginVariableRepo.deleteAll();
	}
}
