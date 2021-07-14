package org.deltafi.dgs;

import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.dgs.configuration.DeltaFiProperties;
import org.deltafi.dgs.schedulers.DeleteScheduler;
import org.deltafi.dgs.generated.DgsConstants;
import org.deltafi.dgs.generated.types.*;
import org.deltafi.dgs.repo.DeltaFiConfigRepo;
import org.deltafi.dgs.repo.DeltaFileRepo;
import org.deltafi.dgs.services.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.IOException;
import java.util.*;

import static graphql.Assert.assertFalse;
import static graphql.Assert.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.deltafi.dgs.Util.equalIgnoringDates;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;

@SpringBootTest
class DeltaFiDgsApplicationTests {

	@Autowired
	DgsQueryExecutor dgsQueryExecutor;

	@Autowired
	DeltaFilesService deltaFilesService;

	@Autowired
	DeltaFiProperties deltaFiProperties;

	@Autowired
	SampleEnrichmentsService sampleEnrichmentsService;

	@Autowired
	StateMachine stateMachine;

	@Autowired
	SampleDomainsService sampleDomainsService;

	@Autowired
	DeleteScheduler deleteScheduler;

	@Autowired
	DeltaFileRepo deltaFileRepo;

	@Autowired
	DeltaFiConfigRepo deltaFiConfigRepo;

	@Autowired
	ConfigLoaderService configLoaderService;

	@MockBean
	RedisService redisService;

	final static List <KeyValue> sampleMetadata = Arrays.asList(
			KeyValue.newBuilder().key("sampleType").value("sample-type").build(),
			KeyValue.newBuilder().key("sampleVersion").value("2.1").build());

	@BeforeEach
	void setup() {
		deltaFileRepo.deleteAll();
		sampleEnrichmentsService.getSampleEnrichments().clear();
		sampleDomainsService.getSampleDomains().clear();
		deltaFiProperties.getDelete().setOnCompletion(false);
		configLoaderService.loadProperties();
	}

	@Test
	void contextLoads() {
		assertTrue(true);
		assertFalse(deltaFiProperties.getIngress().getIngressFlows().isEmpty());
	}

	@Test
	void deletePoliciesScheduled() {
		assertThat(deleteScheduler.getDeletePolicies().size()).isEqualTo(1);
		assertThat(deleteScheduler.getDeletePolicies().get(0).getName()).isEqualTo("oneHourAfterComplete");
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
	void test01Ingress() throws IOException {
		String did = UUID.randomUUID().toString();
		DeltaFile deltaFileFromDgs = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("01.ingress"), did),
				"data." + DgsConstants.MUTATION.Ingress,
				DeltaFile.class);

		assertEquals(did, deltaFileFromDgs.getDid());

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertTrue(equalIgnoringDates(postIngressDeltaFile(did), deltaFile));

		ArgumentCaptor<DeltaFile> actual = ArgumentCaptor.forClass(DeltaFile.class);
		Mockito.verify(redisService).enqueue(eq(Collections.singletonList("Utf8TransformAction")), actual.capture());
		deltaFile = actual.getValue();
		assertTrue(equalIgnoringDates(postIngressDeltaFile(did), deltaFile));
	}

	DeltaFile postTransformUtf8DeltaFile(String did) {
		DeltaFile deltaFile = postIngressDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.TRANSFORM.name());
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
	void test03TransformUtf8() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postIngressDeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("03.transformUtf8"), did),
				"data." + DgsConstants.MUTATION.Transform,
				DeltaFile.class).getDid();

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertTrue(equalIgnoringDates(postTransformUtf8DeltaFile(did), deltaFile));

		ArgumentCaptor<DeltaFile> actual = ArgumentCaptor.forClass(DeltaFile.class);
		Mockito.verify(redisService).enqueue(eq(Collections.singletonList("SampleTransformAction")), actual.capture());
		deltaFile = actual.getValue();
		assertTrue(equalIgnoringDates(postTransformUtf8DeltaFile(did), deltaFile));
	}

	DeltaFile postTransformDeltaFile(String did) {
		DeltaFile deltaFile = postTransformUtf8DeltaFile(did);
		deltaFile.setStage(DeltaFileStage.LOAD.name());
		deltaFile.completeAction("SampleTransformAction");
		deltaFile.queueAction("SampleLoadAction");
		deltaFile.getProtocolStack().add(ProtocolLayer.newBuilder()
				.type("json-utf8-sample")
				.metadata(sampleMetadata)
				.objectReference(ObjectReference.newBuilder()
						.name("objectName")
						.bucket("objectBucket")
						.offset(0)
						.size(500).build()).build());
		return deltaFile;
	}

	@Test
	void test05Transform() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postTransformUtf8DeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("05.transform"), did),
				"data." + DgsConstants.MUTATION.Transform,
				DeltaFile.class);

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertTrue(equalIgnoringDates(postTransformDeltaFile(did), deltaFile));

		ArgumentCaptor<DeltaFile> actual = ArgumentCaptor.forClass(DeltaFile.class);
		Mockito.verify(redisService).enqueue(eq(Collections.singletonList("SampleLoadAction")), actual.capture());
		deltaFile = actual.getValue();
		assertTrue(equalIgnoringDates(postTransformDeltaFile(did), deltaFile));
	}

	SampleDomain sampleDomain(String did) {
		return SampleDomain.newBuilder()
				.did(did)
				.domained(true)
				.build();
	}

	@Test
	void test07AddSampleDomain() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postTransformDeltaFile(did));

		DeltaFile deltaFile = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("07.addSampleDomain"), did),
				"data." + DgsConstants.MUTATION.AddSampleDomain,
				DeltaFile.class);

		assertThat(deltaFile.getDid()).isEqualTo(did);
		assertTrue(equalIgnoringDates(postTransformDeltaFile(did), deltaFilesService.getDeltaFile(did)));
		assertThat(sampleDomain(did)).isEqualTo(sampleDomainsService.forDid(did));
	}

	DeltaFile postLoadDeltaFile(String did) {
		DeltaFile deltaFile = postTransformDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.ENRICH.name());
		deltaFile.queueAction("SampleEnrichAction");
		deltaFile.completeAction("SampleLoadAction");
		deltaFile.getDomains().getDomainTypes().add("sample");
		return deltaFile;
	}

	@Test
	void test08Load() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postTransformDeltaFile(did));
		sampleDomainsService.addSampleDomain(sampleDomain(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("08.load"), did),
				"data." + DgsConstants.MUTATION.Load,
				DeltaFile.class);

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertTrue(equalIgnoringDates(postLoadDeltaFile(did), deltaFile));

		ArgumentCaptor<DeltaFile> actual = ArgumentCaptor.forClass(DeltaFile.class);
		Mockito.verify(redisService).enqueue(eq(Collections.singletonList("SampleEnrichAction")), actual.capture());
		deltaFile = actual.getValue();
		assertTrue(equalIgnoringDates(postLoadDeltaFile(did), deltaFile));
	}

	SampleEnrichment sampleEnrichment(String did) {
		return SampleEnrichment.newBuilder()
				.did(did)
				.enriched(true)
				.build();
	}

	@Test
	void test10AddSampleEnrichment() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postLoadDeltaFile(did));
		sampleDomainsService.addSampleDomain(sampleDomain(did));

		DeltaFile deltaFile = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("10.addSampleEnrichment"), did),
				"data." + DgsConstants.MUTATION.AddSampleEnrichment,
				DeltaFile.class);

		assertThat(deltaFile.getDid()).isEqualTo(did);
		assertThat(sampleEnrichment(did)).isEqualTo(sampleEnrichmentsService.forDid(did));
	}

	DeltaFile postEnrichDeltaFile(String did) {
		DeltaFile deltaFile = postLoadDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.FORMAT.name());
		deltaFile.queueAction("SampleFormatAction");
		deltaFile.completeAction("SampleEnrichAction");
		deltaFile.getEnrichment().getEnrichmentTypes().add("sampleEnrichment");
		return deltaFile;
	}

	@Test
	void test11Enrich() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postLoadDeltaFile(did));
		sampleEnrichmentsService.addSampleEnrichment(sampleEnrichment(did));
		sampleDomainsService.addSampleDomain(sampleDomain(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("11.enrich"), did),
				"data." + DgsConstants.MUTATION.Enrich,
				DeltaFile.class);

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertTrue(equalIgnoringDates(postEnrichDeltaFile(did), deltaFile));

		ArgumentCaptor<DeltaFile> actual = ArgumentCaptor.forClass(DeltaFile.class);
		Mockito.verify(redisService).enqueue(eq(Collections.singletonList("SampleFormatAction")), actual.capture());
		deltaFile = actual.getValue();
		assertTrue(equalIgnoringDates(postEnrichDeltaFile(did), deltaFile));
	}

	DeltaFile postFormatDeltaFile(String did) {
		DeltaFile deltaFile = postEnrichDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.VALIDATE.name());
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
	void test13Format() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postEnrichDeltaFile(did));
		sampleEnrichmentsService.addSampleEnrichment(sampleEnrichment(did));
		sampleDomainsService.addSampleDomain(sampleDomain(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("13.format"), did),
				"data." + DgsConstants.MUTATION.Format,
				DeltaFile.class);

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertTrue(equalIgnoringDates(postFormatDeltaFile(did), deltaFile));

		ArgumentCaptor<DeltaFile> actual = ArgumentCaptor.forClass(DeltaFile.class);
		Mockito.verify(redisService).enqueue(eq(Arrays.asList("AuthorityValidateAction", "SampleValidateAction")), actual.capture());
		deltaFile = actual.getValue();
		assertTrue(equalIgnoringDates(postFormatDeltaFile(did), deltaFile));
	}

	DeltaFile postValidateDeltaFile(String did) {
		DeltaFile deltaFile = postFormatDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.VALIDATE.name());
		deltaFile.completeAction("SampleValidateAction");
		return deltaFile;
	}

	@Test
	void test15Validate() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postFormatDeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("15.validate"), did),
				"data." + DgsConstants.MUTATION.Validate,
				DeltaFile.class);

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertTrue(equalIgnoringDates(postValidateDeltaFile(did), deltaFile));

		Mockito.verify(redisService, never()).enqueue(any(), any());
		assertTrue(equalIgnoringDates(postValidateDeltaFile(did), deltaFile));
	}

	DeltaFile postErrorDeltaFile(String did) {
		DeltaFile deltaFile = postValidateDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.ERROR.name());
		deltaFile.errorAction("AuthorityValidateAction", "Authority XYZ not recognized");
		return deltaFile;
	}

	@Test
	void test17Error() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postValidateDeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("17.error"), did),
				"data." + DgsConstants.MUTATION.Error,
				DeltaFile.class);

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertTrue(equalIgnoringDates(postErrorDeltaFile(did), deltaFile));

		Mockito.verify(redisService, never()).enqueue(any(), any());
	}

	@Test
	void test18Retry() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postErrorDeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("18.retry"), did),
				"data." + DgsConstants.MUTATION.Retry,
				DeltaFile.class);

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertTrue(equalIgnoringDates(postValidateDeltaFile(did), deltaFile));

		// it is not immediately added to the queue, the scheduled requeue task will pick it up
		Mockito.verify(redisService, never()).enqueue(any(), any());
		assertTrue(equalIgnoringDates(postValidateDeltaFile(did), deltaFile));
	}

	DeltaFile postValidateAuthorityDeltaFile(String did) {
		DeltaFile deltaFile = postValidateDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.EGRESS.name());
		deltaFile.queueAction("SampleEgressAction");
		deltaFile.completeAction("AuthorityValidateAction");
		return deltaFile;
	}

	@Test
	void test20ValidateAuthority() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postValidateDeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("20.validateAuthority"), did),
				"data." + DgsConstants.MUTATION.Validate,
				DeltaFile.class);

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertTrue(equalIgnoringDates(postValidateAuthorityDeltaFile(did), deltaFile));

		ArgumentCaptor<DeltaFile> actual = ArgumentCaptor.forClass(DeltaFile.class);
		Mockito.verify(redisService).enqueue(eq(Collections.singletonList("SampleEgressAction")), actual.capture());
		deltaFile = actual.getValue();
		assertTrue(equalIgnoringDates(postValidateAuthorityDeltaFile(did), deltaFile));
	}

	DeltaFile postEgressDeltaFile(String did) {
		DeltaFile deltaFile = postValidateAuthorityDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.COMPLETE.name());
		deltaFile.completeAction("SampleEgressAction");
		return deltaFile;
	}

	@Test
	void test22Egress() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postValidateAuthorityDeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("22.egress"), did),
				"data." + DgsConstants.MUTATION.Egress,
				DeltaFile.class);

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertTrue(equalIgnoringDates(postEgressDeltaFile(did), deltaFile));

		Mockito.verify(redisService, never()).enqueue(any(), any());
	}

	@Test
	void testFilterEgress() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postValidateAuthorityDeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("filter"), did, "SampleEgressAction"),
				"data." + DgsConstants.MUTATION.Filter,
				DeltaFile.class);

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		Action lastAction = deltaFile.getActions().get(deltaFile.getActions().size()-1);
		assertEquals("SampleEgressAction", lastAction.getName());
		assertEquals(ActionState.FILTERED, lastAction.getState());
		assertEquals(DeltaFileStage.COMPLETE.name(), deltaFile.getStage());

		Mockito.verify(redisService, never()).enqueue(any(), any());
	}

	@Test
	void test22EgressDeleteCompleted() throws IOException {
		deltaFiProperties.getDelete().setOnCompletion(true);

		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postValidateAuthorityDeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("22.egress"), did),
				"data." + DgsConstants.MUTATION.Egress,
				DeltaFile.class);

		assertNull(deltaFilesService.getDeltaFile(did));
		Mockito.verify(redisService, never()).enqueue(any(), any());
	}

	@Test
	void test24Delete() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postEgressDeltaFile(did));

		List<String> dids = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("24.delete"), did),
				"data." + DgsConstants.MUTATION.Delete,
				new TypeRef<>() {});

		assertEquals(Arrays.asList(did, "nonsenseDid"), dids);
		assertNull(deltaFilesService.getDeltaFile(did));
	}
}
