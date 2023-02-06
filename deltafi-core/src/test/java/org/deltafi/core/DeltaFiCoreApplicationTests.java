/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import io.minio.MinioClient;
import lombok.SneakyThrows;
import org.apache.nifi.util.FlowFilePackagerV1;
import org.deltafi.common.action.ActionEventQueue;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.Segment;
import org.deltafi.common.resource.Resource;
import org.deltafi.common.types.*;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.datafetchers.FlowPlanDatafetcherTestHelper;
import org.deltafi.core.datafetchers.PropertiesDatafetcherTestHelper;
import org.deltafi.core.delete.DeletePolicyWorker;
import org.deltafi.core.delete.DeleteRunner;
import org.deltafi.core.generated.DgsConstants;
import org.deltafi.core.generated.client.*;
import org.deltafi.core.generated.types.ConfigType;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.metrics.MetricRepository;
import org.deltafi.core.plugin.PluginRepository;
import org.deltafi.core.plugin.deployer.DeployerService;
import org.deltafi.core.plugin.deployer.credential.CredentialProvider;
import org.deltafi.core.plugin.deployer.image.PluginImageRepository;
import org.deltafi.core.plugin.deployer.image.PluginImageRepositoryRepo;
import org.deltafi.core.repo.*;
import org.deltafi.core.services.*;
import org.deltafi.core.types.FlowAssignmentRule;
import org.deltafi.core.types.PluginVariables;
import org.deltafi.core.types.*;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Stream;

import static graphql.Assert.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.deltafi.common.constant.DeltaFiConstants.INGRESS_ACTION;
import static org.deltafi.common.constant.DeltaFiConstants.USER_HEADER;
import static org.deltafi.common.test.TestConstants.MONGODB_CONTAINER;
import static org.deltafi.core.Util.assertEqualsIgnoringDates;
import static org.deltafi.core.Util.buildDeltaFile;
import static org.deltafi.core.datafetchers.DeletePolicyDatafetcherTestHelper.*;
import static org.deltafi.core.datafetchers.DeltaFilesDatafetcherTestHelper.*;
import static org.deltafi.core.datafetchers.FlowAssignmentDatafetcherTestHelper.*;
import static org.deltafi.core.metrics.MetricsUtil.tagsFor;
import static org.deltafi.core.plugin.PluginDataFetcherTestHelper.*;
import static org.deltafi.core.rest.IngressRest.FLOWFILE_V1_MEDIA_TYPE;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
		"schedule.actionEvents=false",
		"schedule.maintenance=false",
		"schedule.flowSync=false",
		"schedule.diskSpace=false",
		"schedule.propertySync=false"})
@Testcontainers
class DeltaFiCoreApplicationTests {

	@Container
	public final static MongoDBContainer mongoDBContainer = new MongoDBContainer(MONGODB_CONTAINER);

	@DynamicPropertySource
	static void setProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
	}

	@Autowired
	DgsQueryExecutor dgsQueryExecutor;

	@Autowired
	DeltaFilesService deltaFilesService;

	@Autowired
	DeltaFiPropertiesService deltaFiPropertiesService;

	@Autowired
	DeletePolicyService deletePolicyService;

	@Autowired
	DeleteRunner deleteRunner;

	@Autowired
	DeltaFileRepo deltaFileRepo;

	@Autowired
	ActionDescriptorRepo actionDescriptorRepo;

	@Autowired
	DeletePolicyRepo deletePolicyRepo;

	@Autowired
	FlowAssignmentRuleRepo flowAssignmentRuleRepo;

	@Autowired
	PluginRepository pluginRepository;

	@Autowired
	IngressFlowService ingressFlowService;

	@Autowired
	IngressFlowPlanService ingressFlowPlanService;

	@Autowired
	EgressFlowService egressFlowService;

	@Autowired
	IngressFlowRepo ingressFlowRepo;

	@Autowired
	EgressFlowRepo egressFlowRepo;

	@Autowired
	IngressFlowPlanRepo ingressFlowPlanRepo;

	@Autowired
	EgressFlowPlanRepo egressFlowPlanRepo;

	@Autowired
	EnrichFlowPlanRepo enrichFlowPlanRepo;

	@Autowired
	EnrichFlowRepo enrichFlowRepo;

	@Autowired
	EnrichFlowService enrichFlowService;

	@Autowired
	PluginVariableRepo pluginVariableRepo;

	@Autowired
	DeltaFiPropertiesRepo deltaFiPropertiesRepo;

	@Autowired
	EgressFlowPlanService egressFlowPlanService;

	@Autowired
	PluginImageRepositoryRepo pluginImageRepositoryRepo;

	@MockBean
	StorageConfigurationService storageConfigurationService;

	@MockBean
	DiskSpaceService diskSpaceService;

	@Captor
	ArgumentCaptor<List<ActionInput>> actionInputListCaptor;

	@Autowired
	TestRestTemplate restTemplate;

	@MockBean
	IngressService ingressService;

	@MockBean
	MetricRepository metricRepository;

	@MockBean
	ActionEventQueue actionEventQueue;

	@MockBean
	DeployerService deployerService;

	@MockBean
	CredentialProvider credentialProvider;

	static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

	// mongo eats microseconds, jump through hoops
	private final OffsetDateTime MONGO_NOW =  OffsetDateTime.of(LocalDateTime.ofEpochSecond(OffsetDateTime.now().toInstant().toEpochMilli(), 0, ZoneOffset.UTC), ZoneOffset.UTC);

	private final OffsetDateTime START_TIME = OffsetDateTime.of(2021, 7, 11, 13, 44, 22, 183, ZoneOffset.UTC);
	private final OffsetDateTime STOP_TIME = OffsetDateTime.of(2021, 7, 11, 13, 44, 22, 184, ZoneOffset.UTC);

	private static final String INGRESS_FLOW_NAME = "sampleIngress";
	private static final String EGRESS_FLOW_NAME = "sampleEgress";

	final static Map<String, String> loadSampleMetadata = Map.of("loadSampleType", "load-sample-type", "loadSampleVersion", "2.2");
	final static Map<String, String> loadWrongMetadata = Map.of("loadSampleType", "wrong-sample-type", "loadSampleVersion", "2.2");
	final static Map<String, String> transformSampleMetadata = Map.of("sampleType", "sample-type", "sampleVersion", "2.1");


	@TestConfiguration
	public static class Config {
		@Bean
		public DeltaFileIndexService deltaFileIndexService(DeltaFileRepo deltaFileRepo, DeltaFiPropertiesService deltaFiPropertiesService) {
			return new DeltaFileIndexService(deltaFileRepo, deltaFiPropertiesService);
		}

		@Bean
		public StorageConfigurationService storageConfigurationService(MinioClient minioClient, DeltaFiPropertiesService deltaFiPropertiesService) {
			return new StorageConfigurationService(minioClient, deltaFiPropertiesService);
		}
	}

	@BeforeEach
	@SneakyThrows
	void setup() {
		actionDescriptorRepo.deleteAll();
		deltaFileRepo.deleteAll();
		deltaFiPropertiesRepo.save(new DeltaFiProperties());
		deltaFiPropertiesService.getDeltaFiProperties().getDelete().setOnCompletion(false);
		deltaFileRepo.deleteAll();
		flowAssignmentRuleRepo.deleteAll();
		loadConfig();

		Mockito.clearInvocations(actionEventQueue);

		// Set the security context for the tests that DgsQueryExecutor
		SecurityContextHolder.clearContext();
		SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
		Authentication authentication = new PreAuthenticatedAuthenticationToken("name", "pass", List.of(new SimpleGrantedAuthority(DeltaFiConstants.ADMIN_PERMISSION)));
		securityContext.setAuthentication(authentication);
		SecurityContextHolder.setContext(securityContext);

		Mockito.when(ingressService.isEnabled()).thenReturn(true);
		Mockito.when(diskSpaceService.isContentStorageDepleted()).thenReturn(false);
	}

	void loadConfig() {
		loadIngressConfig();
		loadEnrichConfig();
		loadEgressConfig();
	}

	void loadIngressConfig() {
		ingressFlowRepo.deleteAll();

		LoadActionConfiguration lc = new LoadActionConfiguration("sampleIngress.SampleLoadAction", "type");
		TransformActionConfiguration tc = new TransformActionConfiguration("sampleIngress.Utf8TransformAction", "type");
		TransformActionConfiguration tc2 = new TransformActionConfiguration("sampleIngress.SampleTransformAction", "type");

		IngressFlow sampleIngressFlow = buildRunningFlow(INGRESS_FLOW_NAME, lc, List.of(tc, tc2), false);
		IngressFlow retryFlow = buildRunningFlow("theFlow", lc, null, false);
		IngressFlow childFlow = buildRunningFlow("childFlow", lc, List.of(tc2), false);

		ingressFlowRepo.saveAll(List.of(sampleIngressFlow, retryFlow, childFlow));
		ingressFlowService.refreshCache();
	}

	void configureTestIngress() {
		LoadActionConfiguration lc = new LoadActionConfiguration("sampleIngress.SampleLoadAction", "type");
		TransformActionConfiguration tc = new TransformActionConfiguration("sampleIngress.Utf8TransformAction", "type");
		TransformActionConfiguration tc2 = new TransformActionConfiguration("sampleIngress.SampleTransformAction", "type");

		IngressFlow sampleIngressFlow = buildRunningFlow(INGRESS_FLOW_NAME, lc, List.of(tc, tc2), true);
		ingressFlowRepo.save(sampleIngressFlow);
		ingressFlowService.refreshCache();
	}

	void configureTestEgress() {
			ValidateActionConfiguration authValidate = new ValidateActionConfiguration("sampleEgress.AuthorityValidateAction", "type");
			ValidateActionConfiguration sampleValidate = new ValidateActionConfiguration("sampleEgress.SampleValidateAction", "type");

			FormatActionConfiguration sampleFormat = new FormatActionConfiguration("sampleEgress.SampleFormatAction", "type", List.of("sampleDomain"));
			sampleFormat.setRequiresEnrichments(List.of("sampleEnrichment"));

			EgressActionConfiguration sampleEgress = new EgressActionConfiguration("sampleEgress.SampleEgressAction", "type");

			EgressFlow sampleEgressFlow = buildRunningFlow(EGRESS_FLOW_NAME, sampleFormat, sampleEgress, true);
			sampleEgressFlow.setValidateActions(List.of(authValidate, sampleValidate));

			egressFlowRepo.save(sampleEgressFlow);
			egressFlowService.refreshCache();
	}

    void loadEgressConfig() {
		egressFlowRepo.deleteAll();

		ValidateActionConfiguration authValidate = new ValidateActionConfiguration("sampleEgress.AuthorityValidateAction", "type");
		ValidateActionConfiguration sampleValidate = new ValidateActionConfiguration("sampleEgress.SampleValidateAction", "type");

		FormatActionConfiguration sampleFormat = new FormatActionConfiguration("sampleEgress.SampleFormatAction", "type", List.of("sampleDomain"));
		sampleFormat.setRequiresEnrichments(List.of("sampleEnrichment"));

		EgressActionConfiguration sampleEgress = new EgressActionConfiguration("sampleEgress.SampleEgressAction", "type");

		EgressFlow sampleEgressFlow = buildRunningFlow(EGRESS_FLOW_NAME, sampleFormat, sampleEgress, false);
		sampleEgressFlow.setValidateActions(List.of(authValidate, sampleValidate));

		FormatActionConfiguration errorFormat = new FormatActionConfiguration("sampleEgress.ErrorFormatAction", "type", List.of("error"));
		EgressActionConfiguration errorEgress = new EgressActionConfiguration("sampleEgress.ErrorEgressAction", "type");
		EgressFlow errorFlow = buildRunningFlow("error", errorFormat, errorEgress, false);

		egressFlowRepo.saveAll(List.of(sampleEgressFlow, errorFlow));
		egressFlowService.refreshCache();
    }

	void loadEnrichConfig() {
		enrichFlowRepo.deleteAll();
		enrichFlowRepo.save(buildEnrichFlow(FlowState.RUNNING));
		enrichFlowService.refreshCache();
	}


	@Test
	void contextLoads() {
		assertTrue(true);
		ConfigQueryInput input = ConfigQueryInput.newBuilder().configType(ConfigType.INGRESS_FLOW).build();
		assertFalse(ingressFlowService.getConfigs(input).isEmpty());
	}

	@Test
	void testReplaceAllDeletePolicies() {
		Result result = replaceAllDeletePolicies(dgsQueryExecutor);
		assertTrue(result.isSuccess());
		assertTrue(result.getErrors().isEmpty());
		assertEquals(3, deletePolicyRepo.count());
	}

	@Test
	void testDuplicatePolicyName() {
		Result result = replaceAllDeletePolicies(dgsQueryExecutor);
		assertTrue(result.isSuccess());
		assertTrue(result.getErrors().isEmpty());
		assertEquals(3, deletePolicyRepo.count());

		Result duplicate = addOnePolicy(dgsQueryExecutor);
		assertFalse(duplicate.isSuccess());
		assertTrue(duplicate.getErrors().contains("duplicate policy name"));
	}

	@Test
	void testRemoveDeletePolicy() {
		replaceAllDeletePolicies(dgsQueryExecutor);
		assertEquals(3, deletePolicyRepo.count());
		String id = getIdByPolicyName(AFTER_COMPLETE_LOCKED_POLICY);
		assertTrue(removeDeletePolicy(dgsQueryExecutor, id));
		assertEquals(2, deletePolicyRepo.count());
		assertFalse(removeDeletePolicy(dgsQueryExecutor, id));
	}

	@Test
	void testEnablePolicy() {
		replaceAllDeletePolicies(dgsQueryExecutor);
		assertTrue(enablePolicy(dgsQueryExecutor, getIdByPolicyName(OFFLINE_POLICY), true));
		assertFalse(enablePolicy(dgsQueryExecutor, getIdByPolicyName(AFTER_COMPLETE_LOCKED_POLICY), false));
	}

	private String getIdByPolicyName(String name) {
		for (DeletePolicy policy : getDeletePolicies(dgsQueryExecutor)) {
			if (policy.getName().equals(name)) {
				return policy.getId();
			}
		}
		return null;
	}

	@Test
	void testUpdateDiskSpaceDeletePolicy() {
		loadOneDeletePolicy(dgsQueryExecutor);
		assertEquals(1, deletePolicyRepo.count());
		String idToUpdate = getIdByPolicyName(DISK_SPACE_PERCENT_POLICY);

		Result validationError = updateDiskSpaceDeletePolicy(dgsQueryExecutor,
				DiskSpaceDeletePolicy.newBuilder()
						.id(idToUpdate)
						.name(DISK_SPACE_PERCENT_POLICY)
						.maxPercent(-1)
						.locked(false)
						.enabled(false)
						.build());
		checkUpdateResult(true, validationError, "maxPercent is invalid", idToUpdate, DISK_SPACE_PERCENT_POLICY, true);

		Result updateNameIsGood = updateDiskSpaceDeletePolicy(dgsQueryExecutor,
				DiskSpaceDeletePolicy.newBuilder()
						.id(idToUpdate)
						.name("newName")
						.maxPercent(50)
						.locked(false)
						.enabled(false)
						.build());
		checkUpdateResult(true, updateNameIsGood, null, idToUpdate, "newName", false);

		Result notFoundError = updateDiskSpaceDeletePolicy(dgsQueryExecutor,
				DiskSpaceDeletePolicy.newBuilder()
						.id("wrongId")
						.name("blah")
						.maxPercent(50)
						.locked(false)
						.enabled(true)
						.build());
		checkUpdateResult(true, notFoundError, "policy not found", idToUpdate, "newName", false);

		Result missingId = updateDiskSpaceDeletePolicy(dgsQueryExecutor,
				DiskSpaceDeletePolicy.newBuilder()
						.name("blah")
						.maxPercent(50)
						.locked(false)
						.enabled(true)
						.build());
		checkUpdateResult(true, missingId, "id is missing", idToUpdate, "newName", false);

		addOnePolicy(dgsQueryExecutor);
		assertEquals(2, deletePolicyRepo.count());
		String secondId = getIdByPolicyName(DISK_SPACE_PERCENT_POLICY);
		Assertions.assertNotNull(secondId);
		assertNotEquals(secondId, idToUpdate);

		Result duplicateName = updateDiskSpaceDeletePolicy(dgsQueryExecutor,
				DiskSpaceDeletePolicy.newBuilder()
						.id(idToUpdate)
						.name(DISK_SPACE_PERCENT_POLICY)
						.maxPercent(60)
						.locked(false)
						.enabled(false)
						.build());
		assertFalse(duplicateName.isSuccess());
		assertTrue(duplicateName.getErrors().contains("duplicate policy name"));
	}

	@Test
	void testUpdateTimedDeletePolicy() {
		loadOneDeletePolicy(dgsQueryExecutor);
		assertEquals(1, deletePolicyRepo.count());
		String idToUpdate = getIdByPolicyName(DISK_SPACE_PERCENT_POLICY);

		Result validationError = updateTimedDeletePolicy(dgsQueryExecutor,
				TimedDeletePolicy.newBuilder()
						.id(idToUpdate)
						.name("blah")
						.afterComplete("ABC")
						.locked(false)
						.enabled(false)
						.build());
		checkUpdateResult(true, validationError, "Unable to parse duration for afterComplete", idToUpdate, DISK_SPACE_PERCENT_POLICY, true);

		Result notFoundError = updateTimedDeletePolicy(dgsQueryExecutor,
				TimedDeletePolicy.newBuilder()
						.id("wrongId")
						.name("blah")
						.afterComplete("PT1H")
						.locked(false)
						.enabled(true)
						.build());
		checkUpdateResult(true, notFoundError, "policy not found", idToUpdate, DISK_SPACE_PERCENT_POLICY, true);

		Result goodUpdate = updateTimedDeletePolicy(dgsQueryExecutor,
				TimedDeletePolicy.newBuilder()
						.id(idToUpdate)
						.name("newTypesAndName")
						.afterComplete("PT1H")
						.locked(false)
						.enabled(false)
						.build());
		checkUpdateResult(false, goodUpdate, null, idToUpdate, "newTypesAndName", false);
	}

	private void checkUpdateResult(boolean disk, Result result, String error, String id, String name, boolean enabled) {
		if (error == null) {
			assertTrue(result.isSuccess());
		} else {
			assertFalse(result.isSuccess());
			assertTrue(result.getErrors().contains(error));
		}

		assertEquals(1, deletePolicyRepo.count());
		List<DeletePolicy> policyList = getDeletePolicies(dgsQueryExecutor);
		assertEquals(1, policyList.size());
		assertEquals(id, policyList.get(0).getId());
		assertEquals(name, policyList.get(0).getName());
		assertEquals(enabled, policyList.get(0).isEnabled());

		if (disk) {
			assertTrue(policyList.get(0) instanceof DiskSpaceDeletePolicy);
		} else {
			assertTrue(policyList.get(0) instanceof TimedDeletePolicy);
		}
	}

	@Test
	void testGetDeletePolicies() {
		replaceAllDeletePolicies(dgsQueryExecutor);
		List<DeletePolicy> policyList = getDeletePolicies(dgsQueryExecutor);
		assertEquals(3, policyList.size());

		boolean foundAfterCompleteLockedPolicy = false;
		boolean foundOfflinePolicy = false;
		boolean foundDiskSpacePercent = false;
		Set<String> ids = new HashSet<>();

		for (DeletePolicy policy : policyList) {
			ids.add(policy.getId());
			if (policy instanceof DiskSpaceDeletePolicy diskPolicy) {
				if (diskPolicy.getName().equals(DISK_SPACE_PERCENT_POLICY)) {
					assertTrue(diskPolicy.isEnabled());
					assertFalse(diskPolicy.isLocked());
					foundDiskSpacePercent = true;
				}
			} else if (policy instanceof TimedDeletePolicy timedPolicy) {
				if (timedPolicy.getName().equals(AFTER_COMPLETE_LOCKED_POLICY)) {
					assertTrue(timedPolicy.isEnabled());
					assertTrue(timedPolicy.isLocked());
					assertEquals("PT2S", timedPolicy.getAfterComplete());
					assertNull(timedPolicy.getAfterCreate());
					assertNull(timedPolicy.getMinBytes());
					foundAfterCompleteLockedPolicy = true;

				} else if (timedPolicy.getName().equals(OFFLINE_POLICY)) {
					assertFalse(timedPolicy.isEnabled());
					assertFalse(timedPolicy.isLocked());
					assertEquals("PT2S", timedPolicy.getAfterCreate());
					assertNull(timedPolicy.getAfterComplete());
					assertEquals(1000, timedPolicy.getMinBytes());
					foundOfflinePolicy = true;
				}
			}
		}

		assertTrue(foundAfterCompleteLockedPolicy);
		assertTrue(foundOfflinePolicy);
		assertTrue(foundDiskSpacePercent);
		assertEquals(3, ids.size());
	}

	@Test
	void testDeleteRunnerPoliciesScheduled() {
		replaceAllDeletePolicies(dgsQueryExecutor);
		assertThat(deletePolicyRepo.count()).isEqualTo(3);
		List<DeletePolicyWorker> policiesScheduled = deleteRunner.refreshPolicies();
		assertThat(policiesScheduled.size()).isEqualTo(2); // only 2 of 3 are enabled
		List<String> names = List.of(policiesScheduled.get(0).getName(),
				policiesScheduled.get(1).getName());
		assertTrue(names.containsAll(List.of(DISK_SPACE_PERCENT_POLICY, AFTER_COMPLETE_LOCKED_POLICY)));
	}

	@Test
	void deletePoliciesDontRaise() {
		deleteRunner.runDeletes();
	}

	private ActionEventInput actionEvent(String filename, String did) throws IOException {
		String json = String.format(new String(Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("full-flow/" + filename + ".json")).readAllBytes()), did);
		return OBJECT_MAPPER.readValue(json, ActionEventInput.class);
	}

	private ActionEventInput filterActionEvent(String did, String filteredAction) throws IOException {
		String json = String.format(new String(Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("full-flow/filter.json")).readAllBytes()), did, filteredAction);
		return OBJECT_MAPPER.readValue(json, ActionEventInput.class);
	}

	private String graphQL(String filename) throws IOException {
		return new String(Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("full-flow/" + filename + ".graphql")).readAllBytes());
	}

	DeltaFile postIngressDeltaFile(String did) {
		DeltaFile deltaFile = Util.emptyDeltaFile(did, "flow");
		deltaFile.setIngressBytes(500L);
		deltaFile.queueAction("sampleIngress.Utf8TransformAction");
		deltaFile.setSourceInfo(new SourceInfo("input.txt", INGRESS_FLOW_NAME, new HashMap<>(Map.of("AuthorizedBy", "XYZ", "removeMe", "whatever"))));
		Content content = Content.newBuilder().contentReference(new ContentReference("application/octet-stream", new Segment("objectName", 0, 500, did))).build();
		deltaFile.getProtocolStack().add(new ProtocolLayer(INGRESS_ACTION, List.of(content), null));
		return deltaFile;
	}

	DeltaFile postTransformUtf8DeltaFile(String did) {
		DeltaFile deltaFile = postIngressDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.INGRESS);
		deltaFile.completeAction("sampleIngress.Utf8TransformAction", START_TIME, STOP_TIME);
		deltaFile.queueAction("sampleIngress.SampleTransformAction");
		Content content = Content.newBuilder().name("file.json").contentReference(new ContentReference("application/octet-stream", new Segment("utf8ObjectName", 0, 500, did))).build();
		deltaFile.getProtocolStack().add(new ProtocolLayer("sampleIngress.Utf8TransformAction", List.of(content), null));
		return deltaFile;
	}

	@Test
	void testTransformUtf8() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postIngressDeltaFile(did));

		deltaFilesService.handleActionEvent(actionEvent("transformUtf8", did));

		verifyActionEventResults(postTransformUtf8DeltaFile(did), "sampleIngress.SampleTransformAction");
		Map<String, String> tags = tagsFor(ActionEventType.TRANSFORM, "sampleIngress.Utf8TransformAction", INGRESS_FLOW_NAME, null);
		Mockito.verify(metricRepository).increment(new Metric(DeltaFiConstants.FILES_IN, 1).addTags(tags));
		Mockito.verifyNoMoreInteractions(metricRepository);
	}

	DeltaFile postTransformDeltaFile(String did) {
		DeltaFile deltaFile = postTransformUtf8DeltaFile(did);
		deltaFile.setStage(DeltaFileStage.INGRESS);
		deltaFile.completeAction("sampleIngress.SampleTransformAction", START_TIME, STOP_TIME);
		deltaFile.queueAction("sampleIngress.SampleLoadAction");
		Content content = Content.newBuilder().contentReference(new ContentReference("application/octet-stream", new Segment("objectName", 0, 500, did))).build();
		deltaFile.getProtocolStack().add(new ProtocolLayer("sampleIngress.SampleTransformAction", List.of(content), transformSampleMetadata));
		return deltaFile;
	}

	@Test
	void testTransform() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postTransformUtf8DeltaFile(did));

		deltaFilesService.handleActionEvent(actionEvent("transform", did));

		verifyActionEventResults(postTransformDeltaFile(did), "sampleIngress.SampleLoadAction");

		Map<String, String> tags = tagsFor(ActionEventType.TRANSFORM, "sampleIngress.SampleTransformAction", INGRESS_FLOW_NAME, null);
		Mockito.verify(metricRepository).increment(new Metric(DeltaFiConstants.FILES_IN, 1).addTags(tags));
		Mockito.verifyNoMoreInteractions(metricRepository);

	}

	DeltaFile postTransformHadErrorDeltaFile(String did) {
		DeltaFile deltaFile = postTransformUtf8DeltaFile(did);
		deltaFile.setStage(DeltaFileStage.ERROR);
		deltaFile.errorAction("sampleIngress.SampleTransformAction", START_TIME, STOP_TIME, "transform failed", "message");
		/*
		 * Even though this action is being used as an ERROR, fake its
		 * protocol layer results so that we can verify the State Machine
		 * will still recognize that Transform actions are incomplete,
		 * and not attempt to queue the Load action, too.
		 */
		Content content = Content.newBuilder().contentReference(new ContentReference("application/octet-stream", new Segment("objectName", 0, 500, did))).build();
		deltaFile.getProtocolStack().add(new ProtocolLayer("sampleIngress.SampleTransformAction", List.of(content), transformSampleMetadata));
		return deltaFile;
	}

	@SuppressWarnings("SameParameterValue")
	DeltaFile postResumeTransformDeltaFile(String did, String retryAction) {
		DeltaFile deltaFile = postTransformHadErrorDeltaFile(did);
		deltaFile.retryErrors();
		deltaFile.setStage(DeltaFileStage.INGRESS);
		deltaFile.getActions().add(Action.newBuilder().name(retryAction).state(ActionState.QUEUED).build());
		deltaFile.getSourceInfo().setMetadata(Map.of("AuthorizedBy", "ABC", "removeMe.original", "whatever", "AuthorizedBy.original", "XYZ", "anotherKey", "anotherValue"));
		return deltaFile;
	}

	@Test
	void testResumeTransform() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postTransformHadErrorDeltaFile(did));

		List<RetryResult> retryResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("resumeTransform"), did),
				"data." + DgsConstants.MUTATION.Resume,
				new TypeRef<>() {});

		assertEquals(1, retryResults.size());
		assertEquals(did, retryResults.get(0).getDid());
		assertTrue(retryResults.get(0).getSuccess());

		DeltaFile expected = postResumeTransformDeltaFile(did, "sampleIngress.SampleTransformAction");
		verifyActionEventResults(expected, "sampleIngress.SampleTransformAction");

		Mockito.verifyNoInteractions(metricRepository);
	}

	@Test
	void testResumeContentDeleted() throws IOException {
		String did = UUID.randomUUID().toString();
		DeltaFile contentDeletedDeltaFile = postTransformHadErrorDeltaFile(did);
		contentDeletedDeltaFile.setContentDeleted(OffsetDateTime.now());
		contentDeletedDeltaFile.setContentDeletedReason("aged off");
		deltaFileRepo.save(contentDeletedDeltaFile);

		List<RetryResult> retryResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("resumeTransform"), did),
				"data." + DgsConstants.MUTATION.Resume,
				new TypeRef<>() {});

		assertEquals(1, retryResults.size());
		assertEquals(did, retryResults.get(0).getDid());
		assertFalse(retryResults.get(0).getSuccess());

		Mockito.verifyNoInteractions(metricRepository);

	}

	DeltaFile postLoadDeltaFile(String did) {
		DeltaFile deltaFile = postTransformDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.ENRICH);
		deltaFile.queueAction("sampleEnrich.SampleDomainAction");
		deltaFile.completeAction("sampleIngress.SampleLoadAction", START_TIME, STOP_TIME);
		deltaFile.addDomain("sampleDomain", "sampleDomainValue", "application/octet-stream");
		Content content = Content.newBuilder().contentReference(new ContentReference("application/octet-stream", new Segment("objectName", 0, 500, did))).build();
		deltaFile.getProtocolStack().add(new ProtocolLayer("sampleIngress.SampleLoadAction", List.of(content), loadSampleMetadata));
		return deltaFile;
	}

	@Test
	void testLoad() throws IOException {
		String did = UUID.randomUUID().toString();
		DeltaFile postTransform = postTransformDeltaFile(did);
		deltaFileRepo.save(postTransform);

		deltaFilesService.handleActionEvent(actionEvent("load", did));

		verifyActionEventResults(postLoadDeltaFile(did), "sampleEnrich.SampleDomainAction");

		Map<String, String> tags = tagsFor(ActionEventType.LOAD, "sampleIngress.SampleLoadAction", INGRESS_FLOW_NAME, null);
		Mockito.verify(metricRepository).increment(new Metric(DeltaFiConstants.FILES_IN, 1).addTags(tags));
		Mockito.verifyNoMoreInteractions(metricRepository);
	}

	DeltaFile post09MissingEnrichDeltaFile(String did) {
		DeltaFile deltaFile = postLoadDeltaFile(did);
		deltaFile.completeAction("sampleEnrich.SampleDomainAction", START_TIME, STOP_TIME);
		deltaFile.addIndexedMetadata(Map.of("domainKey", "domain metadata"));
		deltaFile.setStage(DeltaFileStage.ERROR);
		deltaFile.queueNewAction(DeltaFiConstants.NO_EGRESS_FLOW_CONFIGURED_ACTION);
		deltaFile.errorAction(DeltaFilesService.buildNoEgressConfiguredErrorEvent(deltaFile));
		deltaFile.addDomain("sampleDomain", "sampleDomainValue", "application/octet-stream");
		deltaFile.getLastProtocolLayer().setMetadata(loadWrongMetadata);
		return deltaFile;
	}

	@Test
	void testEnrichSkippedWrongMetadata() throws IOException {
		// Test is similar to load, but has the wrong metadata value, which
		// results in the enrich action not being run, and cascades through.
		String did = UUID.randomUUID().toString();
		DeltaFile loaded = postLoadDeltaFile(did);

		// mock loading the incorrect metadata so the enrichAction is not fired
		loaded.getLastProtocolLayer().setMetadata(loadWrongMetadata);
		deltaFileRepo.save(loaded);

		deltaFilesService.handleActionEvent(actionEvent("domain", did));

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);

		assertEqualsIgnoringDates(post09MissingEnrichDeltaFile(did), deltaFile);
		Map<String, String> tags = tagsFor(ActionEventType.DOMAIN, "sampleEnrich.SampleDomainAction", INGRESS_FLOW_NAME, null);
		Mockito.verify(metricRepository).increment(new Metric(DeltaFiConstants.FILES_IN, 1).addTags(tags));
		Map<String, String> tags2 = tagsFor("unknown", "NoEgressFlowConfiguredAction", INGRESS_FLOW_NAME, null);
		Mockito.verify(metricRepository).increment(new Metric(DeltaFiConstants.FILES_ERRORED, 1).addTags(tags2));
		Mockito.verifyNoMoreInteractions(metricRepository);

	}

	@Test
	void testSplit() throws IOException {
		String did = UUID.randomUUID().toString();
		DeltaFile postTransform = postTransformDeltaFile(did);
		deltaFileRepo.save(postTransform);

		deltaFilesService.handleActionEvent(actionEvent("split", did));

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
		assertEquals(0, child1.getLastProtocolLayerContent().get(0).getContentReference().getSegments().get(0).getOffset());
		assertEquals(2, child1.getLastProtocolLayerContent().size());

		DeltaFile child2 = children.get(1);
		assertEquals(DeltaFileStage.INGRESS, child2.getStage());
		assertEquals(Collections.singletonList(deltaFile.getDid()), child2.getParentDids());
		assertEquals("file2", child2.getSourceInfo().getFilename());
		assertEquals(250, child2.getLastProtocolLayerContent().get(0).getContentReference().getSegments().get(0).getOffset());
		assertEquals(1, child2.getLastProtocolLayerContent().size());

		Mockito.verify(actionEventQueue).putActions(actionInputListCaptor.capture());
		List<ActionInput> actionInputs = actionInputListCaptor.getValue();
		assertThat(actionInputs).hasSize(2);

		assertEqualsIgnoringDates(child1.forQueue("sampleIngress.SampleTransformAction"), actionInputs.get(0).getDeltaFile());
		assertEqualsIgnoringDates(child2.forQueue("sampleIngress.SampleTransformAction"), actionInputs.get(1).getDeltaFile());

		Map<String, String> tags = tagsFor(ActionEventType.SPLIT, "sampleIngress.SampleLoadAction", INGRESS_FLOW_NAME, null);
		Mockito.verify(metricRepository).increment(new Metric(DeltaFiConstants.FILES_IN, 1).addTags(tags));
		Mockito.verifyNoMoreInteractions(metricRepository);
	}

	DeltaFile postDomainDeltaFile(String did) {
		DeltaFile deltaFile = postLoadDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.ENRICH);
		deltaFile.queueAction("sampleEnrich.SampleEnrichAction");
		deltaFile.completeAction("sampleEnrich.SampleDomainAction", START_TIME, STOP_TIME);
		deltaFile.addIndexedMetadata(Map.of("domainKey", "domain metadata"));
		return deltaFile;
	}

	@Test
	void testDomain() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postLoadDeltaFile(did));

		deltaFilesService.handleActionEvent(actionEvent("domain", did));

		verifyActionEventResults(postDomainDeltaFile(did), "sampleEnrich.SampleEnrichAction");

		Map<String, String> tags = tagsFor(ActionEventType.DOMAIN, "sampleEnrich.SampleDomainAction", INGRESS_FLOW_NAME, null);
		Mockito.verify(metricRepository).increment(new Metric(DeltaFiConstants.FILES_IN, 1).addTags(tags));
		Mockito.verifyNoMoreInteractions(metricRepository);

	}

	DeltaFile postEnrichDeltaFile(String did) {
		DeltaFile deltaFile = postDomainDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.EGRESS);
		deltaFile.queueAction("sampleEgress.SampleFormatAction");
		deltaFile.completeAction("sampleEnrich.SampleEnrichAction", START_TIME, STOP_TIME);
		deltaFile.addEnrichment("sampleEnrichment", "enrichmentData");
		deltaFile.addIndexedMetadata(Map.of("first", "one", "second", "two"));
		deltaFile.addEgressFlow(EGRESS_FLOW_NAME);
		return deltaFile;
	}

	@Test
	void testEnrich() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postDomainDeltaFile(did));

		deltaFilesService.handleActionEvent(actionEvent("enrich", did));

		verifyActionEventResults(postEnrichDeltaFile(did), "sampleEgress.SampleFormatAction");
		Map<String, String> tags = tagsFor(ActionEventType.ENRICH, "sampleEnrich.SampleEnrichAction", INGRESS_FLOW_NAME, null);
		Mockito.verify(metricRepository).increment(new Metric(DeltaFiConstants.FILES_IN, 1).addTags(tags));
		Mockito.verifyNoMoreInteractions(metricRepository);
	}

	DeltaFile postFormatDeltaFile(String did) {
		DeltaFile deltaFile = postEnrichDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.EGRESS);
		deltaFile.queueActionsIfNew(Arrays.asList("sampleEgress.AuthorityValidateAction", "sampleEgress.SampleValidateAction"));
		deltaFile.completeAction("sampleEgress.SampleFormatAction", START_TIME, STOP_TIME);
		deltaFile.getFormattedData().add(FormattedData.newBuilder()
				.formatAction("sampleEgress.SampleFormatAction")
				.filename("output.txt")
				.metadata(Map.of("key1", "value1", "key2", "value2"))
				.contentReference(new ContentReference("application/octet-stream", new Segment("formattedObjectName", 0, 1000, did)))
				.egressActions(Collections.singletonList("sampleEgress.SampleEgressAction"))
				.validateActions(List.of("sampleEgress.AuthorityValidateAction", "sampleEgress.SampleValidateAction"))
				.build());
		return deltaFile;
	}

	@Test
	void testFormat() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postEnrichDeltaFile(did));

		deltaFilesService.handleActionEvent(actionEvent("format", did));

		verifyActionEventResults(postFormatDeltaFile(did), "sampleEgress.AuthorityValidateAction", "sampleEgress.SampleValidateAction");
		Map<String, String> tags = tagsFor(ActionEventType.FORMAT, "sampleEgress.SampleFormatAction", INGRESS_FLOW_NAME, EGRESS_FLOW_NAME);
		Mockito.verify(metricRepository).increment(new Metric(DeltaFiConstants.FILES_IN, 1).addTags(tags));
		Mockito.verifyNoMoreInteractions(metricRepository);

	}

	@Test
	void testFormatMany() throws IOException {
		String did = UUID.randomUUID().toString();
		DeltaFile postEnrich = postEnrichDeltaFile(did);
		deltaFileRepo.save(postEnrich);

		deltaFilesService.handleActionEvent(actionEvent("formatMany", did));

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
		assertEquals(0, child1.getFormattedData().get(0).getContentReference().getSegments().get(0).getOffset());

		DeltaFile child2 = children.get(1);
		assertEquals(DeltaFileStage.EGRESS, child2.getStage());
		assertEquals(Collections.singletonList(deltaFile.getDid()), child2.getParentDids());
		assertEquals("input.txt", child2.getSourceInfo().getFilename());
		assertEquals(250, child2.getFormattedData().get(0).getContentReference().getSegments().get(0).getOffset());

		Mockito.verify(actionEventQueue).putActions(actionInputListCaptor.capture());
		assertEquals(4, actionInputListCaptor.getValue().size());
		assertEquals(child1.getDid(), actionInputListCaptor.getValue().get(0).getActionContext().getDid());
		assertEquals(child1.getDid(), actionInputListCaptor.getValue().get(1).getActionContext().getDid());
		assertEquals(child2.getDid(), actionInputListCaptor.getValue().get(2).getActionContext().getDid());
		assertEquals(child2.getDid(), actionInputListCaptor.getValue().get(3).getActionContext().getDid());

		Map<String, String> tags = tagsFor(ActionEventType.FORMAT_MANY, "sampleEgress.SampleFormatAction", INGRESS_FLOW_NAME, EGRESS_FLOW_NAME);
		Mockito.verify(metricRepository).increment(new Metric(DeltaFiConstants.FILES_IN, 1).addTags(tags));
		Mockito.verifyNoMoreInteractions(metricRepository);
	}

	DeltaFile postValidateDeltaFile(String did) {
		DeltaFile deltaFile = postFormatDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.EGRESS);
		deltaFile.completeAction("sampleEgress.SampleValidateAction", START_TIME, STOP_TIME);
		return deltaFile;
	}

	@Test
	void testValidate() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postFormatDeltaFile(did));

		deltaFilesService.handleActionEvent(actionEvent("validate", did));

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertEqualsIgnoringDates(postValidateDeltaFile(did), deltaFile);

		Mockito.verify(actionEventQueue, never()).putActions(any());
		assertEqualsIgnoringDates(postValidateDeltaFile(did), deltaFile);

		Map<String, String> tags = tagsFor(ActionEventType.VALIDATE, "sampleEgress.SampleValidateAction", INGRESS_FLOW_NAME, EGRESS_FLOW_NAME);
		Mockito.verify(metricRepository).increment(new Metric(DeltaFiConstants.FILES_IN, 1).addTags(tags));
		Mockito.verifyNoMoreInteractions(metricRepository);

	}

	DeltaFile postErrorDeltaFile(String did) {
		DeltaFile deltaFile = postValidateDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.ERROR);
		deltaFile.errorAction("sampleEgress.AuthorityValidateAction", START_TIME, STOP_TIME, "Authority XYZ not recognized", "Dead beef feed face cafe");
		return deltaFile;
	}

	@Test
	void testError() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postValidateDeltaFile(did));

		deltaFilesService.handleActionEvent(actionEvent("error", did));

		DeltaFile actual = deltaFilesService.getDeltaFile(did);

		DeltaFile expected = postErrorDeltaFile(did);
		assertEqualsIgnoringDates(expected, actual);

		Map<String, String> tags = tagsFor(ActionEventType.ERROR, "sampleEgress.AuthorityValidateAction", INGRESS_FLOW_NAME, EGRESS_FLOW_NAME);
		Mockito.verify(metricRepository).increment(new Metric(DeltaFiConstants.FILES_IN, 1).addTags(tags));
		Mockito.verify(metricRepository).increment(new Metric(DeltaFiConstants.FILES_ERRORED, 1).addTags(tags));
		Mockito.verifyNoMoreInteractions(metricRepository);

	}

	@SuppressWarnings("SameParameterValue")
	DeltaFile postResumeDeltaFile(String did, String retryAction) {
		DeltaFile deltaFile = postErrorDeltaFile(did);
		deltaFile.retryErrors();
		deltaFile.setStage(DeltaFileStage.EGRESS);
		deltaFile.getActions().add(Action.newBuilder().name(retryAction).state(ActionState.QUEUED).build());
		deltaFile.getSourceInfo().addMetadata("a", "b");
		return deltaFile;
	}

	@Test
	void testResume() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postErrorDeltaFile(did));

		List<RetryResult> retryResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("resume"), did),
				"data." + DgsConstants.MUTATION.Resume,
				new TypeRef<>() {});

		assertEquals(2, retryResults.size());
		assertEquals(did, retryResults.get(0).getDid());
		assertTrue(retryResults.get(0).getSuccess());
		assertFalse(retryResults.get(1).getSuccess());

		verifyActionEventResults(postResumeDeltaFile(did, "sampleEgress.AuthorityValidateAction"), "sampleEgress.AuthorityValidateAction");

		Mockito.verifyNoInteractions(metricRepository);
	}

	@Test
	void test19ResumeClearsAcknowledged() throws IOException {
		String did = UUID.randomUUID().toString();
		DeltaFile postErrorDeltaFile = postErrorDeltaFile(did);
		postErrorDeltaFile.setErrorAcknowledged(OffsetDateTime.now());
		postErrorDeltaFile.setErrorAcknowledgedReason("some reason");
		deltaFileRepo.save(postErrorDeltaFile);

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("resume"), did),
				"data." + DgsConstants.MUTATION.Resume,
				new TypeRef<>() {});

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertNull(deltaFile.getErrorAcknowledged());
		assertNull(deltaFile.getErrorAcknowledgedReason());
		Mockito.verifyNoInteractions(metricRepository);
	}

	@Test
	void testAcknowledge() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postErrorDeltaFile(did));

		List<AcknowledgeResult> acknowledgeResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("acknowledge"), did),
				"data." + DgsConstants.MUTATION.Acknowledge,
				new TypeRef<>() {});

		assertEquals(2, acknowledgeResults.size());
		assertEquals(did, acknowledgeResults.get(0).getDid());
		assertTrue(acknowledgeResults.get(0).getSuccess());
		assertFalse(acknowledgeResults.get(1).getSuccess());

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertNotNull(deltaFile.getErrorAcknowledged());
		assertEquals("apathy", deltaFile.getErrorAcknowledgedReason());
		Mockito.verifyNoInteractions(metricRepository);
	}

	@Test
	void testCancel() throws IOException {
		String did = UUID.randomUUID().toString();
		DeltaFile deltaFile = postTransformUtf8DeltaFile(did);
		deltaFileRepo.save(deltaFile);

		List<CancelResult> cancelResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("cancel"), did),
				"data." + DgsConstants.MUTATION.Cancel,
				new TypeRef<>() {
				});

		assertEquals(2, cancelResults.size());
		assertEquals(did, cancelResults.get(0).getDid());
		assertTrue(cancelResults.get(0).getSuccess());
		assertFalse(cancelResults.get(1).getSuccess());
		assertTrue(cancelResults.get(1).getError().contains("not found"));

		DeltaFile afterMutation = deltaFilesService.getDeltaFile(did);
		assertEqualsIgnoringDates(postCancelDeltaFile(did), afterMutation);

		List<CancelResult> alreadyCancelledResult = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("cancel"), did),
				"data." + DgsConstants.MUTATION.Cancel,
				new TypeRef<>() {
				});

		assertEquals(2, alreadyCancelledResult.size());
		assertEquals(did, alreadyCancelledResult.get(0).getDid());
		assertFalse(alreadyCancelledResult.get(0).getSuccess());
		assertTrue(alreadyCancelledResult.get(0).getError().contains("no longer active"));
		assertFalse(alreadyCancelledResult.get(1).getSuccess());
		assertTrue(alreadyCancelledResult.get(1).getError().contains("not found"));
		Mockito.verifyNoInteractions(metricRepository);
	}

	DeltaFile postCancelDeltaFile(String did) {
		DeltaFile deltaFile = postTransformUtf8DeltaFile(did);
		deltaFile.setStage(DeltaFileStage.CANCELLED);
		deltaFile.cancelQueuedActions();
		return deltaFile;
	}

	DeltaFile postValidateAuthorityDeltaFile(String did) {
		DeltaFile deltaFile = postValidateDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.EGRESS);
		deltaFile.queueAction("sampleEgress.SampleEgressAction");
		deltaFile.completeAction("sampleEgress.AuthorityValidateAction", START_TIME, STOP_TIME);
		return deltaFile;
	}

	DeltaFile postValidateAuthorityDeltaFileInTestMode(String did, String expectedEgressActionName) {
		DeltaFile deltaFile = postValidateDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.COMPLETE);
		deltaFile.queueAction(expectedEgressActionName);
		deltaFile.completeAction(expectedEgressActionName, START_TIME, STOP_TIME);
		deltaFile.completeAction("sampleEgress.AuthorityValidateAction", START_TIME, STOP_TIME);
		deltaFile.setTestMode(expectedEgressActionName);
		deltaFile.setEgressed(false);
		return deltaFile;
	}

	@Test
	void testValidateAuthority() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postValidateDeltaFile(did));

		deltaFilesService.handleActionEvent(actionEvent("validateAuthority", did));

		verifyActionEventResults(postValidateAuthorityDeltaFile(did), "sampleEgress.SampleEgressAction");

		Map<String, String> tags = tagsFor(ActionEventType.VALIDATE, "sampleEgress.AuthorityValidateAction", INGRESS_FLOW_NAME, EGRESS_FLOW_NAME);
		Mockito.verify(metricRepository).increment(new Metric(DeltaFiConstants.FILES_IN, 1).addTags(tags));
		Mockito.verifyNoMoreInteractions(metricRepository);

	}

	DeltaFile postEgressDeltaFile(String did) {
		DeltaFile deltaFile = postValidateAuthorityDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.COMPLETE);
		deltaFile.setEgressed(true);
		deltaFile.completeAction("sampleEgress.SampleEgressAction", START_TIME, STOP_TIME);
		deltaFile.addEgressFlow(EGRESS_FLOW_NAME);
		return deltaFile;
	}

	@Test
	void testEgress() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postValidateAuthorityDeltaFile(did));

		deltaFilesService.handleActionEvent(actionEvent("egress", did));

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertEqualsIgnoringDates(postEgressDeltaFile(did), deltaFile);

		Mockito.verify(actionEventQueue, never()).putActions(any());
		Map<String, String> tags = tagsFor(ActionEventType.EGRESS, "sampleEgress.SampleEgressAction", INGRESS_FLOW_NAME, EGRESS_FLOW_NAME);
		Mockito.verify(metricRepository).increment(new Metric(DeltaFiConstants.FILES_IN, 1).addTags(tags));
		Mockito.verify(metricRepository).increment(new Metric(DeltaFiConstants.FILES_OUT, 1).addTag("destination", "final").addTags(tags));
		Mockito.verify(metricRepository).increment(new Metric(DeltaFiConstants.BYTES_OUT, 42).addTag("destination", "final").addTags(tags));
		Mockito.verifyNoMoreInteractions(metricRepository);
	}

	@Test
	void testToEgressWithTestModeIngress() throws IOException {
		configureTestIngress();

		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postValidateDeltaFile(did));

		deltaFilesService.handleActionEvent(actionEvent("validateAuthority", did));

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);

		assertEqualsIgnoringDates(
				postValidateAuthorityDeltaFileInTestMode(did, "sampleEgress.SyntheticEgressActionForTestIngress"),
				deltaFile
		);
		MatcherAssert.assertThat(deltaFile.getTestModeReason(), containsString(INGRESS_FLOW_NAME));

		Mockito.verify(actionEventQueue, never()).putActions(any());
	}

	@Test
	void testToEgressWithTestModeEgress() throws IOException {
		configureTestEgress();

		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postValidateDeltaFile(did));

		deltaFilesService.handleActionEvent(actionEvent("validateAuthority", did));

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);

		assertEqualsIgnoringDates(
				postValidateAuthorityDeltaFileInTestMode(did, "sampleEgress.SyntheticEgressActionForTestEgress"),
				deltaFile
		);
		MatcherAssert.assertThat(deltaFile.getTestModeReason(), containsString(EGRESS_FLOW_NAME));
		Mockito.verify(actionEventQueue, never()).putActions(any());
		Map<String, String> tags = tagsFor(ActionEventType.VALIDATE, "sampleEgress.AuthorityValidateAction", INGRESS_FLOW_NAME, EGRESS_FLOW_NAME);
		Mockito.verify(metricRepository).increment(new Metric(DeltaFiConstants.FILES_IN, 1).addTags(tags));
		Mockito.verifyNoMoreInteractions(metricRepository);
	}

	@Test
	void testReplay() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postEgressDeltaFile(did));

		List<RetryResult> results = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("replay"), did),
				"data." + DgsConstants.MUTATION.Replay,
				new TypeRef<>() {});

		assertEquals(1, results.size());
		assertTrue(results.get(0).getSuccess());

		DeltaFile parent = deltaFilesService.getDeltaFile(did);
		assertEquals(1, parent.getChildDids().size());
		assertEquals(parent.getChildDids().get(0), parent.getReplayDid());
		assertEquals(results.get(0).getDid(), parent.getReplayDid());
		assertNotNull(parent.getReplayed());

		DeltaFile expected = postIngressDeltaFile(did);
		expected.setDid(parent.getChildDids().get(0));
		expected.setParentDids(List.of(did));
		expected.getActions().get(1).setName("sampleIngress.SampleLoadAction");
		expected.getSourceInfo().setFilename("newFilename");
		expected.getSourceInfo().setFlow("theFlow");
		expected.getSourceInfo().setMetadata(Map.of("AuthorizedBy", "ABC", "sourceInfo.filename.original", "input.txt", "sourceInfo.flow.original", INGRESS_FLOW_NAME, "removeMe.original", "whatever", "AuthorizedBy.original", "XYZ", "anotherKey", "anotherValue"));
		verifyActionEventResults(expected, "sampleIngress.SampleLoadAction");

		List<RetryResult> secondResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("replay"), did),
				"data." + DgsConstants.MUTATION.Replay,
				new TypeRef<>() {});

		assertEquals(1, secondResults.size());
		assertFalse(secondResults.get(0).getSuccess());
		Mockito.verifyNoInteractions(metricRepository);
	}

	@Test
	void testReplayContentDeleted() throws IOException {
		String did = UUID.randomUUID().toString();
		DeltaFile contentDeletedDeltaFile = postEgressDeltaFile(did);
		contentDeletedDeltaFile.setContentDeleted(OffsetDateTime.now());
		contentDeletedDeltaFile.setContentDeletedReason("aged off");
		deltaFileRepo.save(contentDeletedDeltaFile);

		List<RetryResult> results = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("replay"), did),
				"data." + DgsConstants.MUTATION.Replay,
				new TypeRef<>() {});

		assertEquals(1, results.size());
		assertFalse(results.get(0).getSuccess());

		DeltaFile parent = deltaFilesService.getDeltaFile(did);
		assertTrue(parent.getChildDids().isEmpty());

		Mockito.verifyNoInteractions(metricRepository);
	}

	@Test
	void testSourceMetadataUnion() throws IOException {
		DeltaFile deltaFile1 = buildDeltaFile("did1", Map.of("key", "val1"));
		DeltaFile deltaFile2 = buildDeltaFile("did2", Map.of("key", "val2"));
		List<DeltaFile> deltaFiles = List.of(deltaFile1, deltaFile2);
		deltaFileRepo.saveAll(deltaFiles);

		List<UniqueKeyValues> metadataUnion = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("source"), "did1", "did2"),
				"data." + DgsConstants.QUERY.SourceMetadataUnion,
				new TypeRef<>() {
				});

		assertEquals(1, metadataUnion.size());
		assertEquals("key", metadataUnion.get(0).getKey());
		assertEquals(2, metadataUnion.get(0).getValues().size());
		assertTrue(metadataUnion.get(0).getValues().containsAll(List.of("val1", "val2")));
	}

	@Test
	void testFilterTransform() throws IOException {
		String did = UUID.randomUUID().toString();
		verifyFiltered(postIngressDeltaFile(did), "sampleIngress.Utf8TransformAction", true);
	}

	@Test
	void testFilterLoad() throws IOException {
		String did = UUID.randomUUID().toString();
		verifyFiltered(postTransformDeltaFile(did), "sampleIngress.SampleLoadAction", true);
	}

	@Test
	void testFilterDomain() throws IOException {
		String did = UUID.randomUUID().toString();
		verifyFiltered(postLoadDeltaFile(did), "sampleEnrich.SampleDomainAction", false);
	}

	@Test
	void testFilterEnrich() throws IOException {
		String did = UUID.randomUUID().toString();
		verifyFiltered(postDomainDeltaFile(did), "sampleEnrich.SampleEnrichAction", false);
	}

	@Test
	void testFilterFormat() throws IOException {
		String did = UUID.randomUUID().toString();
		verifyFiltered(postEnrichDeltaFile(did), "sampleEgress.SampleFormatAction", true);
	}

	@Test
	void testFilterValidate() throws IOException {
		String did = UUID.randomUUID().toString();
		verifyFiltered(postValidateDeltaFile(did), "sampleEgress.AuthorityValidateAction", true);
	}

	@Test
	void testFilterEgress() throws IOException {
		String did = UUID.randomUUID().toString();
		verifyFiltered(postValidateAuthorityDeltaFile(did), "sampleEgress.SampleEgressAction", true);
	}

	private void verifyFiltered(DeltaFile deltaFile, String filteredAction, boolean filteringAllowed) throws IOException {
		deltaFileRepo.save(deltaFile);

		deltaFilesService.handleActionEvent(filterActionEvent(deltaFile.getDid(), filteredAction));

		DeltaFile actual = deltaFilesService.getDeltaFile(deltaFile.getDid());
		Action action = actual.getActions().stream().filter(a -> a.getName().equals(filteredAction)).findFirst().orElse(null);
		assert action != null;

		if (filteringAllowed) {
			assertEquals(ActionState.FILTERED, action.getState());
			assertEquals(DeltaFileStage.COMPLETE, actual.getStage());
			assertTrue(actual.getFiltered());
			Optional<Action> result = actual.getActions().stream().filter(a -> a.getState() == ActionState.FILTERED).findFirst();
			assertTrue(result.isPresent());
			assertEquals("you got filtered", result.get().getFilteredCause());

			Mockito.verify(actionEventQueue, never()).putActions(any());
		} else {
			assertEquals(ActionState.ERROR, action.getState());
			assertEquals(DeltaFileStage.ERROR, actual.getStage());
			assertFalse(actual.getFiltered());
		}
	}

	@Test
	void testEgressDeleteCompleted() throws IOException {
		deltaFiPropertiesService.getDeltaFiProperties().getDelete().setOnCompletion(true);

		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postValidateAuthorityDeltaFile(did));

		deltaFilesService.handleActionEvent(actionEvent("egress", did));

		Optional<DeltaFile> deltaFile = deltaFileRepo.findById(did);
		assertTrue(deltaFile.isPresent());
		assertNotNull(deltaFile.get().getContentDeleted());
	}

	@Test
	void setDeltaFileTtl() {
		assertEquals(Duration.ofDays(13), deltaFileRepo.getTtlExpiration());
	}

	@Test
	void findConfigsTest() {
		String name = "sampleIngress.SampleLoadAction";

		ConfigQueryInput configQueryInput = ConfigQueryInput.newBuilder().configType(ConfigType.LOAD_ACTION).name(name).build();

		DeltaFiConfigsProjectionRoot projection = new DeltaFiConfigsProjectionRoot()
				.name()
				.apiVersion()
				.onLoadActionConfiguration()
				.name()
				.actionType()
				.type()
				.parent();

		DeltaFiConfigsGraphQLQuery findConfig = DeltaFiConfigsGraphQLQuery.newRequest().configQuery(configQueryInput).build();

		TypeRef<List<DeltaFiConfiguration>> listOfConfigs = new TypeRef<>() {};
		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(findConfig, projection);
		List<DeltaFiConfiguration> configs = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQLQueryRequest.serialize(),
				"data." + findConfig.getOperationName(),
				listOfConfigs);

		assertTrue(configs.get(0) instanceof LoadActionConfiguration);

		LoadActionConfiguration loadActionConfiguration = (LoadActionConfiguration) configs.get(0);
		assertEquals(name, loadActionConfiguration.getName());
	}

	@Test
	void testGetIngressFlowPlan() {
		clearForFlowTests();
		IngressFlowPlan ingressFlowPlanA = new IngressFlowPlan("ingressPlan", "description", new LoadActionConfiguration("load", "type"));
		IngressFlowPlan ingressFlowPlanB = new IngressFlowPlan("b", "description", new LoadActionConfiguration("load", "type"));
		ingressFlowPlanRepo.saveAll(List.of(ingressFlowPlanA, ingressFlowPlanB));
		IngressFlowPlan plan = FlowPlanDatafetcherTestHelper.getIngressFlowPlan(dgsQueryExecutor);
		assertThat(plan.getName()).isEqualTo("ingressPlan");
	}

	@Test
	void testGetEgressFlowPlan() {
		clearForFlowTests();
		EgressFlowPlan egressFlowPlanA = new EgressFlowPlan("egressPlan", "description", new FormatActionConfiguration("format", "type", List.of("domain")), new EgressActionConfiguration("egress", "type"));
		EgressFlowPlan egressFlowPlanB = new EgressFlowPlan("b", "description", new FormatActionConfiguration("format", "type", List.of("domain")), new EgressActionConfiguration("egress", "type"));
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
	void testGetFlowsByState() {
		clearForFlowTests();
		IngressFlow stoppedFlow = buildIngressFlow(FlowState.STOPPED);
		stoppedFlow.setName("stopped");

		IngressFlow invalidFlow = buildIngressFlow(FlowState.INVALID);
		invalidFlow.setName("invalid");

		IngressFlow runningFlow = buildIngressFlow(FlowState.RUNNING);
		runningFlow.setName("running");

		ingressFlowRepo.saveAll(List.of(stoppedFlow, invalidFlow, runningFlow));


		assertThat(ingressFlowService.getFlowNamesByState(null)).hasSize(3).contains("stopped", "invalid", "running");
		assertThat(ingressFlowService.getFlowNamesByState(FlowState.STOPPED)).hasSize(1).contains("stopped");
		assertThat(ingressFlowService.getFlowNamesByState(FlowState.INVALID)).hasSize(1).contains("invalid");
		assertThat(ingressFlowService.getFlowNamesByState(FlowState.RUNNING)).hasSize(1).contains("running");
	}

	@Test
	void testGetFlowsQuery() {
		clearForFlowTests();

		ingressFlowRepo.save(buildIngressFlow(FlowState.STOPPED));
		egressFlowRepo.save(buildEgressFlow(FlowState.STOPPED));

		FlowNames flows = FlowPlanDatafetcherTestHelper.getFlowNames(dgsQueryExecutor);
		assertThat(flows.getIngress()).hasSize(1).contains(INGRESS_FLOW_NAME);
		assertThat(flows.getEgress()).hasSize(1).contains(EGRESS_FLOW_NAME);
		assertThat(flows.getEnrich()).isEmpty();

	}

	@Test
	void getRunningFlows() {
		clearForFlowTests();

		ingressFlowRepo.save(buildIngressFlow(FlowState.STOPPED));
		assertTrue(FlowPlanDatafetcherTestHelper.startIngressFlow(dgsQueryExecutor));

		egressFlowRepo.save(buildEgressFlow(FlowState.STOPPED));
		assertTrue(FlowPlanDatafetcherTestHelper.startEgressFlow(dgsQueryExecutor));

		SystemFlows flows = FlowPlanDatafetcherTestHelper.getRunningFlows(dgsQueryExecutor);
		assertThat(flows.getIngress()).hasSize(1).matches(ingressFlows -> INGRESS_FLOW_NAME.equals(ingressFlows.get(0).getName()));
		assertThat(flows.getEgress()).hasSize(1).matches(egressFlows -> EGRESS_FLOW_NAME.equals(egressFlows.get(0).getName()));
		assertThat(flows.getEnrich()).hasSize(0);

		assertTrue(FlowPlanDatafetcherTestHelper.stopEgressFlow(dgsQueryExecutor));
		SystemFlows updatedFlows = FlowPlanDatafetcherTestHelper.getRunningFlows(dgsQueryExecutor);
		assertThat(updatedFlows.getIngress()).hasSize(1);
		assertThat(updatedFlows.getEgress()).hasSize(0);
		assertThat(updatedFlows.getEnrich()).hasSize(0);
	}

	@Test
	void getAllFlows() {
		clearForFlowTests();
		IngressFlow ingressFlow = new IngressFlow();
		ingressFlow.setName(INGRESS_FLOW_NAME);

		EgressFlow egressFlow = new EgressFlow();
		egressFlow.setName(EGRESS_FLOW_NAME);

		ingressFlowRepo.save(ingressFlow);
		egressFlowRepo.save(egressFlow);

		SystemFlows flows = FlowPlanDatafetcherTestHelper.getAllFlows(dgsQueryExecutor);
		assertThat(flows.getIngress()).hasSize(1).matches(ingressFlows -> INGRESS_FLOW_NAME.equals(ingressFlows.get(0).getName()));
		assertThat(flows.getEgress()).hasSize(1).matches(egressFlows -> EGRESS_FLOW_NAME.equals(egressFlows.get(0).getName()));
	}

	@Test
	void getIngressFlow() {
		clearForFlowTests();
		IngressFlow ingressFlow = new IngressFlow();
		ingressFlow.setName(INGRESS_FLOW_NAME);
		ingressFlowRepo.save(ingressFlow);

		IngressFlow foundFlow = FlowPlanDatafetcherTestHelper.getIngressFlow(dgsQueryExecutor);
		assertThat(foundFlow).isNotNull();
		assertThat(foundFlow.getName()).isEqualTo(INGRESS_FLOW_NAME);
	}

	@Test
	void getEgressFlow() {
		clearForFlowTests();
		EgressFlow egressFlow = new EgressFlow();
		egressFlow.setName(EGRESS_FLOW_NAME);
		egressFlowRepo.save(egressFlow);
		EgressFlow foundFlow = FlowPlanDatafetcherTestHelper.getEgressFlow(dgsQueryExecutor);
		assertThat(foundFlow).isNotNull();
		assertThat(foundFlow.getName()).isEqualTo(EGRESS_FLOW_NAME);
	}

	@Test
	void testNullIncludeIngress() {
		clearForFlowTests();
		FormatActionConfiguration formatActionConfiguration = new FormatActionConfiguration("format", "org.deltafi.actions.Formatter", List.of("domain"));
		EgressActionConfiguration egressActionConfiguration = new EgressActionConfiguration("egress", "org.deltafi.actions.EgressAction");
		EgressFlowPlan egressFlowPlan = new EgressFlowPlan("withNullInclude", null, formatActionConfiguration, egressActionConfiguration);
		egressFlowPlan.setSourcePlugin(PluginCoordinates.builder().artifactId("test-actions").groupId("org.deltafi").version("1.0").build());
		egressFlowPlan.setIncludeIngressFlows(null);
		egressFlowPlan.setExcludeIngressFlows(List.of());

		egressFlowPlanService.saveFlowPlan(egressFlowPlan);

		egressFlowPlan = egressFlowPlanService.getPlanByName("withNullInclude");
		assertThat(egressFlowPlan.getIncludeIngressFlows()).isNull();
		assertThat(egressFlowPlan.getExcludeIngressFlows()).isEmpty();
	}

	@Test
	void getActionNamesByFamily() {
		clearForFlowTests();

		ingressFlowRepo.save(buildIngressFlow(FlowState.STOPPED));
		enrichFlowRepo.save(buildEnrichFlow(FlowState.STOPPED));
		egressFlowRepo.save(buildEgressFlow(FlowState.STOPPED));

		List<ActionFamily> actionFamilies = FlowPlanDatafetcherTestHelper.getActionFamilies(dgsQueryExecutor);
		assertThat(actionFamilies.size()).isEqualTo(8);

		assertThat(getActionNames(actionFamilies, "INGRESS")).hasSize(1).contains("IngressAction");
		assertThat(getActionNames(actionFamilies, "TRANSFORM")).hasSize(2).contains("sampleIngress.Utf8TransformAction", "sampleIngress.SampleTransformAction");
		assertThat(getActionNames(actionFamilies, "LOAD")).hasSize(1).contains("sampleIngress.SampleLoadAction");
		assertThat(getActionNames(actionFamilies, "DOMAIN")).hasSize(1).contains("sampleEnrich.SampleDomainAction");
		assertThat(getActionNames(actionFamilies, "ENRICH")).hasSize(1).contains("sampleEnrich.SampleEnrichAction");
		assertThat(getActionNames(actionFamilies, "FORMAT")).hasSize(1).contains("sampleEgress.SampleFormatAction");
		assertThat(getActionNames(actionFamilies, "VALIDATE")).isEmpty();
		assertThat(getActionNames(actionFamilies, "EGRESS")).hasSize(1).contains("sampleEgress.SampleEgressAction");
	}

	@Test
	void testGetPropertySets() {
		List<PropertySet> propertySets = PropertiesDatafetcherTestHelper.getPropertySets(dgsQueryExecutor);
		assertThat(propertySets).hasSize(1);
	}

	@Test
	void testGetDeltaFiProperties() {
		DeltaFiProperties deltaFiProperties = PropertiesDatafetcherTestHelper.getDeltaFiProperties(dgsQueryExecutor);
		assertThat(deltaFiProperties).isEqualTo(new DeltaFiProperties());
	}

	@Test
	void testUpdateAndRemovePropertyOverrides() {
		assertThat(PropertiesDatafetcherTestHelper.updateProperties(dgsQueryExecutor)).isTrue();
		assertThat(PropertiesDatafetcherTestHelper.removePropertyOverrides(dgsQueryExecutor)).isTrue();
	}

	@Test
	void testAddExternalLinkMutations() {
		assertThat(PropertiesDatafetcherTestHelper.removeExternalLink(dgsQueryExecutor)).isFalse();
		assertThat(PropertiesDatafetcherTestHelper.addExternalLink(dgsQueryExecutor)).isTrue();
		assertThat(PropertiesDatafetcherTestHelper.removeExternalLink(dgsQueryExecutor)).isTrue();
	}

	@Test
	void testDeltaFileLinkMutations() {
		assertThat(PropertiesDatafetcherTestHelper.removeDeltaFileLink(dgsQueryExecutor)).isFalse();
		assertThat(PropertiesDatafetcherTestHelper.addDeltaFileLink(dgsQueryExecutor)).isTrue();
		assertThat(PropertiesDatafetcherTestHelper.removeDeltaFileLink(dgsQueryExecutor)).isTrue();
	}

	List<String> getActionNames(List<ActionFamily> actionFamilies, String family) {
		return actionFamilies.stream()
				.filter(actionFamily -> family.equals(actionFamily.getFamily()))
				.map(ActionFamily::getActionNames)
				.flatMap(Collection::stream)
				.toList();
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
		IngressFlowPlan ingressFlowPlan = new IngressFlowPlan("flowPlan", null, null);
		ingressFlowPlanRepo.save(ingressFlowPlan);
		assertTrue(FlowPlanDatafetcherTestHelper.removeIngressFlowPlan(dgsQueryExecutor));
	}

	@Test
	void testRemoveEgressFlowPlan() {
		clearForFlowTests();
		EgressFlowPlan egressFlowPlan = new EgressFlowPlan("flowPlan", null, null, null);
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
		variables.setVariables(List.of(Variable.newBuilder().name("key").value("test").description("description").dataType(VariableDataType.STRING).build()));
		pluginVariableRepo.save(variables);
		assertTrue(FlowPlanDatafetcherTestHelper.setPluginVariableValues(dgsQueryExecutor));
	}

	@Test
	void testLoadFlowAssignmentRules() {
		Result result = saveFirstRule(dgsQueryExecutor);
		assertTrue(result.isSuccess());
		assertTrue(result.getErrors().isEmpty());
		assertEquals(1, flowAssignmentRuleRepo.count());

		Result badResult = saveBadRule(dgsQueryExecutor, false);
		assertFalse(badResult.isSuccess());
		assertEquals("missing rule name", badResult.getErrors().get(0));

		// Verify firstRule still present since replaceAll was false
		assertEquals(1, flowAssignmentRuleRepo.count());

		// Verify firstRule removed present since replaceAll was true
		saveBadRule(dgsQueryExecutor, true);
		assertEquals(0, flowAssignmentRuleRepo.count());
	}

	@Test
	void testGetAllFlowAssignmentRules() {
		List<Result> results = saveAllRules(dgsQueryExecutor);
		// Verify saved 4, and 5th was invalid
		assertEquals(4, flowAssignmentRuleRepo.count());
		assertFalse(results.get(4).isSuccess());
		List<FlowAssignmentRule> assignmentList = getAllFlowAssignmentRules(dgsQueryExecutor);
		// Verify ordered by priority, then flow
		assertEquals(4, assignmentList.size());
		assertEquals(RULE_NAME4, assignmentList.get(0).getName());
		assertEquals(RULE_NAME2, assignmentList.get(1).getName());
		assertEquals(RULE_NAME1, assignmentList.get(2).getName());
		assertEquals(RULE_NAME3, assignmentList.get(3).getName());

		// Verify generates a unique id for each rule
		Set<String> ids = new HashSet<>();
		for (FlowAssignmentRule rule: assignmentList) {
			ids.add(rule.getId());
		}
		assertEquals(4, ids.size());

	}

	private String getIdByRuleName(String name) {
		for (FlowAssignmentRule rule : getAllFlowAssignmentRules(dgsQueryExecutor)) {
			if (rule.getName().equals(name)) {
				return rule.getId();
			}
		}
		return null;
	}

	@Test
	void testRemoveFlowAssignmentRules() {
		saveFirstRule(dgsQueryExecutor);
		saveSecondRuleSet(dgsQueryExecutor);
		assertEquals(2, flowAssignmentRuleRepo.count());

		String id = getIdByRuleName(RULE_NAME1);
		assertTrue(removeFlowAssignment(dgsQueryExecutor, id));
		assertEquals(1, flowAssignmentRuleRepo.count());
		assertFalse(removeFlowAssignment(dgsQueryExecutor, "not-found"));
		assertEquals(1, flowAssignmentRuleRepo.count());
	}

	@Test
	void testGetFlowAssignment() {
		saveFirstRule(dgsQueryExecutor);
		saveSecondRuleSet(dgsQueryExecutor);

		FlowAssignmentRule first =
				getFlowAssignment(dgsQueryExecutor, getIdByRuleName(RULE_NAME1));
		FlowAssignmentRule second =
				getFlowAssignment(dgsQueryExecutor, getIdByRuleName(RULE_NAME2));
		FlowAssignmentRule notFound =
				getFlowAssignment(dgsQueryExecutor, "notfound");

		assertEquals(RULE_NAME1, first.getName());
		assertEquals(FLOW_NAME1, first.getFlow());
		assertEquals(FILENAME_REGEX, first.getFilenameRegex());

		assertEquals(RULE_NAME2, second.getName());
		assertEquals(FLOW_NAME2, second.getFlow());
		assertEquals(META_KEY, second.getRequiredMetadata().get(0).getKey());
		assertEquals(META_VALUE, second.getRequiredMetadata().get(0).getValue());

		assertNull(notFound);
	}

	@Test
	void testFlowAssignmentDuplicateName() {
		List<Result> results = loadRulesWithDuplicates(dgsQueryExecutor);
		assertTrue(results.get(0).isSuccess());
		assertFalse(results.get(1).isSuccess());
	}

	@Test
	void testUpdateFlowAssignmentRule() {
		saveFirstRule(dgsQueryExecutor);
		saveSecondRuleSet(dgsQueryExecutor);

		FlowAssignmentRule first =
				getFlowAssignment(dgsQueryExecutor, getIdByRuleName(RULE_NAME1));
		FlowAssignmentRule second =
				getFlowAssignment(dgsQueryExecutor, getIdByRuleName(RULE_NAME2));

		Result result = updateFlowAssignmentRule(dgsQueryExecutor,
				first.getId(), second.getName(), 100);
		assertFalse(result.isSuccess());
		assertTrue(result.getErrors().contains("duplicate rule name"));

		result = updateFlowAssignmentRule(dgsQueryExecutor,
				first.getId(), "new-name", -1);
		assertFalse(result.isSuccess());
		assertTrue(result.getErrors().contains("invalid priority"));

		result = updateFlowAssignmentRule(dgsQueryExecutor,
				null, "new-name", 100);
		assertFalse(result.isSuccess());
		assertTrue(result.getErrors().contains("id is missing"));

		result = updateFlowAssignmentRule(dgsQueryExecutor,
				"notFound", "new-name", 100);
		assertFalse(result.isSuccess());
		assertTrue(result.getErrors().contains("rule not found"));

		result = updateFlowAssignmentRule(dgsQueryExecutor,
				first.getId(), "new-name", 100);
		assertTrue(result.isSuccess());

		FlowAssignmentRule updated =
				getFlowAssignment(dgsQueryExecutor, first.getId());

		assertEquals("new-name", updated.getName());
		assertEquals(100, updated.getPriority());
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

		assertEqualsIgnoringDates(expected, actual);
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
		assertEqualsIgnoringDates(expected.getDeltaFiles().get(0), actual.getDeltaFiles().get(0));
	}

	@Test
	void resume() {
		DeltaFile input = deltaFilesService.ingress(INGRESS_INPUT);
		DeltaFile second = deltaFilesService.ingress(INGRESS_INPUT_2);

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(input.getDid());
		deltaFile.errorAction("sampleIngress.SampleLoadAction", START_TIME, STOP_TIME, "blah", "blah");
		deltaFilesService.advanceAndSave(deltaFile);

		DeltaFile erroredFile = deltaFilesService.getDeltaFile(input.getDid());
		assertEquals(2, erroredFile.getActions().size());

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
				new ResumeGraphQLQuery.Builder()
						.dids(List.of(input.getDid(), second.getDid(), "badDid"))
						.build(),
				new ResumeProjectionRoot().did().success().error()
		);

		List<RetryResult> results = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQLQueryRequest.serialize(),
				"data." + DgsConstants.MUTATION.Resume,
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

		DeltaFile afterResumeFile = deltaFilesService.getDeltaFile(input.getDid());
		assertEquals(3, afterResumeFile.getActions().size());
		assertEquals(ActionState.COMPLETE, afterResumeFile.getActions().get(0).getState());
		assertEquals(ActionState.RETRIED, afterResumeFile.getActions().get(1).getState());
		assertEquals(ActionState.QUEUED, afterResumeFile.getActions().get(2).getState());
		// StateMachine will queue the failed loadAction again leaving the DeltaFile in the INGRESS stage
		assertEquals(DeltaFileStage.INGRESS, afterResumeFile.getStage());
	}

	@Test
	void getsPlugins() throws IOException {
		pluginRepository.deleteAll();
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), Plugin.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), Plugin.class));

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(PluginsGraphQLQuery.newRequest().build(), PLUGINS_PROJECTION_ROOT);

		List<Plugin> plugins =
				dgsQueryExecutor.executeAndExtractJsonPathAsObject(graphQLQueryRequest.serialize(),
						"data.plugins[*]", new TypeRef<>() {});

		assertEquals(2, plugins.size());

		validatePlugin1(plugins.get(0));
	}

	@Test
	void registersPlugin() throws IOException {
		pluginRepository.deleteAll();
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), Plugin.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-3.json"), Plugin.class));

		Plugin plugin = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), Plugin.class);
		ResponseEntity<String> response = postPluginRegistration(plugin);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		List<Plugin> plugins = pluginRepository.findAll();
		assertEquals(3, plugins.size());
	}

	ResponseEntity<String> postPluginRegistration(Plugin plugin) throws JsonProcessingException {
		PluginRegistration pluginRegistration = PluginRegistration.builder()
				.pluginCoordinates(plugin.getPluginCoordinates())
				.displayName(plugin.getDisplayName())
				.description(plugin.getDescription())
				.actionKitVersion(plugin.getActionKitVersion())
				.dependencies(plugin.getDependencies())
				.actions(plugin.getActions())
				.build();
		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", MediaType.APPLICATION_JSON);
		HttpEntity<String> request = new HttpEntity<>(OBJECT_MAPPER.writeValueAsString(pluginRegistration), headers);
		return restTemplate.postForEntity("/plugins", request, String.class);
	}

	@Test
	void registerPluginReplacesExistingPlugin() throws IOException {
		pluginRepository.deleteAll();
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), Plugin.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-3.json"), Plugin.class));
		Plugin existingPlugin = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), Plugin.class);
		existingPlugin.getPluginCoordinates().setVersion("0.0.9");
		existingPlugin.setDescription("changed");
		pluginRepository.save(existingPlugin);

		Plugin plugin = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), Plugin.class);
		ResponseEntity<String> response = postPluginRegistration(plugin);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		List<Plugin> plugins = pluginRepository.findAll();
		assertEquals(3, plugins.size());
		assertThat(pluginRepository.findById(existingPlugin.getPluginCoordinates())).isEmpty();
		assertThat(pluginRepository.findById(plugin.getPluginCoordinates())).isPresent();
	}

	@Test
	void registerPluginReturnsErrorsOnMissingDependencies() throws IOException {
		pluginRepository.deleteAll();

		Plugin plugin = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), Plugin.class);
		ResponseEntity<String> response = postPluginRegistration(plugin);

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		if (response.getBody() == null) {
			fail("Missing response body");
			return;
		}
		assertTrue(response.getBody().contains("Plugin dependency not registered: org.deltafi:plugin-2:1.0.0."));
		assertTrue(response.getBody().contains("Plugin dependency not registered: org.deltafi:plugin-3:1.0.0."));
		List<Plugin> plugins = pluginRepository.findAll();
		assertTrue(plugins.isEmpty());
	}

	@Test
	void uninstallPluginSuccess() throws IOException {
		pluginRepository.deleteAll();
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), Plugin.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-3.json"), Plugin.class));

		Plugin plugin = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), Plugin.class);
		UninstallPluginGraphQLQuery uninstallPluginGraphQLQuery =
				UninstallPluginGraphQLQuery.newRequest()
						.pluginCoordinates(plugin.getPluginCoordinates()).build();

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(uninstallPluginGraphQLQuery, UNINSTALL_PLUGIN_PROJECTION_ROOT);


		Result mockResult = Result.success();
		Mockito.when(deployerService.uninstallPlugin(plugin.getPluginCoordinates())).thenReturn(mockResult);
		Result result = dgsQueryExecutor.executeAndExtractJsonPathAsObject(graphQLQueryRequest.serialize(),
				"data." + uninstallPluginGraphQLQuery.getOperationName(), Result.class);

		Mockito.verify(deployerService).uninstallPlugin(plugin.getPluginCoordinates());
		assertThat(result).isEqualTo(mockResult);
	}

    @Test
	void findPluginsWithDependency() throws IOException {
		pluginRepository.deleteAll();
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), Plugin.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), Plugin.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-3.json"), Plugin.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-4.json"), Plugin.class));

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
		List<String> dids = List.of("a", "b", "c");
		List<DeltaFile> deltaFiles = dids.stream().map(Util::buildDeltaFile).toList();
		deltaFileRepo.saveAll(deltaFiles);

		List<String> didsRead = deltaFileRepo.readDidsWithContent();

		assertEquals(3, didsRead.size());
		assertTrue(didsRead.containsAll(dids));
	}

	@Test
	void testUpdateForRequeue() {
		Action shouldRequeue = Action.newBuilder().name("hit").modified(MONGO_NOW.minusSeconds(1000)).state(ActionState.QUEUED).build();
		Action shouldStay = Action.newBuilder().name("miss").modified(MONGO_NOW.plusSeconds(1000)).state(ActionState.QUEUED).build();

		DeltaFile hit = buildDeltaFile("did", null, null, MONGO_NOW, MONGO_NOW);
		hit.setActions(Arrays.asList(shouldRequeue, shouldStay));
		deltaFileRepo.save(hit);

		DeltaFile miss = buildDeltaFile("did2", null, null, MONGO_NOW, MONGO_NOW);
		miss.setActions(Arrays.asList(shouldStay, shouldStay));
		deltaFileRepo.save(miss);

		List<DeltaFile> hits = deltaFileRepo.updateForRequeue(MONGO_NOW, 30);

		assertEquals(1, hits.size());
		assertEquals(hit.getDid(), hits.get(0).getDid());
		assertEquals(1, hits.get(0).getRequeueCount());

		DeltaFile fromDatabase = deltaFilesService.getDeltaFile(hits.get(0).getDid());
		assertEquals(1, fromDatabase.getRequeueCount());

		DeltaFile hitAfter = loadDeltaFile("did");
		DeltaFile missAfter = loadDeltaFile("did2");

		assertEquals(miss, missAfter);
		assertNotEquals(hit.getActions().get(0).getModified(), hitAfter.getActions().get(0).getModified());
		assertEquals(hit.getActions().get(1).getModified(), hitAfter.getActions().get(1).getModified());
	}

	@Test
	void deleteByDidIn() {
		List<DeltaFile> deltaFiles = Stream.of("a", "b", "c").map(Util::buildDeltaFile).toList();
		deltaFileRepo.saveAll(deltaFiles);

		assertEquals(3, deltaFileRepo.count());

		deltaFileRepo.deleteByDidIn(Arrays.asList("a", "c"));

		assertEquals(1, deltaFileRepo.count());
		assertEquals("b", deltaFileRepo.findAll().get(0).getDid());
	}

	@Test
	void testFindForDeleteCreatedBefore() {
		DeltaFile deltaFile1 = buildDeltaFile("1", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile("2", null, DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = buildDeltaFile("3", null, DeltaFileStage.INGRESS, OffsetDateTime.now().plusSeconds(2), OffsetDateTime.now().plusSeconds(2));
		deltaFileRepo.save(deltaFile3);
		DeltaFile deltaFile4 = buildDeltaFile("2", null, DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile4.setContentDeleted(OffsetDateTime.now());
		deltaFileRepo.save(deltaFile3);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForDelete(OffsetDateTime.now().plusSeconds(1), null, 0, null, "policy", false, 10);
		assertEquals(List.of(deltaFile1.getDid(), deltaFile2.getDid()), deltaFiles.stream().map(DeltaFile::getDid).toList());
	}

	@Test
	void testFindForDeleteCreatedBeforeBatchLimit() {
		DeltaFile deltaFile1 = buildDeltaFile("1", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile("2", null, DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = buildDeltaFile("3", null, DeltaFileStage.INGRESS, OffsetDateTime.now().plusSeconds(2), OffsetDateTime.now().plusSeconds(2));
		deltaFileRepo.save(deltaFile3);
		DeltaFile deltaFile4 = buildDeltaFile("2", null, DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile4.setContentDeleted(OffsetDateTime.now());
		deltaFileRepo.save(deltaFile3);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForDelete(OffsetDateTime.now().plusSeconds(1), null, 0, null, "policy", false, 1);
		assertEquals(List.of(deltaFile1.getDid()), deltaFiles.stream().map(DeltaFile::getDid).toList());
	}

	@Test
	void testFindForDeleteCreatedBeforeWithMetadata() {
		DeltaFile deltaFile1 = buildDeltaFile("1", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile1.setContentDeleted(OffsetDateTime.now());
		deltaFileRepo.save(deltaFile1);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForDelete(OffsetDateTime.now().plusSeconds(1), null, 0, null, "policy", true, 10);
		assertEquals(List.of(deltaFile1.getDid()), deltaFiles.stream().map(DeltaFile::getDid).toList());
	}

	@Test
	void testFindForDeleteCompletedBefore() {
		DeltaFile deltaFile1 = buildDeltaFile("1", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile("2", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now().plusSeconds(2));
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = buildDeltaFile("3", null, DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile3);
		DeltaFile deltaFile4 = buildDeltaFile("4", null, DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile4.setErrorAcknowledged(OffsetDateTime.now());
		deltaFileRepo.save(deltaFile4);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForDelete(null, OffsetDateTime.now().plusSeconds(1), 0, null, "policy", false, 10);
		assertEquals(List.of(deltaFile1.getDid(), deltaFile4.getDid()), deltaFiles.stream().map(DeltaFile::getDid).toList());
	}

	@Test
	void testFindForDeleteWithFlow() {
		DeltaFile deltaFile1 = buildDeltaFile("1", "a", DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile("2", "b", DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile2);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForDelete(OffsetDateTime.now().plusSeconds(1), null, 0, "a", "policy", false, 10);
		assertEquals(List.of(deltaFile1.getDid()), deltaFiles.stream().map(DeltaFile::getDid).toList());
	}

	@Test
	void testFindForDelete_alreadyMarkedDeleted() {
		OffsetDateTime oneSecondAgo = OffsetDateTime.now().minusSeconds(1);

		DeltaFile deltaFile1 = buildDeltaFile("1", null, DeltaFileStage.COMPLETE, oneSecondAgo, oneSecondAgo);
		deltaFile1.setContentDeleted(oneSecondAgo);
		deltaFileRepo.save(deltaFile1);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForDelete(OffsetDateTime.now(), null, 0, null, "policy", false, 10);
		assertTrue(deltaFiles.isEmpty());
	}

	@Test
	void testFindForDeleteDiskSpace() {
		DeltaFile deltaFile1 = buildDeltaFile("1", null, DeltaFileStage.COMPLETE, OffsetDateTime.now().minusSeconds(5), OffsetDateTime.now());
		deltaFile1.setTotalBytes(100L);
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile("2", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now().plusSeconds(2));
		deltaFile2.setTotalBytes(300L);
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = buildDeltaFile("3", null, DeltaFileStage.COMPLETE, OffsetDateTime.now().plusSeconds(5), OffsetDateTime.now());
		deltaFile3.setTotalBytes(500L);
		deltaFileRepo.save(deltaFile3);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForDelete(250L, null, "policy", 100);
		assertEquals(List.of(deltaFile1.getDid(), deltaFile2.getDid()), deltaFiles.stream().map(DeltaFile::getDid).toList());
	}

	@Test
	void testFindForDeleteDiskSpaceAll() {
		DeltaFile deltaFile1 = buildDeltaFile("1", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile1.setTotalBytes(100L);
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile("2", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now().plusSeconds(2));
		deltaFile2.setTotalBytes(300L);
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = buildDeltaFile("3", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile3.setTotalBytes(500L);
		deltaFileRepo.save(deltaFile3);
		DeltaFile deltaFile4 = buildDeltaFile("4", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile4.setTotalBytes(500L);
		deltaFile4.setContentDeleted(OffsetDateTime.now());
		deltaFileRepo.save(deltaFile4);
		DeltaFile deltaFile5 = buildDeltaFile("5", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile5.setTotalBytes(0L);
		deltaFileRepo.save(deltaFile5);
		DeltaFile deltaFile6 = buildDeltaFile("6", null, DeltaFileStage.EGRESS, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile6.setTotalBytes(50L);
		deltaFileRepo.save(deltaFile6);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForDelete(2500L, null, "policy", 100);
		assertEquals(List.of(deltaFile1.getDid(), deltaFile2.getDid(), deltaFile3.getDid()), deltaFiles.stream().map(DeltaFile::getDid).toList());
	}

	@Test
	void testFindForDeleteDiskSpaceBatchSizeLimited() {
		DeltaFile deltaFile1 = buildDeltaFile("1", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile1.setTotalBytes(100L);
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile("2", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now().plusSeconds(2));
		deltaFile2.setTotalBytes(300L);
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = buildDeltaFile("3", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile3.setTotalBytes(500L);
		deltaFileRepo.save(deltaFile3);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForDelete(2500L, null, "policy", 2);
		assertEquals(List.of(deltaFile1.getDid(), deltaFile2.getDid()), deltaFiles.stream().map(DeltaFile::getDid).toList());
	}

	@Test
	void testFindForDeleteDiskSpaceBatchSizeFlow() {
		DeltaFile deltaFile1 = buildDeltaFile("1", "a", DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile1.setTotalBytes(100L);
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile("2", "a", DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now().plusSeconds(2));
		deltaFile2.setTotalBytes(300L);
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = buildDeltaFile("3", "b", DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile3.setTotalBytes(500L);
		deltaFileRepo.save(deltaFile3);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForDelete(2500L, "a", "policy", 100);
		assertEquals(List.of(deltaFile1.getDid(), deltaFile2.getDid()), deltaFiles.stream().map(DeltaFile::getDid).toList());
	}

	@Test
	void testDeltaFiles_all() {
		DeltaFile deltaFile1 = buildDeltaFile("1", null, DeltaFileStage.COMPLETE, MONGO_NOW.minusSeconds(2), MONGO_NOW.minusSeconds(2));
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile("2", null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.plusSeconds(2));
		deltaFileRepo.save(deltaFile2);

		DeltaFiles deltaFiles = deltaFileRepo.deltaFiles(null, 50, new DeltaFilesFilter(), null);
		assertEquals(deltaFiles.getDeltaFiles(), List.of(deltaFile2, deltaFile1));
	}

	@Test
	void testDeltaFiles_limit() {
		DeltaFile deltaFile1 = buildDeltaFile("1", null, DeltaFileStage.COMPLETE, MONGO_NOW.minusSeconds(2), MONGO_NOW.minusSeconds(2));
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile("2", null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.plusSeconds(2));
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
		DeltaFile deltaFile1 = buildDeltaFile("1", null, DeltaFileStage.COMPLETE, MONGO_NOW.minusSeconds(2), MONGO_NOW.plusSeconds(2));
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile("2", null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));
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
		DeltaFile deltaFile1 = buildDeltaFile("1", null, DeltaFileStage.COMPLETE, MONGO_NOW.minusSeconds(2), MONGO_NOW.plusSeconds(2));
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile("2", null, DeltaFileStage.ERROR, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));
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
		DeltaFile deltaFile1 = buildDeltaFile("1", null, DeltaFileStage.COMPLETE, MONGO_NOW.minusSeconds(2), MONGO_NOW.plusSeconds(2));
		deltaFile1.setIngressBytes(100L);
		deltaFile1.setTotalBytes(1000L);
		deltaFile1.setDomains(List.of(new Domain("domain1", null, null)));
		deltaFile1.addIndexedMetadata(Map.of("a.1", "first", "common", "value"));
		deltaFile1.setEnrichment(List.of(new Enrichment("enrichment1", null, null)));
		deltaFile1.setContentDeleted(MONGO_NOW);
		deltaFile1.setSourceInfo(new SourceInfo("filename1", "flow1", Map.of("key1", "value1", "key2", "value2")));
		deltaFile1.setActions(List.of(Action.newBuilder().name("action1").build()));
		deltaFile1.setFormattedData(List.of(FormattedData.newBuilder().filename("formattedFilename1").formatAction("formatAction1").metadata(Map.of("formattedKey1", "formattedValue1", "formattedKey2", "formattedValue2")).egressActions(List.of("EgressAction1", "EgressAction2")).build()));
		deltaFile1.setErrorAcknowledged(MONGO_NOW);
		deltaFile1.incrementRequeueCount();
		deltaFile1.addEgressFlow("MyEgressFlow");
		deltaFile1.setTestMode("TestModeReason");
		deltaFileRepo.save(deltaFile1);

		DeltaFile deltaFile2 = buildDeltaFile("2", null, DeltaFileStage.ERROR, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));
		deltaFile2.setIngressBytes(200L);
		deltaFile2.setTotalBytes(2000L);
		deltaFile2.setDomains(List.of(new Domain("domain1", null, null), new Domain("domain2", null, null)));
		deltaFile2.addIndexedMetadata(Map.of("a.2", "first", "common", "value"));
		deltaFile2.setEnrichment(List.of(new Enrichment("enrichment1", null, null), new Enrichment("enrichment2", null, null)));
		deltaFile2.setSourceInfo(new SourceInfo("filename2", "flow2", Map.of()));
		deltaFile2.setActions(List.of(Action.newBuilder().name("action1").state(ActionState.ERROR).errorCause("Cause").build(), Action.newBuilder().name("action2").build()));
		deltaFile2.setFormattedData(List.of(FormattedData.newBuilder().filename("formattedFilename2").formatAction("formatAction2").egressActions(List.of("EgressAction1")).build()));
		deltaFile2.setEgressed(true);
		deltaFile2.setFiltered(true);
		deltaFile2.addEgressFlow("MyEgressFlow");
		deltaFile2.addEgressFlow("MyEgressFlow2");
		deltaFileRepo.save(deltaFile2);

		DeltaFile deltaFile3 = buildDeltaFile("3", null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));
		deltaFile3.setIngressBytes(300L);
		deltaFile3.setTotalBytes(3000L);
		deltaFile3.setDomains(List.of(new Domain("domain3", null, null)));
		deltaFile3.addIndexedMetadata(Map.of("b.2", "first", "common", "value"));
		deltaFile3.setEnrichment(List.of(new Enrichment("enrichment3", null, null), new Enrichment("enrichment4", null, null)));
		deltaFile3.setSourceInfo(new SourceInfo("filename3", "flow3", Map.of()));
		deltaFile3.setActions(List.of(Action.newBuilder().name("action2").state(ActionState.FILTERED).filteredCause("Coffee").build(), Action.newBuilder().name("action2").build()));
		deltaFile3.setFormattedData(List.of(FormattedData.newBuilder().filename("formattedFilename3").formatAction("formatAction3").egressActions(List.of("EgressAction2")).build()));
		deltaFile3.setEgressed(true);
		deltaFile3.setFiltered(true);
		deltaFile3.addEgressFlow("MyEgressFlow3");
		deltaFileRepo.save(deltaFile3);

		testFilter(DeltaFilesFilter.newBuilder().testMode(true).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().testMode(false).build(), deltaFile3, deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().createdAfter(MONGO_NOW).build(), deltaFile3, deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().createdBefore(MONGO_NOW).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().domains(Collections.emptyList()).build(), deltaFile3, deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().domains(List.of("domain1")).build(), deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().domains(List.of("domain1", "domain2")).build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().enrichment(Collections.emptyList()).build(), deltaFile3, deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().enrichment(List.of("enrichment1")).build(), deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().enrichment(List.of("enrichment1", "enrichment2")).build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().contentDeleted(true).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().contentDeleted(false).build(), deltaFile3, deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().modifiedAfter(MONGO_NOW).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().modifiedBefore(MONGO_NOW).build(), deltaFile3, deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().requeueCountMin(1).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().requeueCountMin(0).build(), deltaFile3, deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().ingressBytesMin(50L).build(), deltaFile3, deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().ingressBytesMin(150L).build(), deltaFile3, deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().ingressBytesMax(250L).build(), deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().ingressBytesMax(150L).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().ingressBytesMax(100L).ingressBytesMin(100L).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().totalBytesMin(500L).build(), deltaFile3, deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().totalBytesMin(1500L).build(), deltaFile3, deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().totalBytesMax(2500L).build(), deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().totalBytesMax(1500L).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().totalBytesMax(1000L).totalBytesMin(1000L).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().stage(DeltaFileStage.COMPLETE).build(), deltaFile3, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().sourceInfo(SourceInfoFilter.newBuilder().filename("filename1").build()).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().sourceInfo(SourceInfoFilter.newBuilder().filename("FiLeNaMe").build()).build(), deltaFile3, deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().sourceInfo(SourceInfoFilter.newBuilder().flow("flow2").build()).build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().sourceInfo(SourceInfoFilter.newBuilder().metadata(List.of(new KeyValue("key1", "value1"))).build()).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().sourceInfo(SourceInfoFilter.newBuilder().metadata(List.of(new KeyValue("key1", "value1"), new KeyValue("key2", "value2"))).build()).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().sourceInfo(SourceInfoFilter.newBuilder().metadata(List.of(new KeyValue("key1", "value1"), new KeyValue("key2", "value1"))).build()).build());
		testFilter(DeltaFilesFilter.newBuilder().actions(Collections.emptyList()).build(), deltaFile3, deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().actions(List.of("action1")).build(), deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().actions(List.of("action1", "action2")).build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().errorCause("^Cause$").build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().filteredCause("^Coffee$").build(), deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().filteredCause("off").build(), deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().filteredCause("nope").build());
		testFilter(DeltaFilesFilter.newBuilder().formattedData(FormattedDataFilter.newBuilder().filename("formattedFilename1").build()).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().formattedData(FormattedDataFilter.newBuilder().formatAction("formatAction2").build()).build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().formattedData(FormattedDataFilter.newBuilder().metadata(List.of(new KeyValue("formattedKey1", "formattedValue1"))).build()).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().formattedData(FormattedDataFilter.newBuilder().metadata(List.of(new KeyValue("formattedKey1", "formattedValue1"), new KeyValue("formattedKey2", "formattedValue2"))).build()).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().formattedData(FormattedDataFilter.newBuilder().metadata(List.of(new KeyValue("formattedKey1", "formattedValue1"), new KeyValue("formattedKey2", "formattedValue1"))).build()).build());
		testFilter(DeltaFilesFilter.newBuilder().formattedData(FormattedDataFilter.newBuilder().egressActions(List.of("EgressAction1")).build()).build(), deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().formattedData(FormattedDataFilter.newBuilder().egressActions(List.of("EgressAction1", "EgressAction2")).build()).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().dids(Collections.emptyList()).build(), deltaFile3, deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().dids(Collections.singletonList("1")).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().dids(List.of("1", "3")).build(), deltaFile3, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().dids(List.of("1", "2")).build(), deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().dids(List.of("5", "4")).build());
		testFilter(DeltaFilesFilter.newBuilder().errorAcknowledged(true).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().errorAcknowledged(false).build(), deltaFile3, deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().egressed(false).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().egressed(true).build(), deltaFile3, deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().filtered(false).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().filtered(true).build(), deltaFile3, deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().indexedMetadata(Map.of("common", "value")).build(), deltaFile3, deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().indexedMetadata(Map.of("a.1", "first")).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().indexedMetadata(Map.of("a.1", "first", "common", "value")).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().indexedMetadata(Map.of("a.1", "first", "common", "value", "extra", "missing")).build());
		testFilter(DeltaFilesFilter.newBuilder().indexedMetadata(Map.of("a.1", "first", "common", "miss")).build());
		testFilter(DeltaFilesFilter.newBuilder().egressFlows(List.of("MyEgressFlowz")).build());
		testFilter(DeltaFilesFilter.newBuilder().egressFlows(List.of("MyEgressFlow")).build(), deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().egressFlows(List.of("MyEgressFlow2")).build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().egressFlows(List.of("MyEgressFlow", "MyEgressFlow2")).build(), deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().egressFlows(List.of("MyEgressFlow", "MyEgressFlow3")).build(), deltaFile3, deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().sourceInfo(SourceInfoFilter.newBuilder().ingressFlows(List.of("flow1", "flow2")).build()).build(), deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().sourceInfo(SourceInfoFilter.newBuilder().ingressFlows(List.of("flow2")).build()).build(), deltaFile2);
	}

	@Test
	void testQueryByFilterMessage() {
		// Not filtered
		DeltaFile deltaFile1 = buildDeltaFile("1", null, DeltaFileStage.COMPLETE, MONGO_NOW, MONGO_NOW);
		deltaFile1.setActions(List.of(Action.newBuilder().name("action1").build()));
		deltaFileRepo.save(deltaFile1);
		// Not filtered, with errorCause
		DeltaFile deltaFile2 = buildDeltaFile("2", null, DeltaFileStage.ERROR, MONGO_NOW.plusSeconds(1), MONGO_NOW.plusSeconds(1));
		deltaFile2.setActions(List.of(Action.newBuilder().name("action1").state(ActionState.ERROR).errorCause("Error reason 1").build()));
		deltaFileRepo.save(deltaFile2);
		// Filtered, reason 1
		DeltaFile deltaFile3 = buildDeltaFile("3", null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.plusSeconds(2));
		deltaFile3.setActions(List.of(Action.newBuilder().name("action1").state(ActionState.FILTERED).errorCause("Filtered reason 1").build()));
		deltaFile3.setFiltered(true);
		deltaFileRepo.save(deltaFile3);
		// Filtered, reason 2
		DeltaFile deltaFile4 = buildDeltaFile("4", null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(3), MONGO_NOW.plusSeconds(3));
		deltaFile4.setActions(List.of(Action.newBuilder().name("action1").state(ActionState.ERROR).filteredCause("Filtered reason 2").build()));
		deltaFile4.setFiltered(true);
		deltaFileRepo.save(deltaFile4);
		DeltaFile deltaFile5 = buildDeltaFile("5", null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(3), MONGO_NOW.plusSeconds(3));
		deltaFile5.setActions(List.of(Action.newBuilder().name("action1").state(ActionState.FILTERED).filteredCause("Filtered reason 2").build()));
		deltaFile5.setFiltered(true);
		deltaFileRepo.save(deltaFile5);

		testFilter(DeltaFilesFilter.newBuilder().errorCause("reason").build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().filteredCause("reason").build(), deltaFile5);
	}

	private void testFilter(DeltaFilesFilter filter, DeltaFile... expected) {
		DeltaFiles deltaFiles = deltaFileRepo.deltaFiles(null, 50, filter, null);
		assertEquals(new ArrayList<>(Arrays.asList(expected)), deltaFiles.getDeltaFiles());
	}

	@Test
	void deleteIngressFlowPlanByPlugin() {
		clearForFlowTests();
		PluginCoordinates pluginToDelete = PluginCoordinates.builder().groupId("group").artifactId("deltafi-actions").version("1.0.0").build();

		IngressFlowPlan ingressFlowPlanA = new IngressFlowPlan("a", null, null);
		ingressFlowPlanA.setSourcePlugin(pluginToDelete);
		IngressFlowPlan ingressFlowPlanB = new IngressFlowPlan("b", null, null);
		ingressFlowPlanB.setSourcePlugin(pluginToDelete);
		IngressFlowPlan ingressFlowPlanC = new IngressFlowPlan("c", null, null);
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

		EgressFlowPlan egressFlowPlanA = new EgressFlowPlan("a", null, null, null);
		egressFlowPlanA.setSourcePlugin(pluginToDelete);
		EgressFlowPlan egressFlowPlanB = new EgressFlowPlan("b", null, null, null);
		egressFlowPlanB.setSourcePlugin(pluginToDelete);
		EgressFlowPlan egressFlowPlanC = new EgressFlowPlan("c", null, null, null);
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

	@Test
	void testDeleteByOtherVersions() {
		clearForFlowTests();
		PluginCoordinates newCoordinates = PluginCoordinates.builder().groupId("group").artifactId("deltafi-actions").version("2.0.0").build();

		IngressFlow ingressFlowA = new IngressFlow();
		ingressFlowA.setName("a");
		ingressFlowA.setSourcePlugin(newCoordinates);

		IngressFlow ingressFlowB = new IngressFlow();
		ingressFlowB.setName("b");
		ingressFlowB.setSourcePlugin(newCoordinates);

		// flow c has a different version and should be removed
		IngressFlow ingressFlowC = new IngressFlow();
		ingressFlowC.setName("c");
		ingressFlowC.setSourcePlugin(PluginCoordinates.builder().groupId("group").artifactId("deltafi-actions").version("1.0.0").build());
		ingressFlowRepo.saveAll(List.of(ingressFlowA, ingressFlowB, ingressFlowC));

		IngressFlowPlan ingressFlowPlanA = new IngressFlowPlan("a", null, null);
		ingressFlowPlanA.setSourcePlugin(newCoordinates);
		IngressFlowPlan ingressFlowPlanB = new IngressFlowPlan("b", null, null);
		ingressFlowPlanB.setSourcePlugin(newCoordinates);
		IngressFlowPlan ingressFlowPlanC = new IngressFlowPlan("c", null, null);
		ingressFlowPlanC.setSourcePlugin(PluginCoordinates.builder().groupId("group").artifactId("deltafi-actions").version("1.0.0").build());
		ingressFlowPlanRepo.saveAll(List.of(ingressFlowPlanA, ingressFlowPlanB, ingressFlowPlanC));

		assertThat(ingressFlowService.getFlowNamesByState(null)).hasSize(3).contains("a", "b", "c");
		assertThat(ingressFlowPlanRepo.findAll().stream().map(FlowPlan::getName).toList()).hasSize(3).contains("a", "b", "c");

		ingressFlowPlanService.pruneFlowsAndPlans(newCoordinates);

		assertThat(ingressFlowService.getFlowNamesByState(null)).hasSize(2).contains("a", "b");
		assertThat(ingressFlowPlanRepo.findAll().stream().map(FlowPlan::getName).toList()).hasSize(2).contains("a", "b");
	}

	@Test
	void testDeltaFilesEndpoint() {
		DeltaFile deltaFile1 = buildDeltaFile("1", "flow1", DeltaFileStage.COMPLETE, MONGO_NOW.minusSeconds(2), MONGO_NOW.minusSeconds(2));
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile("2", "flow2", DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.plusSeconds(2));
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

	@Test
	void testGetErrorSummaryByFlowDatafetcher() {
		OffsetDateTime now = OffsetDateTime.now();
		OffsetDateTime plusOne = OffsetDateTime.now().plusMinutes(1);
		OffsetDateTime plusTwo = OffsetDateTime.now().plusMinutes(2);
		loadDeltaFilesWithActionErrors(now, plusOne);

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
				new ErrorSummaryByFlowGraphQLQuery.Builder()
						.limit(5)
						.filter(ErrorSummaryFilter.newBuilder().modifiedBefore(plusTwo).build())
						.orderBy(DeltaFileOrder.newBuilder().field("flow").direction(DeltaFileDirection.DESC).build())
						.build(),
				ERRORS_BY_FLOW_PROJECTION_ROOT
		);

		ErrorsByFlow actual = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQLQueryRequest.serialize(),
				"data." + DgsConstants.QUERY.ErrorSummaryByFlow,
				ErrorsByFlow.class
		);

		assertEquals(3, actual.getCount());
		assertEquals(0, actual.getOffset());
		assertEquals(3, actual.getTotalCount());
		assertEquals(3, actual.getCountPerFlow().size());
	}

	@Test
	void testGetErrorSummaryByFlow() {
		OffsetDateTime now = OffsetDateTime.now();
		OffsetDateTime plusTwo = OffsetDateTime.now().plusMinutes(2);
		OffsetDateTime minusTwo = OffsetDateTime.now().minusDays(2);

		DeltaFile deltaFile1 = buildDeltaFile("1", "flow1", DeltaFileStage.ERROR, now, plusTwo);
		deltaFileRepo.save(deltaFile1);

		DeltaFile deltaFile2 = buildDeltaFile("2", "flow1", DeltaFileStage.COMPLETE, now, now);
		deltaFileRepo.save(deltaFile2);

		DeltaFile deltaFile3 = buildDeltaFile("3", "flow2", DeltaFileStage.ERROR, now, minusTwo);
		deltaFileRepo.save(deltaFile3);

		DeltaFile deltaFile4 = buildDeltaFile("4", "flow1", DeltaFileStage.ERROR, now, now);
		deltaFileRepo.save(deltaFile4);

		DeltaFile deltaFile5 = buildDeltaFile("5", "flow3", DeltaFileStage.ERROR, now, now);
		deltaFileRepo.save(deltaFile5);

		ErrorsByFlow firstPage = deltaFilesService.getErrorSummaryByFlow(
				0, 2, null, null);

		assertEquals(2, firstPage.getCount());
		assertEquals(0, firstPage.getOffset());
		assertEquals(3, firstPage.getTotalCount());
		assertEquals(2, firstPage.getCountPerFlow().size());

		assertEquals("flow1", firstPage.getCountPerFlow().get(0).getFlow());
		assertEquals("flow2", firstPage.getCountPerFlow().get(1).getFlow());

		assertEquals(2, firstPage.getCountPerFlow().get(0).getCount());
		assertEquals(1, firstPage.getCountPerFlow().get(1).getCount());

		assertTrue(firstPage.getCountPerFlow().get(0).getDids().containsAll(List.of("1", "4")));
		assertTrue(firstPage.getCountPerFlow().get(1).getDids().contains("3"));

		ErrorsByFlow secondPage = deltaFilesService.getErrorSummaryByFlow(
				2, 2, null, null);

		assertEquals(1, secondPage.getCount());
		assertEquals(2, secondPage.getOffset());
		assertEquals(3, secondPage.getTotalCount());
		assertEquals(1, secondPage.getCountPerFlow().size());

		assertEquals("flow3", secondPage.getCountPerFlow().get(0).getFlow());
		assertEquals(1, secondPage.getCountPerFlow().get(0).getCount());
		assertTrue(secondPage.getCountPerFlow().get(0).getDids().contains("5"));

		DeltaFile deltaFile6 = buildDeltaFile("6", "flow3", DeltaFileStage.ERROR, now, minusTwo);
		deltaFileRepo.save(deltaFile6);

		DeltaFile deltaFile7 = buildDeltaFile("7", "flow3", DeltaFileStage.ERROR, now, minusTwo);
		deltaFileRepo.save(deltaFile7);

		ErrorsByFlow filterByTime = deltaFilesService.getErrorSummaryByFlow(
				0, 99, ErrorSummaryFilter.newBuilder()
						.modifiedBefore(now)
						.build(),
				DeltaFileOrder.newBuilder()
						.field("Count")
						.direction(DeltaFileDirection.DESC)
						.build());

		assertEquals(2, filterByTime.getCount());
		assertEquals(0, filterByTime.getOffset());
		assertEquals(2, filterByTime.getTotalCount());
		assertEquals(2, filterByTime.getCountPerFlow().size());

		assertEquals(2, filterByTime.getCountPerFlow().get(0).getCount());
		assertEquals(1, filterByTime.getCountPerFlow().get(1).getCount());

		assertEquals("flow3", filterByTime.getCountPerFlow().get(0).getFlow());
		assertEquals("flow2", filterByTime.getCountPerFlow().get(1).getFlow());

		ErrorsByFlow filterByFlow = deltaFilesService.getErrorSummaryByFlow(
				0, 99, ErrorSummaryFilter.newBuilder()
						.flow("flow3")
						.modifiedBefore(now)
						.build(),
				DeltaFileOrder.newBuilder()
						.field("Flow")
						.direction(DeltaFileDirection.ASC)
						.build());

		assertEquals(1, filterByFlow.getCount());
		assertEquals(0, filterByFlow.getOffset());
		assertEquals(1, filterByFlow.getTotalCount());
		assertEquals(1, filterByFlow.getCountPerFlow().size());
		assertEquals(2, filterByFlow.getCountPerFlow().get(0).getCount());
		assertEquals("flow3", filterByFlow.getCountPerFlow().get(0).getFlow());

		ErrorsByFlow noneFound = deltaFilesService.getErrorSummaryByFlow(
				0, 99, ErrorSummaryFilter.newBuilder()
						.flow("flowNotFound")
						.modifiedBefore(now)
						.build(), null);

		assertEquals(0, noneFound.getCount());
		assertEquals(0, noneFound.getOffset());
		assertEquals(0, noneFound.getTotalCount());
		assertEquals(0, noneFound.getCountPerFlow().size());
	}

	@Test
	void testGetErrorSummaryByFlowFilterAcknowledged() {
		OffsetDateTime now = OffsetDateTime.now();
		OffsetDateTime plusTwo = OffsetDateTime.now().plusMinutes(2);
		loadDeltaFilesWithActionErrors(now, plusTwo);

		ErrorSummaryFilter filterAck = ErrorSummaryFilter.newBuilder()
				.errorAcknowledged(true)
				.flow("f3")
				.build();

		ErrorsByFlow resultsAck = deltaFilesService.getErrorSummaryByFlow(
				0, 99, filterAck, null);

		assertEquals(1, resultsAck.getCount());
		assertEquals(1, resultsAck.getCountPerFlow().size());
		assertEquals(1, resultsAck.getCountPerFlow().get(0).getCount());
		assertEquals("f3", resultsAck.getCountPerFlow().get(0).getFlow());
		assertTrue(resultsAck.getCountPerFlow().get(0).getDids().contains("6"));

		ErrorSummaryFilter filterNoAck = ErrorSummaryFilter.newBuilder()
				.errorAcknowledged(false)
				.flow("f3")
				.build();

		ErrorsByFlow resultsNoAck = deltaFilesService.getErrorSummaryByFlow(
				0, 99, filterNoAck, null);

		assertEquals(1, resultsNoAck.getCount());
		assertEquals(1, resultsNoAck.getCountPerFlow().size());
		assertEquals(2, resultsNoAck.getCountPerFlow().get(0).getCount());
		assertEquals("f3", resultsNoAck.getCountPerFlow().get(0).getFlow());
		assertTrue(resultsNoAck.getCountPerFlow().get(0).getDids().containsAll(List.of("7", "8")));

		ErrorSummaryFilter filterFlowOnly = ErrorSummaryFilter.newBuilder()
				.flow("f3")
				.build();

		ErrorsByFlow resultsForFlow = deltaFilesService.getErrorSummaryByFlow(
				0, 99, filterFlowOnly, null);

		assertEquals(1, resultsForFlow.getCount());
		assertEquals(1, resultsForFlow.getCountPerFlow().size());
		assertEquals(3, resultsForFlow.getCountPerFlow().get(0).getCount());
		assertEquals("f3", resultsForFlow.getCountPerFlow().get(0).getFlow());
		assertTrue(resultsForFlow.getCountPerFlow().get(0).getDids().containsAll(List.of("6", "7", "8")));
	}

	@Test
	void testGetErrorSummaryByMessageDatafetcher() {
		OffsetDateTime now = OffsetDateTime.now();
		OffsetDateTime plusOne = OffsetDateTime.now().plusMinutes(1);
		OffsetDateTime plusTwo = OffsetDateTime.now().plusMinutes(2);
		loadDeltaFilesWithActionErrors(now, plusOne);

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
				new ErrorSummaryByMessageGraphQLQuery.Builder()
						.limit(5)
						.filter(ErrorSummaryFilter.newBuilder().modifiedBefore(plusTwo).build())
						.orderBy(DeltaFileOrder.newBuilder().field("flow").direction(DeltaFileDirection.DESC).build())
						.build(),
				ERRORS_BY_MESSAGE_PROJECTION_ROOT
		);

		ErrorsByMessage actual = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQLQueryRequest.serialize(),
				"data." + DgsConstants.QUERY.ErrorSummaryByMessage,
				ErrorsByMessage.class
		);

		assertEquals(5, actual.getCount());
		assertEquals(0, actual.getOffset());
		assertEquals(7, actual.getTotalCount());
		assertEquals(5, actual.getCountPerMessage().size());
	}

	@Test
	void testGetErrorSummaryByMessage() {
		OffsetDateTime now = OffsetDateTime.now();
		OffsetDateTime plusTwo = OffsetDateTime.now().plusMinutes(2);

		loadDeltaFilesWithActionErrors(now, plusTwo);

		ErrorsByMessage fullSummary = deltaFilesService.getErrorSummaryByMessage(
				0, 99, null, null);

		assertEquals(0, fullSummary.getOffset());
		assertEquals(7, fullSummary.getCount());
		assertEquals(7, fullSummary.getTotalCount());
		assertEquals(7, fullSummary.getCountPerMessage().size());

		matchesCounterPerMessage(fullSummary, 0, "causeA", "f1", List.of("4", "5"));
		matchesCounterPerMessage(fullSummary, 1, "causeX", "f1", List.of("2"));
		matchesCounterPerMessage(fullSummary, 2, "causeX", "f2", List.of("1", "3"));
		matchesCounterPerMessage(fullSummary, 3, "causeY", "f2", List.of("3"));
		matchesCounterPerMessage(fullSummary, 4, "causeZ", "f1", List.of("9"));
		matchesCounterPerMessage(fullSummary, 5, "causeZ", "f2", List.of("10"));
		matchesCounterPerMessage(fullSummary, 6, "causeZ", "f3", List.of("6", "7", "8"));
	}

	@Test
	void testGetErrorSummaryByMessageOrdering() {
		OffsetDateTime now = OffsetDateTime.now();
		OffsetDateTime plusTwo = OffsetDateTime.now().plusMinutes(2);

		loadDeltaFilesWithActionErrors(now, plusTwo);

		ErrorsByMessage orderByFlow = deltaFilesService.getErrorSummaryByMessage(
				0, 4, null,
				DeltaFileOrder.newBuilder()
						.direction(DeltaFileDirection.ASC)
						.field("Flow").build());

		assertEquals(0, orderByFlow.getOffset());
		assertEquals(4, orderByFlow.getCount());
		assertEquals(7, orderByFlow.getTotalCount());
		assertEquals(4, orderByFlow.getCountPerMessage().size());

		matchesCounterPerMessage(orderByFlow, 0, "causeA", "f1", List.of("4", "5"));
		matchesCounterPerMessage(orderByFlow, 1, "causeX", "f1", List.of("2"));
		matchesCounterPerMessage(orderByFlow, 2, "causeZ", "f1", List.of("9"));
		matchesCounterPerMessage(orderByFlow, 3, "causeX", "f2", List.of("1", "3"));
	}

	@Test
	void testGetErrorSummaryByMessageFiltering() {
		OffsetDateTime now = OffsetDateTime.now();
		OffsetDateTime plusOne = OffsetDateTime.now().plusMinutes(1);
		OffsetDateTime plusTwo = OffsetDateTime.now().plusMinutes(2);

		loadDeltaFilesWithActionErrors(now, plusTwo);

		ErrorSummaryFilter filterBefore = ErrorSummaryFilter.newBuilder()
				.modifiedBefore(plusOne).build();

		ErrorsByMessage resultsBefore = deltaFilesService.getErrorSummaryByMessage(
				0, 99, filterBefore, null);

		assertEquals(0, resultsBefore.getOffset());
		assertEquals(6, resultsBefore.getCount());
		assertEquals(6, resultsBefore.getTotalCount());
		assertEquals(6, resultsBefore.getCountPerMessage().size());

		// no 'causeA' entry
		matchesCounterPerMessage(resultsBefore, 0, "causeX", "f1", List.of("2"));
		matchesCounterPerMessage(resultsBefore, 1, "causeX", "f2", List.of("1", "3"));
		matchesCounterPerMessage(resultsBefore, 2, "causeY", "f2", List.of("3"));
		matchesCounterPerMessage(resultsBefore, 3, "causeZ", "f1", List.of("9"));
		matchesCounterPerMessage(resultsBefore, 4, "causeZ", "f2", List.of("10"));
		matchesCounterPerMessage(resultsBefore, 5, "causeZ", "f3", List.of("6", "7", "8"));

		ErrorSummaryFilter filterAfter = ErrorSummaryFilter.newBuilder()
				.modifiedAfter(plusOne).build();

		ErrorsByMessage resultAfter = deltaFilesService.getErrorSummaryByMessage(
				0, 99, filterAfter, null);

		assertEquals(0, resultAfter.getOffset());
		assertEquals(1, resultAfter.getCount());
		assertEquals(1, resultAfter.getTotalCount());
		assertEquals(1, resultAfter.getCountPerMessage().size());
		matchesCounterPerMessage(resultAfter, 0, "causeA", "f1", List.of("4", "5"));
	}

	@Test
	void testGetErrorSummaryByMessagePaging() {
		OffsetDateTime now = OffsetDateTime.now();
		OffsetDateTime plusTwo = OffsetDateTime.now().plusMinutes(2);

		loadDeltaFilesWithActionErrors(now, plusTwo);

		ErrorSummaryFilter filter = ErrorSummaryFilter.newBuilder()
				.errorAcknowledged(false)
				.flow("f2").build();
		DeltaFileOrder order = DeltaFileOrder.newBuilder()
				.direction(DeltaFileDirection.DESC)
				.field("Count").build();

		ErrorsByMessage firstPage = deltaFilesService.getErrorSummaryByMessage(
				0, 2, filter, order);

		assertEquals(0, firstPage.getOffset());
		assertEquals(2, firstPage.getCount());
		assertEquals(3, firstPage.getTotalCount());
		assertEquals(2, firstPage.getCountPerMessage().size());
		matchesCounterPerMessage(firstPage, 0, "causeX", "f2", List.of("1", "3"));
		matchesCounterPerMessage(firstPage, 1, "causeZ", "f2", List.of("10"));

		ErrorsByMessage pageTwo = deltaFilesService.getErrorSummaryByMessage(
				2, 2, filter, order);

		assertEquals(2, pageTwo.getOffset());
		assertEquals(1, pageTwo.getCount());
		assertEquals(3, pageTwo.getTotalCount());
		assertEquals(1, pageTwo.getCountPerMessage().size());
		matchesCounterPerMessage(pageTwo, 0, "causeY", "f2", List.of("3"));

		ErrorsByMessage invalidPage = deltaFilesService.getErrorSummaryByMessage(
				4, 2, filter, order);

		// there was only enough data for two pages
		assertEquals(4, invalidPage.getOffset());
		assertEquals(0, invalidPage.getCount());
		assertEquals(3, invalidPage.getTotalCount());
		assertEquals(0, invalidPage.getCountPerMessage().size());
	}

	@Test
	void testGetErrorSummaryByMessageNoeFound() {
		OffsetDateTime now = OffsetDateTime.now();
		OffsetDateTime plusTwo = OffsetDateTime.now().plusMinutes(2);

		loadDeltaFilesWithActionErrors(now, plusTwo);

		ErrorsByMessage noneFound = deltaFilesService.getErrorSummaryByMessage(
				0, 99, ErrorSummaryFilter.newBuilder()
						.flow("flowNotFound").build(), null);

		assertEquals(0, noneFound.getOffset());
		assertEquals(0, noneFound.getCount());
		assertEquals(0, noneFound.getTotalCount());
		assertEquals(0, noneFound.getCountPerMessage().size());
	}

	private void loadDeltaFilesWithActionErrors(OffsetDateTime now, OffsetDateTime later) {
		// causeX, f1: 1, f2: 2
		// _AND_ causeY, f2: 1
		deltaFileRepo.save(Util.buildErrorDeltaFile(
				"1", "f2", "causeX", "x", now, now, false, true, null));
		deltaFileRepo.save(Util.buildErrorDeltaFile(
				"2", "f1", "causeX", "x", now));
		deltaFileRepo.save(Util.buildErrorDeltaFile(
				"3", "f2", "causeX", "x", now, now, true, true, "causeY"));

		// causeA, f1: 2
		deltaFileRepo.save(Util.buildErrorDeltaFile(
				"4", "f1", "causeA", "x", now, later, false, false, null));
		deltaFileRepo.save(Util.buildErrorDeltaFile(
				"5", "f1", "causeA", "x", now, later, true, true, null));

		// causeZ, f2: 1, f3: 3. f1: 1 (which is not the last action)
		DeltaFile deltaFileWithAck = Util.buildErrorDeltaFile(
				"6", "f3", "causeZ", "x", now);
		deltaFileWithAck.setErrorAcknowledged(now);
		deltaFileRepo.save(deltaFileWithAck);
		deltaFileRepo.save(Util.buildErrorDeltaFile(
				"7", "f3", "causeZ", "x", now));
		deltaFileRepo.save(Util.buildErrorDeltaFile(
				"8", "f3", "causeZ", "x", now));
		deltaFileRepo.save(Util.buildErrorDeltaFile(
				"9", "f1", "causeZ", "x", now, now, true, false, null));
		deltaFileRepo.save(Util.buildErrorDeltaFile(
				"10", "f2", "causeZ", "x", now, now, false, true, null));

		// these have no errors
		deltaFileRepo.save(buildDeltaFile(
				"11", "f1", DeltaFileStage.COMPLETE, now, now));
		deltaFileRepo.save(buildDeltaFile(
				"12", "f4", DeltaFileStage.COMPLETE, now, now));
	}

	private void matchesCounterPerMessage(ErrorsByMessage result, int index, String cause, String flow, List<String> dids) {
		assertEquals(cause, result.getCountPerMessage().get(index).getMessage());
		assertEquals(flow, result.getCountPerMessage().get(index).getFlow());
		assertEquals(dids.size(), result.getCountPerMessage().get(index).getCount());
		assertEquals(dids.size(), result.getCountPerMessage().get(index).getDids().size());
		assertTrue(result.getCountPerMessage().get(index).getDids().containsAll(dids));
	}

	@Test
	void updateProperties() {
		DeltaFiProperties current = deltaFiPropertiesService.getDeltaFiProperties();
		assertThat(current.getSystemName()).isEqualTo("DeltaFi");
		assertThat(current.getSetProperties()).isEmpty();

		assertThat(deltaFiPropertiesRepo.updateProperties(Map.of(PropertyType.SYSTEM_NAME, "newName"))).isTrue();

		deltaFiPropertiesService.refreshProperties();
		current = deltaFiPropertiesService.getDeltaFiProperties();

		assertThat(current.getSystemName()).isEqualTo("newName");
		assertThat(current.getSetProperties()).hasSize(1).contains(PropertyType.SYSTEM_NAME);

		// already newName no changes made, return false
		assertThat(deltaFiPropertiesRepo.updateProperties(Map.of(PropertyType.SYSTEM_NAME, "newName"))).isFalse();
	}

	@Test
	void unsetProperties() {
		DeltaFiProperties deltaFiProperties = new DeltaFiProperties();
		deltaFiProperties.setSystemName("newName");
		deltaFiProperties.getSetProperties().add(PropertyType.SYSTEM_NAME);

		deltaFiPropertiesRepo.save(deltaFiProperties);
		deltaFiPropertiesService.refreshProperties();

		DeltaFiProperties current = deltaFiPropertiesService.getDeltaFiProperties();
		assertThat(current.getSystemName()).isEqualTo("newName");
		assertThat(current.getSetProperties()).hasSize(1).contains(PropertyType.SYSTEM_NAME);


		assertThat(deltaFiPropertiesRepo.unsetProperties(List.of(PropertyType.SYSTEM_NAME))).isTrue();
		deltaFiPropertiesService.refreshProperties();

		current = deltaFiPropertiesService.getDeltaFiProperties();
		assertThat(current.getSystemName()).isEqualTo("DeltaFi");
		assertThat(current.getSetProperties()).isEmpty();

		// second time no change is needed so it returns false
		assertThat(deltaFiPropertiesRepo.unsetProperties(List.of(PropertyType.SYSTEM_NAME))).isFalse();

	}

	@Test
	void testActionRegisteredQuery() {
		actionDescriptorRepo.deleteAll();

		ActionDescriptor transformActionDescriptor = ActionDescriptor.builder().name("transformAction").build();
		ActionDescriptor loadActionDescriptor = ActionDescriptor.builder().name("loadAction").build();
		// name does not match
		ActionDescriptor formatActionDescriptor = ActionDescriptor.builder().name("otherFormatAction").build();

		actionDescriptorRepo.saveAll(List.of(transformActionDescriptor, loadActionDescriptor, formatActionDescriptor));

		assertThat(actionDescriptorRepo.countAllByNameIn(List.of("transformAction", "loadAction", "formatAction"))).isEqualTo(2);
		assertThat(actionDescriptorRepo.count()).isEqualTo(3);
	}

	private DeltaFile loadDeltaFile(String did) {
		return deltaFileRepo.findById(did).orElse(null);
	}

	private void verifyActionEventResults(DeltaFile expected, String ... forActions) {
		DeltaFile afterMutation = deltaFilesService.getDeltaFile(expected.getDid());
		assertEqualsIgnoringDates(expected, afterMutation);

		Mockito.verify(actionEventQueue).putActions(actionInputListCaptor.capture());
		List<ActionInput> actionInputs = actionInputListCaptor.getValue();
		assertThat(actionInputs).hasSize(forActions.length);
		for (int i = 0; i < forActions.length; i++) {
			ActionInput actionInput = actionInputs.get(i);
			assertThat(actionInput.getActionContext().getName()).isEqualTo(forActions[i]);
			assertEqualsIgnoringDates(expected.forQueue(forActions[i]), actionInput.getDeltaFile());
		}
	}

	private IngressFlow buildIngressFlow(FlowState flowState) {
		LoadActionConfiguration lc = new LoadActionConfiguration("sampleIngress.SampleLoadAction", "type");
		TransformActionConfiguration tc = new TransformActionConfiguration("sampleIngress.Utf8TransformAction", "type");
		TransformActionConfiguration tc2 = new TransformActionConfiguration("sampleIngress.SampleTransformAction", "type");

		return buildFlow(INGRESS_FLOW_NAME, lc, List.of(tc, tc2), flowState, false);
	}

	private IngressFlow buildRunningFlow(String name, LoadActionConfiguration loadActionConfiguration, List<TransformActionConfiguration> transforms, boolean testMode) {
		return buildFlow(name, loadActionConfiguration, transforms, FlowState.RUNNING, testMode);
	}

	private IngressFlow buildFlow(String name, LoadActionConfiguration loadActionConfiguration, List<TransformActionConfiguration> transforms, FlowState flowState, boolean testMode) {
		IngressFlow ingressFlow = new IngressFlow();
		ingressFlow.setName(name);
		ingressFlow.getFlowStatus().setState(flowState);
		ingressFlow.setLoadAction(loadActionConfiguration);
		ingressFlow.setTransformActions(transforms);
		ingressFlow.setTestMode(testMode);
		return ingressFlow;
	}

	private EgressFlow buildEgressFlow(FlowState flowState) {
		FormatActionConfiguration sampleFormat = new FormatActionConfiguration("sampleEgress.SampleFormatAction", "type", List.of("sampleDomain"));
		sampleFormat.setRequiresEnrichments(List.of("sampleEnrichment"));

		EgressActionConfiguration sampleEgress = new EgressActionConfiguration("sampleEgress.SampleEgressAction", "type");

		return buildFlow(EGRESS_FLOW_NAME, sampleFormat, sampleEgress, flowState, false);
	}

	private EnrichFlow buildEnrichFlow(FlowState flowState) {
		EnrichFlow enrichFlow = new EnrichFlow();
		enrichFlow.setName("sampleEnrich");

		DomainActionConfiguration sampleDomain = new DomainActionConfiguration("sampleEnrich.SampleDomainAction", "type", List.of("sampleDomain"));

		EnrichActionConfiguration sampleEnrich = new EnrichActionConfiguration("sampleEnrich.SampleEnrichAction", "type", List.of("sampleDomain"));
		sampleEnrich.setRequiresMetadataKeyValues(List.of(new KeyValue("loadSampleType", "load-sample-type")));

		enrichFlow.setDomainActions(List.of(sampleDomain));
		enrichFlow.setEnrichActions(List.of(sampleEnrich));
		enrichFlow.getFlowStatus().setState(flowState);
		return enrichFlow;
	}

	private EgressFlow buildRunningFlow(String name, FormatActionConfiguration formatAction, EgressActionConfiguration egressAction, boolean testMode) {
		return buildFlow(name, formatAction, egressAction, FlowState.RUNNING, testMode);
	}

	private EgressFlow buildFlow(String name, FormatActionConfiguration formatAction, EgressActionConfiguration egressAction, FlowState flowState, boolean testMode) {
		EgressFlow egressFlow = new EgressFlow();
		egressFlow.setName(name);
		egressFlow.setFormatAction(formatAction);
		egressFlow.setEgressAction(egressAction);
		egressFlow.setIncludeIngressFlows(null);
		egressFlow.getFlowStatus().setState(flowState);
		egressFlow.setTestMode(testMode);
		return egressFlow;
	}

	void clearForFlowTests() {
		ingressFlowRepo.deleteAll();
		ingressFlowPlanRepo.deleteAll();
		ingressFlowService.refreshCache();
		enrichFlowRepo.deleteAll();
		enrichFlowPlanRepo.deleteAll();
		enrichFlowService.refreshCache();
		egressFlowRepo.deleteAll();
		egressFlowPlanRepo.deleteAll();
		egressFlowService.refreshCache();
		pluginVariableRepo.deleteAll();
	}

	@Test
	void domains() {
		deltaFileRepo.insert(DeltaFile.newBuilder()
				.domains(List.of(Domain.newBuilder().name("a").build(), Domain.newBuilder().name("b").build()))
				.build());
		deltaFileRepo.insert(DeltaFile.newBuilder()
				.domains(List.of(Domain.newBuilder().name("b").build(), Domain.newBuilder().name("c").build()))
				.build());

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(new DomainsGraphQLQuery("query"));

		List<String> actual = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQLQueryRequest.serialize(),
				"data." + DgsConstants.QUERY.Domains,
				new TypeRef<>() {}
		);

		assertEquals(List.of("a", "b", "c"), actual);
	}

	@Test
	void domainsEmpty() {
		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(new DomainsGraphQLQuery("query"));

		List<String> actual = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQLQueryRequest.serialize(),
				"data." + DgsConstants.QUERY.Domains,
				new TypeRef<>() {}
		);

		assertEquals(Collections.emptyList(), actual);
	}

	@Test
	void indexedMetadata() {
		deltaFileRepo.insert(DeltaFile.newBuilder()
				.domains(List.of(Domain.newBuilder().name("a").build(), Domain.newBuilder().name("b").build()))
				.indexedMetadata(Map.of("x", "1", "y", "2"))
				.build());
		deltaFileRepo.insert(DeltaFile.newBuilder()
				.domains(List.of(Domain.newBuilder().name("b").build(), Domain.newBuilder().name("c").build()))
				.indexedMetadata(Map.of("y", "3", "z", "4"))
				.build());

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(new IndexedMetadataKeysGraphQLQuery());

		List<String> actual = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQLQueryRequest.serialize(),
				"data." + DgsConstants.QUERY.IndexedMetadataKeys,
				new TypeRef<>() {}
		);

		assertEquals(List.of("x", "y", "z"), actual);
	}

	@Test
	void indexedMetadataPerDomain() {
		deltaFileRepo.insert(DeltaFile.newBuilder()
				.domains(List.of(Domain.newBuilder().name("a").build(), Domain.newBuilder().name("b").build()))
				.indexedMetadata(Map.of("x", "1", "y", "2"))
				.build());
		deltaFileRepo.insert(DeltaFile.newBuilder()
				.domains(List.of(Domain.newBuilder().name("b").build(), Domain.newBuilder().name("c").build()))
				.indexedMetadata(Map.of("y", "3", "z", "4"))
				.build());

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(IndexedMetadataKeysGraphQLQuery
				.newRequest()
				.domain("a")
				.build());

		List<String> actual = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQLQueryRequest.serialize(),
				"data." + DgsConstants.QUERY.IndexedMetadataKeys,
				new TypeRef<>() {}
		);

		assertEquals(List.of("x", "y"), actual);
	}

	@Test
	void indexedMetadataEmpty() {
		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(new IndexedMetadataKeysGraphQLQuery());

		List<String> actual = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQLQueryRequest.serialize(),
				"data." + DgsConstants.QUERY.IndexedMetadataKeys,
				new TypeRef<>() {}
		);

		assertEquals(Collections.emptyList(), actual);
	}

	static final String CONTENT = "STARLORD was here";
	static final String METADATA = "{\"key\": \"value\"}";
	static final String FILENAME = "incoming.txt";
	static final String FLOW = "theFlow";
	static final String INCOMING_FLOWFILE_METADATA = "{\"fromHeader\": \"this is from header\", \"overwrite\": \"emacs is awesome\"}";
	static final Map<String, String> RESOLVED_FLOWFILE_METADATA = Map.of(
			"fromHeader", "this is from header",
			"overwrite", "vim is awesome",
			"fromFlowfile", "youbetcha");
	static final Map<String, String> FLOWFILE_METADATA_NO_HEADERS = Map.of(
			"filename", FILENAME,
			"flow", FLOW,
			"overwrite", "vim is awesome",
			"fromFlowfile", "youbetcha");
	static final Map<String, String> FLOWFILE_METADATA_NO_HEADERS_NO_FLOW = Map.of(
			"filename", FILENAME,
			"overwrite", "vim is awesome",
			"fromFlowfile", "youbetcha");
	static final String MEDIA_TYPE = MediaType.APPLICATION_OCTET_STREAM;
	static final String USERNAME = "myname";
	static final String DID = "did";
	static final ContentReference CONTENT_REFERENCE = new ContentReference(MEDIA_TYPE, new Segment(FILENAME, 0, CONTENT.length(), DID));
	static final IngressService.IngressResult INGRESS_RESULT = new IngressService.IngressResult(CONTENT_REFERENCE, FLOW, FILENAME, DID);

	private ResponseEntity<String> ingress(String filename, String flow, String metadata, byte[] body, String contentType) {
		HttpHeaders headers = new HttpHeaders();
		if (filename != null) {
			headers.add("Filename", filename);
		}
		if (flow != null) {
			headers.add("Flow", flow);
		}
		if (metadata != null) {
			headers.add("Metadata", metadata);
		}
		headers.add(HttpHeaders.CONTENT_TYPE, contentType);
		headers.add(USER_HEADER, USERNAME);
		headers.add(DeltaFiConstants.PERMISSIONS_HEADER, DeltaFiConstants.ADMIN_PERMISSION);
		HttpEntity<byte[]> request = new HttpEntity<>(body, headers);

		return restTemplate.postForEntity("/deltafile/ingress", request, String.class);
	}

	@Test
	@SneakyThrows
	void testIngress() {
		Mockito.when(ingressService.ingressData(any(), eq(FILENAME), eq(FLOW), eq(METADATA), eq(MEDIA_TYPE))).thenReturn(INGRESS_RESULT);

		ResponseEntity<String> response = ingress(FILENAME, FLOW, METADATA, CONTENT.getBytes(), MediaType.APPLICATION_OCTET_STREAM);
		assertEquals(200, response.getStatusCode().value());
		assertEquals(INGRESS_RESULT.getDid(), response.getBody());

		Mockito.verify(ingressService).ingressData(any(), eq(FILENAME), eq(FLOW), eq(METADATA), eq(MEDIA_TYPE));

		Map<String, String> tags = tagsFor("ingress", INGRESS_ACTION, FLOW, null);
		Mockito.verify(metricRepository).increment(DeltaFiConstants.FILES_IN, tags, 1);
		Mockito.verify(metricRepository).increment(DeltaFiConstants.BYTES_IN, tags, CONTENT.length());
	}

	@Test
	@SneakyThrows
	void testIngress_missingFlow() {
		Mockito.when(ingressService.ingressData(any(), eq(FILENAME), isNull(), eq(METADATA), eq(MEDIA_TYPE))).thenReturn(INGRESS_RESULT);

		ResponseEntity<String> response = ingress(FILENAME, null, METADATA, CONTENT.getBytes(), MediaType.APPLICATION_OCTET_STREAM);

		assertEquals(200, response.getStatusCode().value());
		assertEquals(INGRESS_RESULT.getContentReference().getSegments().get(0).getDid(), response.getBody());

		ArgumentCaptor<InputStream> is = ArgumentCaptor.forClass(InputStream.class);
		Mockito.verify(ingressService).ingressData(is.capture(), eq(FILENAME), isNull(), eq(METADATA), eq(MEDIA_TYPE));
		// TODO: EOF inputStream?
		// assertThat(new String(is.getValue().readAllBytes()), equalTo(CONTENT));

		Map<String, String> tags = tagsFor("ingress", INGRESS_ACTION, FLOW, null);
		Mockito.verify(metricRepository).increment(DeltaFiConstants.FILES_IN, tags, 1);
		Mockito.verify(metricRepository).increment(DeltaFiConstants.BYTES_IN, tags, CONTENT.length());
	}

	@Test
	void testIngress_missingFilename() {
		ResponseEntity<String> response = ingress(null, FLOW, METADATA, CONTENT.getBytes(), MediaType.APPLICATION_OCTET_STREAM);
		assertEquals(400, response.getStatusCode().value());
	}

	@Test
	void testIngress_disabled() {
		Mockito.when(ingressService.isEnabled()).thenReturn(false);
		ResponseEntity<String> response = ingress(null, FLOW, METADATA, CONTENT.getBytes(), MediaType.APPLICATION_OCTET_STREAM);
		assertEquals(503, response.getStatusCode().value());
	}

	@Test
	@SneakyThrows
	void testIngress_storageLimit() {
		Mockito.when(diskSpaceService.isContentStorageDepleted()).thenReturn(true);
		ResponseEntity<String> response = ingress(null, FLOW, METADATA, CONTENT.getBytes(), MediaType.APPLICATION_OCTET_STREAM);
		assertEquals(507, response.getStatusCode().value());
	}

	@Test
	@SneakyThrows
	void testIngress_flowfile() {
		Mockito.when(ingressService.ingressData(any(), eq(FILENAME), eq(FLOW), eq(RESOLVED_FLOWFILE_METADATA), eq(MEDIA_TYPE))).thenReturn(INGRESS_RESULT);
		ResponseEntity<String> response = ingress(FILENAME, FLOW, INCOMING_FLOWFILE_METADATA, flowfile(Map.of("overwrite", "vim is awesome", "fromFlowfile", "youbetcha")), FLOWFILE_V1_MEDIA_TYPE);

		assertEquals(200, response.getStatusCode().value());

		ArgumentCaptor<InputStream> is = ArgumentCaptor.forClass(InputStream.class);

		Mockito.verify(ingressService).ingressData(is.capture(),
				eq(FILENAME),
				eq(FLOW),
				eq(RESOLVED_FLOWFILE_METADATA),
				eq(MEDIA_TYPE));

		// TODO: EOF inputStream?
		// assertThat(new String(is.getValue().readAllBytes()), equalTo(CONTENT));

		Map<String, String> tags = tagsFor("ingress", INGRESS_ACTION, FLOW, null);
		Mockito.verify(metricRepository).increment(DeltaFiConstants.FILES_IN, tags, 1);
		Mockito.verify(metricRepository).increment(DeltaFiConstants.BYTES_IN, tags, CONTENT.length());
	}

	@Test
	@SneakyThrows
	void testIngress_flowfile_noParamsOrHeaders() {
		Mockito.when(ingressService.ingressData(any(), eq(FILENAME), eq(FLOW), eq(FLOWFILE_METADATA_NO_HEADERS), eq(MEDIA_TYPE))).thenReturn(INGRESS_RESULT);
		ResponseEntity<String> response = ingress(null, null, null,
				flowfile(Map.of(
						"flow", FLOW,
						"filename", FILENAME,
						"overwrite", "vim is awesome",
						"fromFlowfile", "youbetcha")),
				FLOWFILE_V1_MEDIA_TYPE);

		assertEquals(200, response.getStatusCode().value());

		ArgumentCaptor<InputStream> is = ArgumentCaptor.forClass(InputStream.class);

		Mockito.verify(ingressService).ingressData(is.capture(),
				eq(FILENAME),
				eq(FLOW),
				eq(FLOWFILE_METADATA_NO_HEADERS),
				eq(MEDIA_TYPE));

		// TODO: EOF inputStream?
		// assertThat(new String(is.getValue().readAllBytes()), equalTo(CONTENT));
	}

	@Test @SneakyThrows
	void testIngress_flowfile_missingFilename() {
		ResponseEntity<String> response = ingress(null, null, null,
				flowfile(Map.of(
						"flow", FLOW,
						"overwrite", "vim is awesome",
						"fromFlowfile", "youbetcha")),
				FLOWFILE_V1_MEDIA_TYPE);

		assertEquals(400, response.getStatusCode().value());
		assertEquals("Filename must be passed in as a header or flowfile attribute", response.getBody());
	}

	@Test
	@SneakyThrows
	void testIngress_flowfile_missingFlow() {
		Mockito.when(ingressService.ingressData(any(), eq(FILENAME), isNull(), eq(FLOWFILE_METADATA_NO_HEADERS_NO_FLOW), eq(MEDIA_TYPE))).thenReturn(INGRESS_RESULT);
		ResponseEntity<String> response = ingress(null, null, null,
				flowfile(Map.of(
						"filename", FILENAME,
						"overwrite", "vim is awesome",
						"fromFlowfile", "youbetcha")),
				FLOWFILE_V1_MEDIA_TYPE);

		assertEquals(200, response.getStatusCode().value());

		ArgumentCaptor<InputStream> is = ArgumentCaptor.forClass(InputStream.class);

		Mockito.verify(ingressService).ingressData(is.capture(),
				eq(FILENAME),
				isNull(),
				eq(FLOWFILE_METADATA_NO_HEADERS_NO_FLOW),
				eq(MEDIA_TYPE));

		// TODO: EOF inputStream?
		// assertThat(new String(is.getValue().readAllBytes()), equalTo(CONTENT));
	}

	@Test
	void testPluginImageRepository() {
		pluginImageRepositoryRepo.deleteAll();
		PluginImageRepository pluginImageRepository = new PluginImageRepository();
		pluginImageRepository.setPluginGroupIds(List.of("a", "b"));

		pluginImageRepositoryRepo.save(pluginImageRepository);

		assertThat(pluginImageRepositoryRepo.findByPluginGroupIds("a")).isPresent().contains(pluginImageRepository);
		assertThat(pluginImageRepositoryRepo.findByPluginGroupIds("b")).isPresent().contains(pluginImageRepository);
		assertThat(pluginImageRepositoryRepo.findByPluginGroupIds("c")).isEmpty();
	}

	@Test
	void testPluginImageRepository_duplicateGroupId() {
		pluginImageRepositoryRepo.deleteAll();
		PluginImageRepository pluginImageRepository = new PluginImageRepository();
		pluginImageRepository.setImageRepositoryBase("docker");
		pluginImageRepository.setPluginGroupIds(List.of("a", "b"));

		pluginImageRepositoryRepo.save(pluginImageRepository);

		PluginImageRepository pluginGroupB = new PluginImageRepository();
		pluginGroupB.setImageRepositoryBase("gitlab");
		pluginGroupB.setPluginGroupIds(List.of("b"));

		assertThatThrownBy(() -> pluginImageRepositoryRepo.save(pluginGroupB));
	}

	@SneakyThrows
	byte[] flowfile(Map<String, String> metadata) {
		FlowFilePackagerV1 packager = new FlowFilePackagerV1();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		packager.packageFlowFile(new ByteArrayInputStream(CONTENT.getBytes()),
				out, metadata, CONTENT.length());
		return out.toByteArray();
	}

	@Test
	void testSetContentDeletedByDidIn() {
		DeltaFile deltaFile1 = buildDeltaFile("1", null, DeltaFileStage.COMPLETE, MONGO_NOW.minusSeconds(2), MONGO_NOW.minusSeconds(2));
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile("2", null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.plusSeconds(2));
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = buildDeltaFile("3", null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.plusSeconds(2));
		deltaFileRepo.save(deltaFile3);

		OffsetDateTime timestamp = MONGO_NOW;

		deltaFileRepo.setContentDeletedByDidIn(List.of("1", "3", "4"), timestamp, "MyPolicy");
		DeltaFiles deltaFiles = deltaFileRepo.deltaFiles(null, 50, new DeltaFilesFilter(), null);
		deltaFile1.setContentDeleted(timestamp);
		deltaFile1.setContentDeletedReason("MyPolicy");
		deltaFile1.setVersion(2);
		deltaFile3.setContentDeleted(timestamp);
		deltaFile3.setContentDeletedReason("MyPolicy");
		deltaFile3.setVersion(2);
		assertEquals(deltaFiles.getDeltaFiles(), List.of(deltaFile3, deltaFile2, deltaFile1));
	}


}
