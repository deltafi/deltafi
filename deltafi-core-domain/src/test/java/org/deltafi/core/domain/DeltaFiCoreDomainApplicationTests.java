package org.deltafi.core.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import org.deltafi.core.domain.repo.DeltaFileRepo;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.configuration.DeltaFiProperties;
import org.deltafi.core.domain.delete.DeleteRunner;
import org.deltafi.core.domain.exceptions.ActionConfigException;
import org.deltafi.core.domain.generated.DgsConstants;
import org.deltafi.core.domain.generated.types.*;
import org.deltafi.core.domain.services.DeltaFiConfigService;
import org.deltafi.core.domain.services.DeltaFilesService;
import org.deltafi.core.domain.services.RedisService;
import org.deltafi.core.domain.validation.DeltafiRuntimeConfigurationValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;

import static graphql.Assert.assertFalse;
import static graphql.Assert.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;

@SpringBootTest
class DeltaFiCoreDomainApplicationTests {

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
			KeyValue.newBuilder().key("loadSampleType").value("load-sample-type").build(),
			KeyValue.newBuilder().key("loadSampleVersion").value("2.2").build());
	final static List <KeyValue> transformSampleMetadata = Arrays.asList(
			KeyValue.newBuilder().key("sampleType").value("sample-type").build(),
			KeyValue.newBuilder().key("sampleVersion").value("2.1").build());

	@BeforeEach
	void setup() throws IOException {
		deltaFileRepo.deleteAll();
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
		deltaFile.setSourceInfo(SourceInfo.newBuilder()
				.filename("input.txt")
				.flow("sample")
				.metadata(Collections.singletonList(KeyValue.newBuilder()
						.key("AuthorizedBy")
						.value("XYZ").build())).build());
		deltaFile.getProtocolStack().add(ProtocolLayer.newBuilder()
				.type("json")
				.metadata(new ArrayList<>())
				.objectReference(ObjectReference.newBuilder()
						.name("objectName")
						.bucket("objectBucket")
						.offset(0)
						.size(500).build()).build());
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
		deltaFile.getProtocolStack().add(ProtocolLayer.newBuilder()
				.type("json-utf8")
				.objectReference(ObjectReference.newBuilder()
						.name("utf8ObjectName")
						.bucket("utf8ObjectBucket")
						.offset(0)
						.size(500).build()).build());
		return deltaFile;
	}

	@Test
	void test03TransformUtf8() throws IOException, ActionConfigException {
		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postIngressDeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("03.transformUtf8"), did),
				"data." + DgsConstants.MUTATION.ActionEvent,
				DeltaFile.class).getDid();

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
		deltaFile.getProtocolStack().add(ProtocolLayer.newBuilder()
				.type("json-utf8-sample")
				.metadata(transformSampleMetadata)
				.objectReference(ObjectReference.newBuilder()
						.name("objectName")
						.bucket("objectBucket")
						.offset(0)
						.size(500).build()).build());
		return deltaFile;
	}

	@Test
	void test05Transform() throws IOException, ActionConfigException {
		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postTransformUtf8DeltaFile(did));

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

	DeltaFile postLoadDeltaFile(String did) {
		DeltaFile deltaFile = postTransformDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.EGRESS);
		deltaFile.queueAction("SampleEnrichAction");
		deltaFile.completeAction("SampleLoadAction");
		deltaFile.addDomain("sample", "sampleDomain");
		deltaFile.getProtocolStack().add(ProtocolLayer.newBuilder()
				.type("json-utf8-sample-load")
				.metadata(loadSampleMetadata)
				.objectReference(ObjectReference.newBuilder()
						.name("objectName")
						.bucket("objectBucket")
						.offset(0)
						.size(500).build()).build());
		return deltaFile;
	}

	@Test
	void test08Load() throws IOException, ActionConfigException {
		String did = UUID.randomUUID().toString();
		DeltaFile postTransform = postTransformDeltaFile(did);
		deltaFilesService.addDeltaFile(postTransform);

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
		deltaFilesService.addDeltaFile(postLoadDeltaFile(did));

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
				.metadata(Arrays.asList(KeyValue.newBuilder().key("key1").value("value1").build(), KeyValue.newBuilder().key("key2").value("value2").build()))
				.objectReference(ObjectReference.newBuilder()
						.name("formattedObjectName")
						.bucket("formattedBucketName")
						.offset(0)
						.size(1000)
						.build())
				.egressActions(Collections.singletonList("SampleEgressAction"))
				.build());
		return deltaFile;
	}

	@Test
	void test13Format() throws IOException, ActionConfigException {
		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postEnrichDeltaFile(did));

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
		deltaFilesService.addDeltaFile(postFormatDeltaFile(did));

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
		deltaFilesService.addDeltaFile(postValidateDeltaFile(did));

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
		deltaFilesService.addDeltaFile(postErrorDeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("18.retry"), did),
				"data." + DgsConstants.MUTATION.Retry,
				new TypeRef<>() {});

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertTrue(Util.equalIgnoringDates(postRetryDeltaFile(did), deltaFile));

		ArgumentCaptor<DeltaFile> actual = ArgumentCaptor.forClass(DeltaFile.class);
		Mockito.verify(redisService).enqueue(eq(Collections.singletonList("AuthorityValidateAction")), actual.capture());
		deltaFile = actual.getValue();
		assertTrue(Util.equalIgnoringDates(postRetryDeltaFile(did), deltaFile));
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
		deltaFilesService.addDeltaFile(postValidateDeltaFile(did));

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
		deltaFilesService.addDeltaFile(postValidateAuthorityDeltaFile(did));

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
		deltaFilesService.addDeltaFile(postValidateAuthorityDeltaFile(did));

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
		deltaFilesService.addDeltaFile(postValidateAuthorityDeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("22.egress"), did),
				"data." + DgsConstants.MUTATION.ActionEvent,
				DeltaFile.class);

		Mockito.verify(redisService).enqueue(Mockito.eq(Collections.singletonList("DeleteAction")), Mockito.any());
	}

	@Test
	void test25ActionConfigError() throws IOException, ActionConfigException {
		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postIngressDeltaFile(did));

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
	void deleteActionAddedToRedisQueueAfterCompletion() throws Exception {
		deltaFileRepo.save(DeltaFile.newBuilder().did("a").stage(DeltaFileStage.COMPLETE)
				.modified(OffsetDateTime.now()).actions(Collections.emptyList()).build());
		Thread.sleep(1000);
		Mockito.verify(redisService, never()).enqueue(any(), any());

		Thread.sleep(2000);
		Mockito.verify(redisService).enqueue(Mockito.eq(Collections.singletonList("DeleteAction")), Mockito.any());
	}
}
