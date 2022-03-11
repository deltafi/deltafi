package org.deltafi.core.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.resource.Resource;
import org.deltafi.core.domain.api.types.*;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.client.*;
import org.deltafi.core.domain.generated.types.ConfigType;
import org.deltafi.core.domain.generated.types.DeleteActionSchema;
import org.deltafi.core.domain.generated.types.EgressActionSchema;
import org.deltafi.core.domain.generated.types.EnrichActionSchema;
import org.deltafi.core.domain.generated.types.FlowPlan;
import org.deltafi.core.domain.generated.types.FormatActionSchema;
import org.deltafi.core.domain.generated.types.LoadActionSchema;
import org.deltafi.core.domain.generated.types.TransformActionSchema;
import org.deltafi.core.domain.generated.types.ValidateActionSchema;
import org.deltafi.core.domain.plugin.PluginRegistryService;
import org.deltafi.core.domain.repo.ActionSchemaRepo;
import org.deltafi.core.domain.repo.DeltaFileRepo;
import org.deltafi.core.domain.configuration.DeltaFiProperties;
import org.deltafi.core.domain.delete.DeleteRunner;
import org.deltafi.core.domain.exceptions.ActionConfigException;
import org.deltafi.core.domain.generated.DgsConstants;
import org.deltafi.core.domain.generated.types.*;
import org.deltafi.core.domain.repo.FlowPlanRepo;
import org.deltafi.core.domain.services.DeltaFiConfigService;
import org.deltafi.core.domain.services.DeltaFilesService;
import org.deltafi.core.domain.services.RedisService;
import org.deltafi.core.domain.validation.DeltafiRuntimeConfigurationValidator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
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

import static graphql.Assert.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.deltafi.common.constant.DeltaFiConstants.INGRESS_ACTION;
import static org.deltafi.core.domain.Util.equalIgnoringDates;
import static org.deltafi.core.domain.datafetchers.ActionSchemaDatafetcherTestHelper.*;
import static org.deltafi.core.domain.datafetchers.DeltaFilesDatafetcherTestHelper.*;
import static org.deltafi.core.domain.datafetchers.FlowPlanDatafetcherTestHelper.*;
import static org.deltafi.core.domain.plugin.PluginDataFetcherTestHelper.PLUGINS_PROJECTION_ROOT;
import static org.deltafi.core.domain.plugin.PluginDataFetcherTestHelper.validatePlugin1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.deltafi.common.test.TestConstants.MONGODB_CONTAINER;

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
    DeltaFiConfigService configService;

	@MockBean
	DeltafiRuntimeConfigurationValidator configValidator;

	@Autowired
	ActionSchemaRepo actionSchemaRepo;

	@Autowired
	FlowPlanRepo flowPlanRepo;

	@MockBean
	PluginRegistryService pluginRegistryService;

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
	void setup() throws IOException {
		actionSchemaRepo.deleteAll();
		deltaFileRepo.deleteAll();
		flowPlanRepo.deleteAll();
		deltaFiProperties.getDelete().setOnCompletion(false);
		loadConfig();

		Mockito.clearInvocations(redisService);
	}

	void loadConfig() throws IOException {
		String config = new String(Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("deltafi-config.yaml")).readAllBytes());
		configService.replaceConfig(config);
	}

	@Test
	void contextLoads() {
		assertTrue(true);
		ConfigQueryInput input = ConfigQueryInput.newBuilder().configType(ConfigType.INGRESS_FLOW).build();
		assertFalse(configService.getConfigs(input).isEmpty());
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
	void test01Ingress() throws IOException, ActionConfigException {
		String did = UUID.randomUUID().toString();
		DeltaFile deltaFileFromDgs = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("01.ingress"), did),
				"data." + DgsConstants.MUTATION.Ingress,
				DeltaFile.class);

		assertEquals(did, deltaFileFromDgs.getDid());

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertTrue(Util.equalIgnoringDates(postIngressDeltaFile(did), deltaFile));

		ArgumentCaptor<DeltaFile> actual = ArgumentCaptor.forClass(DeltaFile.class);
		Mockito.verify(redisService).enqueue(eq(Collections.singletonList("Utf8TransformAction")), actual.capture());
		deltaFile = actual.getValue();
		assertTrue(Util.equalIgnoringDates(postIngressDeltaFile(did), deltaFile));
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
	void test03TransformUtf8() throws IOException, ActionConfigException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postIngressDeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("03.transformUtf8"), did),
				"data." + DgsConstants.MUTATION.ActionEvent,
				DeltaFile.class);

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertTrue(Util.equalIgnoringDates(postTransformUtf8DeltaFile(did), deltaFile));

		ArgumentCaptor<DeltaFile> actual = ArgumentCaptor.forClass(DeltaFile.class);
		Mockito.verify(redisService).enqueue(eq(Collections.singletonList("SampleTransformAction")), actual.capture());
		deltaFile = actual.getValue();
		assertTrue(Util.equalIgnoringDates(postTransformUtf8DeltaFile(did), deltaFile));
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
	void test05Transform() throws IOException, ActionConfigException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postTransformUtf8DeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("05.transform"), did),
				"data." + DgsConstants.MUTATION.ActionEvent,
				DeltaFile.class);

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertTrue(Util.equalIgnoringDates(postTransformDeltaFile(did), deltaFile));

		ArgumentCaptor<DeltaFile> actual = ArgumentCaptor.forClass(DeltaFile.class);
		Mockito.verify(redisService).enqueue(eq(Collections.singletonList("SampleLoadAction")), actual.capture());
		deltaFile = actual.getValue();
		assertTrue(Util.equalIgnoringDates(postTransformDeltaFile(did), deltaFile));
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
	void test06Retry() throws IOException, ActionConfigException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postTransformHadErrorDeltaFile(did));

		List<RetryResult> retryResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("06.retry"), did),
				"data." + DgsConstants.MUTATION.Retry,
				new TypeRef<>() {});

		assertEquals(1, retryResults.size());
		assertEquals(did, retryResults.get(0).getDid());
		assertTrue(retryResults.get(0).getSuccess());

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertTrue(Util.equalIgnoringDates(postRetryTransformDeltaFile(did), deltaFile));

		ArgumentCaptor<DeltaFile> actual = ArgumentCaptor.forClass(DeltaFile.class);
		Mockito.verify(redisService).enqueue(eq(Collections.singletonList("SampleTransformAction")), actual.capture());
		deltaFile = actual.getValue();
		assertTrue(Util.equalIgnoringDates(postRetryTransformDeltaFile(did), deltaFile));
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
	void test08Load() throws IOException, ActionConfigException {
		String did = UUID.randomUUID().toString();
		DeltaFile postTransform = postTransformDeltaFile(did);
		deltaFileRepo.save(postTransform);

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("08.load"), did),
				"data." + DgsConstants.MUTATION.ActionEvent,
				DeltaFile.class);

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertTrue(Util.equalIgnoringDates(postLoadDeltaFile(did), deltaFile));

		ArgumentCaptor<DeltaFile> actual = ArgumentCaptor.forClass(DeltaFile.class);
		Mockito.verify(redisService).enqueue(eq(Collections.singletonList("SampleEnrichAction")), actual.capture());
		deltaFile = actual.getValue();
		assertTrue(Util.equalIgnoringDates(postLoadDeltaFile(did), deltaFile));
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

	DeltaFile postEnrichDeltaFile(String did) {
		DeltaFile deltaFile = postLoadDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.EGRESS);
		deltaFile.queueAction("SampleFormatAction");
		deltaFile.completeAction("SampleEnrichAction");
		deltaFile.addEnrichment("sampleEnrichment", "enrichmentData");
		return deltaFile;
	}

	@Test
	void test11Enrich() throws IOException, ActionConfigException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postLoadDeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("11.enrich"), did),
				"data." + DgsConstants.MUTATION.ActionEvent,
				DeltaFile.class);

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertTrue(Util.equalIgnoringDates(postEnrichDeltaFile(did), deltaFile));

		ArgumentCaptor<DeltaFile> actual = ArgumentCaptor.forClass(DeltaFile.class);
		Mockito.verify(redisService).enqueue(eq(Collections.singletonList("SampleFormatAction")), actual.capture());
		deltaFile = actual.getValue();
		assertTrue(Util.equalIgnoringDates(postEnrichDeltaFile(did), deltaFile));
	}

	DeltaFile postFormatDeltaFile(String did) {
		DeltaFile deltaFile = postEnrichDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.EGRESS);
		deltaFile.queueActionsIfNew(Arrays.asList("AuthorityValidateAction", "SampleValidateAction"));
		deltaFile.completeAction("SampleFormatAction");
		deltaFile.getFormattedData().add(FormattedData.newBuilder()
				.formatAction("SampleFormatAction")
				.filename("output.txt")
				.metadata(Arrays.asList(new KeyValue("key1", "value1"), new KeyValue("key2", "value2")))
				.contentReference(new ContentReference("formattedObjectName", 0, 1000, did, "application/octet-stream"))
				.egressActions(Collections.singletonList("SampleEgressAction"))
				.validateActions(List.of("AuthorityValidateAction", "SampleValidateAction"))
				.build());
		return deltaFile;
	}

	@Test
	void test13Format() throws IOException, ActionConfigException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postEnrichDeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("13.format"), did),
				"data." + DgsConstants.MUTATION.ActionEvent,
				DeltaFile.class);

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertTrue(Util.equalIgnoringDates(postFormatDeltaFile(did), deltaFile));

		ArgumentCaptor<DeltaFile> actual = ArgumentCaptor.forClass(DeltaFile.class);
		Mockito.verify(redisService).enqueue(eq(Arrays.asList("AuthorityValidateAction", "SampleValidateAction")), actual.capture());
		deltaFile = actual.getValue();
		assertTrue(Util.equalIgnoringDates(postFormatDeltaFile(did), deltaFile));
	}

	DeltaFile postValidateDeltaFile(String did) {
		DeltaFile deltaFile = postFormatDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.EGRESS);
		deltaFile.completeAction("SampleValidateAction");
		return deltaFile;
	}

	@Test
	void test15Validate() throws IOException, ActionConfigException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postFormatDeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("15.validate"), did),
				"data." + DgsConstants.MUTATION.ActionEvent,
				DeltaFile.class);

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertTrue(Util.equalIgnoringDates(postValidateDeltaFile(did), deltaFile));

		Mockito.verify(redisService, never()).enqueue(any(), any());
		assertTrue(Util.equalIgnoringDates(postValidateDeltaFile(did), deltaFile));
	}

	DeltaFile postErrorDeltaFile(String did) {
		DeltaFile deltaFile = postValidateDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.ERROR);
		deltaFile.errorAction("AuthorityValidateAction", "Authority XYZ not recognized", "Dead beef feed face cafe");
		return deltaFile;
	}

	@Test
	void test17Error() throws IOException, ActionConfigException {
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

		Mockito.verify(redisService).enqueue(eq(Collections.singletonList("ErrorFormatAction")), any());
	}

	DeltaFile postRetryDeltaFile(String did) {
		DeltaFile deltaFile = postErrorDeltaFile(did);
		deltaFile.retryErrors();
		deltaFile.setStage(DeltaFileStage.EGRESS);
		return deltaFile;
	}

	@Test
	void test18Retry() throws IOException, ActionConfigException {
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

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertTrue(Util.equalIgnoringDates(postRetryDeltaFile(did), deltaFile));

		ArgumentCaptor<DeltaFile> actual = ArgumentCaptor.forClass(DeltaFile.class);
		Mockito.verify(redisService).enqueue(eq(Collections.singletonList("AuthorityValidateAction")), actual.capture());
		deltaFile = actual.getValue();
		assertTrue(Util.equalIgnoringDates(postRetryDeltaFile(did), deltaFile));
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
	void test20ValidateAuthority() throws IOException, ActionConfigException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postValidateDeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("20.validateAuthority"), did),
				"data." + DgsConstants.MUTATION.ActionEvent,
				DeltaFile.class);

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertTrue(Util.equalIgnoringDates(postValidateAuthorityDeltaFile(did), deltaFile));

		ArgumentCaptor<DeltaFile> actual = ArgumentCaptor.forClass(DeltaFile.class);
		Mockito.verify(redisService).enqueue(eq(Collections.singletonList("SampleEgressAction")), actual.capture());
		deltaFile = actual.getValue();
		assertTrue(Util.equalIgnoringDates(postValidateAuthorityDeltaFile(did), deltaFile));
	}

	DeltaFile postEgressDeltaFile(String did) {
		DeltaFile deltaFile = postValidateAuthorityDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.COMPLETE);
		deltaFile.completeAction("SampleEgressAction");
		return deltaFile;
	}

	@Test
	void test22Egress() throws IOException, ActionConfigException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postValidateAuthorityDeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("22.egress"), did),
				"data." + DgsConstants.MUTATION.ActionEvent,
				DeltaFile.class);

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertTrue(Util.equalIgnoringDates(postEgressDeltaFile(did), deltaFile));

		Mockito.verify(redisService, never()).enqueue(any(), any());
	}

	@Test
	void testFilterEgress() throws IOException, ActionConfigException {
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

		Mockito.verify(redisService, never()).enqueue(any(), any());
	}

	@Test
	void test22EgressDeleteCompleted() throws IOException, ActionConfigException {
		deltaFiProperties.getDelete().setOnCompletion(true);

		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postValidateAuthorityDeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("22.egress"), did),
				"data." + DgsConstants.MUTATION.ActionEvent,
				DeltaFile.class);

		Mockito.verify(redisService).enqueue(Mockito.eq(Collections.singletonList("DeleteAction")), Mockito.any());
	}

	@Test
	void test25ActionConfigError() throws IOException, ActionConfigException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postIngressDeltaFile(did));

		Mockito.doThrow(new ActionConfigException("SampleTransformAction", "action not found")).when(redisService).enqueue(eq(Collections.singletonList("SampleTransformAction")), argThat((d) -> did.equals(d.getDid())));
		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("03.transformUtf8"), did),
				"data." + DgsConstants.MUTATION.ActionEvent,
				DeltaFile.class).getDid();

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertThat(DeltaFileStage.ERROR).isEqualTo(deltaFile.getStage());

		Action errored = deltaFile.actionNamed("SampleTransformAction").orElseThrow();
		assertThat(errored.getErrorCause()).isEqualTo("action not found");
	}

	@Test
	void setDeltaFileTtl() {
		assertEquals(Duration.ofDays(3), deltaFileRepo.getTtlExpiration());
	}

	@Test
	void testRegisterDelete() {
		DeleteActionSchema schema = saveDelete(dgsQueryExecutor);
		assertEquals(DELETE_ACTION, schema.getId());
		assertEquals(PARAM_CLASS, schema.getParamClass());
		assertNotNull(schema.getLastHeard());
	}

	@Test
	void testRegisterEgress() {
		EgressActionSchema schema = saveEgress(dgsQueryExecutor);
		assertEquals(EGRESS_ACTION, schema.getId());
		assertEquals(PARAM_CLASS, schema.getParamClass());
		assertNotNull(schema.getLastHeard());
	}

	@Test
	void testRegisterEnrich() {
		EnrichActionSchema schema = saveEnrich(dgsQueryExecutor);
		assertEquals(ENRICH_ACTION, schema.getId());
		assertEquals(PARAM_CLASS, schema.getParamClass());
		assertNotNull(schema.getLastHeard());
		assertEquals(DOMAIN, schema.getRequiresDomains().get(0));
	}

	@Test
	void testRegisterFormat() {
		FormatActionSchema schema = saveFormat(dgsQueryExecutor);
		assertEquals(FORMAT_ACTION, schema.getId());
		assertEquals(PARAM_CLASS, schema.getParamClass());
		assertNotNull(schema.getLastHeard());
		assertEquals(DOMAIN, schema.getRequiresDomains().get(0));
	}

	@Test
	void testRegisterLoad() {
		LoadActionSchema schema = saveLoad(dgsQueryExecutor);
		assertEquals(LOAD_ACTION, schema.getId());
		assertEquals(PARAM_CLASS, schema.getParamClass());
		assertNotNull(schema.getLastHeard());
		assertEquals(CONSUMES, schema.getConsumes());
	}

	@Test
	void testRegisterTransform() {
		TransformActionSchema schema = saveTransform(dgsQueryExecutor);
		assertEquals(TRANSFORM_ACTION, schema.getId());
		assertEquals(PARAM_CLASS, schema.getParamClass());
		assertNotNull(schema.getLastHeard());
		assertEquals(CONSUMES, schema.getConsumes());
		assertEquals(PRODUCES, schema.getProduces());
	}

	@Test
	void testRegisterValidate() {
		ValidateActionSchema schema = saveValidate(dgsQueryExecutor);
		assertEquals(VALIDATE_ACTION, schema.getId());
		assertEquals(PARAM_CLASS, schema.getParamClass());
		assertNotNull(schema.getLastHeard());
	}

	@Test
	void testGetAll() {
		saveEgress(dgsQueryExecutor);
		saveFormat(dgsQueryExecutor);
		saveLoad(dgsQueryExecutor);
		assertEquals(3, actionSchemaRepo.count());

		List<ActionSchema> schemas = getSchemas(dgsQueryExecutor);
		assertEquals(3, schemas.size());

		boolean foundEgress = false;
		boolean foundFormat = false;
		boolean foundLoad = false;

		for (ActionSchema schema : schemas) {
			if (schema instanceof EgressActionSchema) {
				foundEgress = true;
				EgressActionSchema e = (EgressActionSchema) schema;
				assertEquals(EGRESS_ACTION, e.getId());
			} else if (schema instanceof FormatActionSchema) {
				foundFormat = true;
				FormatActionSchema f = (FormatActionSchema) schema;
				assertEquals(FORMAT_ACTION, f.getId());
				assertEquals(DOMAIN, f.getRequiresDomains().get(0));
			} else if (schema instanceof LoadActionSchema) {
				foundLoad = true;
				LoadActionSchema l = (LoadActionSchema) schema;
				assertEquals(LOAD_ACTION, l.getId());
				assertEquals(CONSUMES, l.getConsumes());
			}
		}
		assertTrue(foundEgress);
		assertTrue(foundFormat);
		assertTrue(foundLoad);
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

		assertTrue(configs.get(0) instanceof LoadActionConfiguration);

		LoadActionConfiguration loadActionConfiguration = (LoadActionConfiguration) configs.get(0);
		assertEquals(name, loadActionConfiguration.getName());
		assertEquals("json-utf8-sample", loadActionConfiguration.getConsumes());
		Assertions.assertNull(loadActionConfiguration.getType()); // not in the projection should be null
	}

	@Test
	void deleteConfigsTest() {
		RemoveDeltaFiConfigsGraphQLQuery remove = RemoveDeltaFiConfigsGraphQLQuery.newRequest().build();
		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(remove, null);
		Integer removed = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQLQueryRequest.serialize(),
				"data." + remove.getOperationName(),
				Integer.class);
		assertEquals(15, removed.intValue());
	}

	@Test
	void testDeleteFlowPlan() {
		saveFlowPlan("planA", false, dgsQueryExecutor);
		saveFlowPlan("planB", true, dgsQueryExecutor);
		saveFlowPlan("planA", true, dgsQueryExecutor);

		assertEquals(2, flowPlanRepo.count());
		assertTrue(removeFlowPlan("planB", dgsQueryExecutor));
		assertEquals(1, flowPlanRepo.count());
		assertEquals("planA", getFlowPlans(dgsQueryExecutor).get(0).getName());
	}

	@Test
	void testExportFlowPlan() throws IOException {
		saveFlowPlan("planA", false, dgsQueryExecutor);
		saveFlowPlan("planB", true, dgsQueryExecutor);

		String exportedJson = exportFlowPlan("planA", dgsQueryExecutor);
		String expected = Resource.read("/flowPlans/flow-plan-export-test.json");
		assertEquals(expected, exportedJson);
	}

	@Test
	public void testImportFlowPlan() throws IOException {
		/*
		 * Another "Save FlowPlan" test with a more complete
		 * FlowPlanInput and verify, using a JSON file in a
		 * similar manner as the CLI.
		 */
		FlowPlanInput flowPlanInput = OBJECT_MAPPER.readValue(Resource.read("/flowPlans/flow-plan-1.json"), FlowPlanInput.class);
		SaveFlowPlanGraphQLQuery saveFlowPlanGraphQLQuery = SaveFlowPlanGraphQLQuery.newRequest().flowPlan(flowPlanInput).build();

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(saveFlowPlanGraphQLQuery, FLOW_PLAN_PROJECTION_ROOT);

		FlowPlan flowPlan = dgsQueryExecutor.executeAndExtractJsonPathAsObject(graphQLQueryRequest.serialize(),
				"data." + saveFlowPlanGraphQLQuery.getOperationName(), FlowPlan.class);

		assertEquals(1, flowPlanRepo.count());

		assertEquals("stix crossbar plan", flowPlan.getName());
		assertEquals("stix2_1", flowPlan.getIngressFlowConfigurations().get(0).getName());
		assertEquals("stix1_x", flowPlan.getIngressFlowConfigurations().get(1).getName());
		assertEquals("Stix2_1", flowPlan.getEgressFlowConfigurations().get(0).getName());
		assertEquals("Stix1_x", flowPlan.getEgressFlowConfigurations().get(1).getName());
		assertEquals("Stix1_xTo2_1TransformAction", flowPlan.getTransformActionConfigurations().get(0).getName());
		assertEquals("Stix2_1LoadAction", flowPlan.getLoadActionConfigurations().get(0).getName());
		assertEquals("Stix2_1FormatAction", flowPlan.getFormatActionConfigurations().get(0).getName());
		assertEquals("Stix1_xFormatAction", flowPlan.getFormatActionConfigurations().get(1).getName());
		assertEquals("Stix2_1ValidateAction", flowPlan.getValidateActionConfigurations().get(0).getName());
		assertEquals("Stix1_xValidateAction", flowPlan.getValidateActionConfigurations().get(1).getName());
		assertEquals("Stix2_1EgressAction", flowPlan.getEgressActionConfigurations().get(0).getName());
		assertEquals("Stix1_xEgressAction", flowPlan.getEgressActionConfigurations().get(1).getName());
		assertEquals("org.deltafi.stix", flowPlan.getSourcePlugin().getGroupId());
		assertEquals("deltafi-stix", flowPlan.getSourcePlugin().getArtifactId());
		assertEquals("0.17.0", flowPlan.getSourcePlugin().getVersion());

		Variable variable = Variable.newBuilder()
				.dataType(DATA_TYPE.STRING)
				.defaultValue("http://deltafi-egress-sink-service")
				.description("The URL to post the DeltaFile to")
				.name("egressUrl")
				.required(true).build();

		assertEquals(variable, flowPlan.getVariables().get(0));
	}

	@Test
	void testUpdateFlowPlan() {
		FlowPlan created = saveFlowPlan("planA", true, dgsQueryExecutor);
		assertTrue(verifyFlowPlan(created, "planA", true));

		FlowPlan updated = saveFlowPlan("planA", false, dgsQueryExecutor);
		assertEquals(1, flowPlanRepo.count());
		assertTrue(verifyFlowPlan(updated, "planA", false));
	}

	@Test
	void testGetFlowPlans() {
		saveFlowPlan("flowPlan1", true, dgsQueryExecutor);
		saveFlowPlan("flowPlan2", true, dgsQueryExecutor);

		List<FlowPlan> flowPlans = getFlowPlans(dgsQueryExecutor);
		assertEquals(2, flowPlans.size());

		Set<String> names = new HashSet<>();
		flowPlans.forEach(f -> names.add(f.getName()));
		assertTrue(names.containsAll(Arrays.asList("flowPlan1", "flowPlan2")));
	}

	@Test
	void testCreateFlowPlan() {
		FlowPlan flowPlan = saveFlowPlan("flowPlan", true, dgsQueryExecutor);
		assertEquals(1, flowPlanRepo.count());
		assertTrue(verifyFlowPlan(flowPlan, "flowPlan", true));
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
		assertThat(deltaFile.getSourceInfo().getMetadata()).isEqualTo(new ObjectMapper().convertValue(INGRESS_INPUT.getSourceInfo().getMetadata(), new TypeReference<List<KeyValue>>(){}));
		assertThat(deltaFile.getFirstProtocolLayer().getType()).isEqualTo("theType");
		assertThat(deltaFile.getFirstContentReference()).isEqualTo(INGRESS_INPUT.getContentReference());
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
		assertThat(deltaFile.getFirstProtocolLayer().getType()).isEqualTo("theType");
		assertThat(deltaFile.getFirstContentReference()).isEqualTo(INGRESS_INPUT.getContentReference());
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

	@Test
	public void getsPlugins() throws IOException {
		org.deltafi.core.domain.plugin.Plugin plugin1 = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), org.deltafi.core.domain.plugin.Plugin.class);
		org.deltafi.core.domain.plugin.Plugin plugin2 = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), org.deltafi.core.domain.plugin.Plugin.class);
		Mockito.when(pluginRegistryService.getPlugins()).thenReturn(List.of(plugin1, plugin2));

		PluginsGraphQLQuery pluginsGraphQLQuery = PluginsGraphQLQuery.newRequest().build();

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(pluginsGraphQLQuery, PLUGINS_PROJECTION_ROOT);

		List<org.deltafi.core.domain.generated.types.Plugin> plugins =
				dgsQueryExecutor.executeAndExtractJsonPathAsObject(graphQLQueryRequest.serialize(),
						"data.plugins[*]", new TypeRef<>() {});

		assertEquals(2, plugins.size());

		validatePlugin1(plugins.get(0));
	}

	@Test
	public void registersPlugin() throws IOException {
		PluginInput pluginInput = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), PluginInput.class);
		RegisterPluginGraphQLQuery registerPluginGraphQLQuery = RegisterPluginGraphQLQuery.newRequest().pluginInput(pluginInput).build();

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(registerPluginGraphQLQuery, PLUGINS_PROJECTION_ROOT);

		org.deltafi.core.domain.plugin.Plugin plugin = dgsQueryExecutor.executeAndExtractJsonPathAsObject(graphQLQueryRequest.serialize(),
				"data." + registerPluginGraphQLQuery.getOperationName(), org.deltafi.core.domain.plugin.Plugin.class);

		validatePlugin1(plugin);

		ArgumentCaptor<org.deltafi.core.domain.plugin.Plugin> pluginArgumentCaptor = ArgumentCaptor.forClass(org.deltafi.core.domain.plugin.Plugin.class);
		Mockito.verify(pluginRegistryService).addPlugin(pluginArgumentCaptor.capture());
		validatePlugin1(pluginArgumentCaptor.getValue());
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

	private void testFilter(DeltaFilesFilter filter, DeltaFile... expected) {
		DeltaFiles deltaFiles = deltaFileRepo.deltaFiles(null, 50, filter, null);
		assertEquals(new ArrayList<>(Arrays.asList(expected)), deltaFiles.getDeltaFiles());
	}

	private DeltaFile loadDeltaFile(String did) {
		return deltaFileRepo.findById(did).orElse(null);
	}
}
