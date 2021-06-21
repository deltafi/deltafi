package org.deltafi.dgs;

import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.dgs.configuration.DeltaFiProperties;
import org.deltafi.dgs.converters.DeltaFileConverter;
import org.deltafi.dgs.delete.DeleteConstants;
import org.deltafi.dgs.delete.DeleteScheduler;
import org.deltafi.dgs.generated.DgsConstants;
import org.deltafi.dgs.generated.types.*;
import org.deltafi.dgs.repo.DeltaFileRepo;
import org.deltafi.dgs.services.DeltaFilesService;
import org.deltafi.dgs.services.SampleDomainsService;
import org.deltafi.dgs.services.SampleEnrichmentsService;
import org.deltafi.dgs.services.StateMachine;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;

import static graphql.Assert.assertFalse;
import static graphql.Assert.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.deltafi.dgs.Util.equalIgnoringDates;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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

	final static List<String> completedActions = Arrays.asList(
			"Utf8TransformAction",
			"SampleTransformAction",
			"SampleLoadAction",
			"SampleEnrichAction",
			"SampleFormatAction",
			"SampleValidateAction",
			"AuthorityValidateAction",
			"SampleEgressAction");

	final static List <KeyValue> sampleMetadata = Arrays.asList(
			KeyValue.newBuilder().key("sampleType").value("sample-type").build(),
			KeyValue.newBuilder().key("sampleVersion").value("2.1").build());

	@BeforeEach
	void setup() {
		deltaFileRepo.deleteAll();
		sampleEnrichmentsService.getSampleEnrichments().clear();
		sampleDomainsService.getSampleDomains().clear();
		deltaFiProperties.getDelete().setOnCompletion(false);
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
		assertTrue(deltaFileRepo.findAll().isEmpty());
		String did = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQL("01.ingress"),
				"data." + DgsConstants.MUTATION.Ingress,
				DeltaFile.class).getDid();

		assertThat(UUID.fromString(did)).hasToString(did);

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertTrue(equalIgnoringDates(postIngressDeltaFile(did), deltaFile));
	}

	@Test
	void test02TransformFeedUtf8() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postIngressDeltaFile(did));

		List<DeltaFile> transformFeed = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQL("02.transformFeedUtf8"),
				"data." + DgsConstants.QUERY.ActionFeed,
				new TypeRef<>() {});

		assertThat(transformFeed.size()).isEqualTo(1);

		DeltaFile deltaFile = transformFeed.get(0);
		assertThat(deltaFile.getDid()).isEqualTo(did);
		assertThat(deltaFile.getProtocolStack().size()).isEqualTo(1);
		ProtocolLayer protocolLayer = deltaFile.getProtocolStack().get(0);
		assertThat(protocolLayer.getType()).isEqualTo("json");
		assertThat(protocolLayer.getObjectReference().getOffset()).isZero();
		assertThat(protocolLayer.getObjectReference().getSize()).isEqualTo(500);
		assertThat(protocolLayer.getObjectReference().getName()).isEqualTo("objectName");
		assertThat(protocolLayer.getObjectReference().getBucket()).isEqualTo("objectBucket");
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

		DeltaFile deltaFile = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("03.transformUtf8"), did),
				"data." + DgsConstants.MUTATION.Transform,
				DeltaFile.class);

		assertThat(deltaFile.getDid()).isEqualTo(did);
		assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.TRANSFORM.name());
		assertThat(deltaFile.queuedActions().size()).isEqualTo(1);
		assertTrue(deltaFile.readyForDispatch("SampleTransformAction", deltaFiProperties.getFeedTimeoutSeconds()));
		assertThat(deltaFile.completedActions()).isEqualTo(completedActions.subList(0, 1));

		assertTrue(equalIgnoringDates(postTransformUtf8DeltaFile(did), deltaFilesService.getDeltaFile(did)));
		assertThat(deltaFile.getCreated()).isNotEqualTo(deltaFile.getModified());
	}

	@Test
	void test04TransformFeed() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postTransformUtf8DeltaFile(did));

		List<DeltaFile> transformFeed = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQL("04.transformFeed"),
				"data." + DgsConstants.QUERY.ActionFeed,
				new TypeRef<>() {});

		assertThat(transformFeed.size()).isEqualTo(1);

		DeltaFile deltaFile = transformFeed.get(0);
		assertThat(deltaFile.getDid()).isEqualTo(did);
		assertThat(deltaFile.getProtocolStack().size()).isEqualTo(1);
		ProtocolLayer protocolLayer = deltaFile.getProtocolStack().get(0);
		assertThat(protocolLayer.getType()).isEqualTo("json-utf8");
		assertThat(protocolLayer.getObjectReference().getOffset()).isZero();
		assertThat(protocolLayer.getObjectReference().getSize()).isEqualTo(500);
		assertThat(protocolLayer.getObjectReference().getName()).isEqualTo("utf8ObjectName");
		assertThat(protocolLayer.getObjectReference().getBucket()).isEqualTo("utf8ObjectBucket");
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

		DeltaFile deltaFile = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("05.transform"), did),
				"data." + DgsConstants.MUTATION.Transform,
				DeltaFile.class);

		assertThat(deltaFile.getDid()).isEqualTo(did);
		assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.LOAD.name());
		assertThat(deltaFile.queuedActions().size()).isEqualTo(1);
		assertTrue(deltaFile.readyForDispatch("SampleLoadAction", deltaFiProperties.getFeedTimeoutSeconds()));
		assertThat(deltaFile.completedActions()).isEqualTo(completedActions.subList(0, 2));

		assertTrue(equalIgnoringDates(postTransformDeltaFile(did), deltaFilesService.getDeltaFile(did)));
	}

	@Test
	void test06LoadFeed() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postTransformDeltaFile(did));

		List<DeltaFile> transformFeed = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQL("06.loadFeed"),
				"data." + DgsConstants.QUERY.ActionFeed,
				new TypeRef<>() {});

		assertThat(transformFeed.size()).isEqualTo(1);

		DeltaFile deltaFile = transformFeed.get(0);
		assertThat(deltaFile.getDid()).isEqualTo(did);
		assertThat(deltaFile.getProtocolStack().size()).isEqualTo(1);
		assertThat(deltaFile.getProtocolStack().get(0).getType()).isEqualTo("json-utf8-sample");
		Map<String, String> metadata = DeltaFileConverter.convertKeyValues(deltaFile.getProtocolStack().get(0).getMetadata());
		assertThat(metadata).containsEntry("sampleType", "sample-type").containsEntry("sampleVersion", "2.1");
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

		DeltaFile deltaFile = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("08.load"), did),
				"data." + DgsConstants.MUTATION.Load,
				DeltaFile.class);

		assertThat(deltaFile.getDid()).isEqualTo(did);
		assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.ENRICH.name());
		assertThat(deltaFile.queuedActions().size()).isEqualTo(1);
		assertTrue(deltaFile.readyForDispatch("SampleEnrichAction", deltaFiProperties.getFeedTimeoutSeconds()));
		assertThat(deltaFile.completedActions()).isEqualTo(completedActions.subList(0, 3));

		assertTrue(equalIgnoringDates(postLoadDeltaFile(did), deltaFilesService.getDeltaFile(did)));
	}

	@Test
	void test09EnrichFeed() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postLoadDeltaFile(did));
		sampleDomainsService.addSampleDomain(sampleDomain(did));

		List<DeltaFile> transformFeed = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQL("09.enrichFeed"),
				"data." + DgsConstants.QUERY.ActionFeed,
				new TypeRef<>() {});

		assertThat(transformFeed.size()).isEqualTo(1);

		DeltaFile deltaFile = transformFeed.get(0);
		assertThat(deltaFile.getDid()).isEqualTo(did);
		assertThat(deltaFile.getDomains().getSampleDomain()).isNotNull();
		assertThat(deltaFile.getDomains().getSampleDomain().getDid()).isEqualTo(did);
		assertTrue(deltaFile.getDomains().getSampleDomain().getDomained());
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

		DeltaFile deltaFile = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("11.enrich"), did),
				"data." + DgsConstants.MUTATION.Enrich,
				DeltaFile.class);

		assertThat(deltaFile.getDid()).isEqualTo(did);
		assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.FORMAT.name());
		assertThat(deltaFile.queuedActions().size()).isEqualTo(1);
		assertTrue(deltaFile.readyForDispatch("SampleFormatAction", deltaFiProperties.getFeedTimeoutSeconds()));
		assertThat(deltaFile.completedActions()).isEqualTo(completedActions.subList(0, 4));

		assertTrue(equalIgnoringDates(postEnrichDeltaFile(did), deltaFilesService.getDeltaFile(did)));
	}

	@Test
	void test12FormatFeed() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postEnrichDeltaFile(did));
		sampleEnrichmentsService.addSampleEnrichment(sampleEnrichment(did));
		sampleDomainsService.addSampleDomain(sampleDomain(did));

		List<DeltaFile> formatFeed = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQL("12.formatFeed"),
				"data." + DgsConstants.QUERY.ActionFeed,
				new TypeRef<>() {});

		assertThat(formatFeed.size()).isEqualTo(1);

		DeltaFile deltaFile = formatFeed.get(0);
		assertThat(deltaFile.getDid()).isEqualTo(did);
		assertThat(deltaFile.getDomains().getSampleDomain()).isNotNull();
		assertThat(deltaFile.getDomains().getSampleDomain().getDid()).isEqualTo(did);
		assertTrue(deltaFile.getDomains().getSampleDomain().getDomained());
		assertThat(deltaFile.getEnrichment().getSampleEnrichment()).isNotNull();
		assertThat(deltaFile.getEnrichment().getSampleEnrichment().getDid()).isEqualTo(did);
		assertTrue(deltaFile.getEnrichment().getSampleEnrichment().getEnriched());
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
						.size(1000).build()).build());
		return deltaFile;
	}

	@Test
	void test13Format() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postEnrichDeltaFile(did));
		sampleEnrichmentsService.addSampleEnrichment(sampleEnrichment(did));
		sampleDomainsService.addSampleDomain(sampleDomain(did));

		DeltaFile deltaFile = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("13.format"), did),
				"data." + DgsConstants.MUTATION.Format,
				DeltaFile.class);

		assertThat(deltaFile.getDid()).isEqualTo(did);
		assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.VALIDATE.name());
		assertThat(deltaFile.queuedActions().size()).isEqualTo(2);
		assertTrue(deltaFile.readyForDispatch("AuthorityValidateAction", deltaFiProperties.getFeedTimeoutSeconds()));
		assertTrue(deltaFile.readyForDispatch("SampleValidateAction", deltaFiProperties.getFeedTimeoutSeconds()));
		assertThat(deltaFile.completedActions()).isEqualTo(completedActions.subList(0, 5));

		assertTrue(equalIgnoringDates(postFormatDeltaFile(did), deltaFilesService.getDeltaFile(did)));
	}

	@Test
	void test14ValidateFeed() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postFormatDeltaFile(did));

		List<DeltaFile> validateFeed = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQL("14.validateFeed"),
				"data." + DgsConstants.QUERY.ActionFeed,
				new TypeRef<>() {});

		assertThat(validateFeed.size()).isEqualTo(1);

		DeltaFile deltaFile = validateFeed.get(0);
		assertThat(deltaFile.getDid()).isEqualTo(did);
		assertThat(deltaFile.getFormattedData().size()).isEqualTo(1);
		assertThat(deltaFile.getFormattedData().get(0).getFormatAction()).isEqualTo("SampleFormatAction");
		assertThat(deltaFile.getFormattedData().get(0).getObjectReference()).isNotNull();
		assertThat(deltaFile.getFormattedData().get(0).getObjectReference().getBucket()).isEqualTo("formattedBucketName");
		assertThat(deltaFile.getFormattedData().get(0).getObjectReference().getName()).isEqualTo("formattedObjectName");
		assertThat(deltaFile.getFormattedData().get(0).getObjectReference().getSize()).isEqualTo(1000);
		assertThat(deltaFile.getFormattedData().get(0).getObjectReference().getOffset()).isZero();
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

		DeltaFile deltaFile = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("15.validate"), did),
				"data." + DgsConstants.MUTATION.Validate,
				DeltaFile.class);

		assertThat(deltaFile.getDid()).isEqualTo(did);
		assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.VALIDATE.name());
		assertThat(deltaFile.queuedActions().size()).isEqualTo(1);
		assertTrue(deltaFile.readyForDispatch("AuthorityValidateAction", deltaFiProperties.getFeedTimeoutSeconds()));
		assertThat(deltaFile.completedActions()).isEqualTo(completedActions.subList(0, 6));

		assertTrue(equalIgnoringDates(postValidateDeltaFile(did), deltaFilesService.getDeltaFile(did)));
	}

	@Test
	void test16ValidateFeedAuthority() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postValidateDeltaFile(did));

		List<DeltaFile> validateFeed = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQL("16.validateFeedAuthority"),
				"data." + DgsConstants.QUERY.ActionFeed,
				new TypeRef<>() {});

		assertThat(validateFeed.size()).isEqualTo(1);

		DeltaFile deltaFile = validateFeed.get(0);
		assertThat(deltaFile.getDid()).isEqualTo(did);
		assertThat(deltaFile.getSourceInfo().getMetadata().size()).isEqualTo(1);
		assertThat(deltaFile.getSourceInfo().getMetadata().get(0).getKey()).isEqualTo("AuthorizedBy");
		assertThat(deltaFile.getSourceInfo().getMetadata().get(0).getValue()).isEqualTo("XYZ");
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

		DeltaFile deltaFile = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("17.error"), did),
				"data." + DgsConstants.MUTATION.Error,
				DeltaFile.class);

		assertThat(deltaFile.getDid()).isEqualTo(did);
		assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.ERROR.name());
		assertTrue(deltaFile.queuedActions().isEmpty());
		assertThat(deltaFile.completedActions()).isEqualTo(completedActions.subList(0, 6));
		assertThat(deltaFile.erroredActions().size()).isEqualTo(1);
		assertTrue(deltaFile.hasErroredAction("AuthorityValidateAction"));
		Optional<Action> maybeAction = deltaFile.actionNamed("AuthorityValidateAction");
		assertTrue(maybeAction.isPresent());
		assertThat(maybeAction.get().getErrorMessage()).isEqualTo("Authority XYZ not recognized");

		assertTrue(equalIgnoringDates(postErrorDeltaFile(did), deltaFilesService.getDeltaFile(did)));
	}

	@Test
	void test18Retry() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postErrorDeltaFile(did));

		DeltaFile deltaFile = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("18.retry"), did),
				"data." + DgsConstants.MUTATION.Retry,
				DeltaFile.class);

		assertThat(deltaFile.getDid()).isEqualTo(did);
		assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.VALIDATE.name());
		assertThat(deltaFile.queuedActions().size()).isEqualTo(1);
		assertTrue(deltaFile.readyForDispatch("AuthorityValidateAction", deltaFiProperties.getFeedTimeoutSeconds()));
		assertThat(deltaFile.completedActions()).isEqualTo(completedActions.subList(0, 6));
		assertTrue(deltaFile.erroredActions().isEmpty());

		assertTrue(equalIgnoringDates(postValidateDeltaFile(did), deltaFilesService.getDeltaFile(did)));
	}

	@Test
	void test19ValidateFeedAuthorityAgain() throws IOException {
		// this is a duplicate of 14, leave it here for flow clarity
		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postValidateDeltaFile(did));

		List<DeltaFile> validateFeed = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQL("19.validateFeedAuthorityAgain"),
				"data." + DgsConstants.QUERY.ActionFeed,
				new TypeRef<>() {});

		assertThat(validateFeed.size()).isEqualTo(1);

		DeltaFile deltaFile = validateFeed.get(0);
		assertThat(deltaFile.getDid()).isEqualTo(did);
		assertThat(deltaFile.getSourceInfo().getMetadata().size()).isEqualTo(1);
		assertThat(deltaFile.getSourceInfo().getMetadata().get(0).getKey()).isEqualTo("AuthorizedBy");
		assertThat(deltaFile.getSourceInfo().getMetadata().get(0).getValue()).isEqualTo("XYZ");
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

		DeltaFile deltaFile = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("20.validateAuthority"), did),
				"data." + DgsConstants.MUTATION.Validate,
				DeltaFile.class);

		assertThat(deltaFile.getDid()).isEqualTo(did);
		assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.EGRESS.name());
		assertThat(deltaFile.queuedActions().size()).isEqualTo(1);
		assertTrue(deltaFile.readyForDispatch("SampleEgressAction", deltaFiProperties.getFeedTimeoutSeconds()));
		// the actions completed in a different order than they were queued.  Sort the lists alphabetically so they match
		List<String> expectedActions = new ArrayList<>(completedActions.subList(0, 7));
		Collections.sort(expectedActions);
		List<String> actualActions = new ArrayList<>(deltaFile.completedActions());
		Collections.sort(actualActions);
		assertThat(expectedActions).isEqualTo(actualActions);

		assertTrue(equalIgnoringDates(postValidateAuthorityDeltaFile(did), deltaFilesService.getDeltaFile(did)));
	}

	@Test
	void test21EgressFeed() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postValidateAuthorityDeltaFile(did));

		List<DeltaFile> egressFeed = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQL("21.egressFeed"),
				"data." + DgsConstants.QUERY.ActionFeed,
				new TypeRef<>() {});

		assertThat(egressFeed.size()).isEqualTo(1);

		DeltaFile deltaFile = egressFeed.get(0);
		assertThat(deltaFile.getDid()).isEqualTo(did);
		assertThat(deltaFile.getFormattedData().size()).isEqualTo(1);
		assertThat(deltaFile.getFormattedData().get(0).getFormatAction()).isEqualTo("SampleFormatAction");
		assertThat(deltaFile.getFormattedData().get(0).getObjectReference()).isNotNull();
		assertThat(deltaFile.getFormattedData().get(0).getObjectReference().getBucket()).isEqualTo("formattedBucketName");
		assertThat(deltaFile.getFormattedData().get(0).getObjectReference().getName()).isEqualTo("formattedObjectName");
		assertThat(deltaFile.getFormattedData().get(0).getObjectReference().getSize()).isEqualTo(1000);
		assertThat(deltaFile.getFormattedData().get(0).getObjectReference().getOffset()).isZero();
	}

	@Test
	void test21EgressFeedTimeout() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postValidateAuthorityDeltaFile(did));

		List<DeltaFile> egressFeed = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQL("21.egressFeed"),
				"data." + DgsConstants.QUERY.ActionFeed,
				new TypeRef<>() {});

		assertThat(egressFeed.size()).isEqualTo(1);

		egressFeed = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQL("21.egressFeed"),
				"data." + DgsConstants.QUERY.ActionFeed,
				new TypeRef<>() {});

		// we've queried the feed within the last 30 seconds, so we should not get the DeltaFile again
		assertTrue(egressFeed.isEmpty());

		// get the latest version from the DB to avoid lock failure and artificially age the last feed dispatch
		// so that we are reissued the DeltaFile
		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		deltaFile.getActions().get(7).setModified(OffsetDateTime.now().minusSeconds(31));
		deltaFilesService.addDeltaFile(deltaFile);

		egressFeed = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQL("21.egressFeed"),
				"data." + DgsConstants.QUERY.ActionFeed,
				new TypeRef<>() {});

		assertThat(egressFeed.size()).isEqualTo(1);
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

		DeltaFile deltaFile = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("22.egress"), did),
				"data." + DgsConstants.MUTATION.Egress,
				DeltaFile.class);

		assertThat(deltaFile.getDid()).isEqualTo(did);
		assertThat(deltaFile.getStage()).isEqualTo(DeltaFileStage.COMPLETE.name());
		assertTrue(deltaFile.queuedActions().isEmpty());
		// the actions completed in a different order than they were queued.  Sort the lists alphabetically so they match
		List<String> expectedActions = new ArrayList<>(completedActions.subList(0, 8));
		Collections.sort(expectedActions);
		List<String> actualActions = new ArrayList<>(deltaFile.completedActions());
		Collections.sort(actualActions);
		assertThat(expectedActions).isEqualTo(actualActions);

		assertTrue(equalIgnoringDates(postEgressDeltaFile(did), deltaFilesService.getDeltaFile(did)));
	}

	@Test
	void test22EgressDeleteCompleted() throws IOException {
		deltaFiProperties.getDelete().setOnCompletion(true);

		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postValidateAuthorityDeltaFile(did));

		assertNotNull(deltaFilesService.getDeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("22.egress"), did),
				"data." + DgsConstants.MUTATION.Egress,
				DeltaFile.class);

		assertNull(deltaFilesService.getDeltaFile(did));
	}

	@Test
	void test23DeleteFeed() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postEgressDeltaFile(did));

		List<DeltaFile> deleteFeed = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQL("23.deleteFeed"),
				"data." + DgsConstants.QUERY.ActionFeed,
				new TypeRef<>() {});

		assertTrue(deleteFeed.isEmpty());

		DeltaFile deltaFileToRemove = deltaFilesService.getDeltaFile(did);
		deltaFileToRemove.markForDelete(DeleteConstants.DELETE_ACTION, "becauseISaidSo");
		deltaFilesService.addDeltaFile(deltaFileToRemove);

		deleteFeed = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQL("23.deleteFeed"),
				"data." + DgsConstants.QUERY.ActionFeed,
				new TypeRef<>() {});

		assertThat(deleteFeed.size()).isEqualTo(1);
		DeltaFile deltaFile = deleteFeed.get(0);
		assertThat(deltaFile.getDid()).isEqualTo(did);
	}

	@Test
	void test24Delete() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFilesService.addDeltaFile(postEgressDeltaFile(did));
		assertTrue(deltaFilesService.getDeltaFile(did) != null);

		List<String> dids = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("24.delete"), did),
				"data." + DgsConstants.MUTATION.Delete,
				new TypeRef<>() {});

		assertThat(dids.size()).isEqualTo(2);
		assertTrue(dids.contains(did));
		assertTrue(deltaFilesService.getDeltaFile(did) == null);
	}
}
