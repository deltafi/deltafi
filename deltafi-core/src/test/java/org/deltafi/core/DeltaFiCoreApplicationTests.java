/*
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
import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import com.netflix.graphql.dgs.exceptions.QueryException;
import io.minio.MinioClient;
import lombok.SneakyThrows;
import org.deltafi.common.action.ActionEventQueue;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.content.Segment;
import org.deltafi.common.resource.Resource;
import org.deltafi.common.types.*;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.datafetchers.FlowPlanDatafetcherTestHelper;
import org.deltafi.core.datafetchers.PropertiesDatafetcherTestHelper;
import org.deltafi.core.datafetchers.ResumePolicyDatafetcherTestHelper;
import org.deltafi.core.delete.DeletePolicyWorker;
import org.deltafi.core.delete.DeleteRunner;
import org.deltafi.core.exceptions.IngressMetadataException;
import org.deltafi.core.exceptions.IngressStorageException;
import org.deltafi.core.exceptions.IngressUnavailableException;
import org.deltafi.core.generated.DgsConstants;
import org.deltafi.core.generated.client.*;
import org.deltafi.core.generated.types.ConfigType;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.metrics.MetricService;
import org.deltafi.core.plugin.PluginRepository;
import org.deltafi.core.plugin.SystemPluginService;
import org.deltafi.core.plugin.deployer.DeployerService;
import org.deltafi.core.plugin.deployer.credential.CredentialProvider;
import org.deltafi.core.plugin.deployer.image.PluginImageRepository;
import org.deltafi.core.plugin.deployer.image.PluginImageRepositoryRepo;
import org.deltafi.core.repo.*;
import org.deltafi.core.services.*;
import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.snapshot.SystemSnapshotDatafetcherTestHelper;
import org.deltafi.core.snapshot.SystemSnapshotRepo;
import org.deltafi.core.types.PluginVariables;
import org.deltafi.core.types.ResumePolicy;
import org.deltafi.core.types.*;
import org.deltafi.core.util.Util;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
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
import org.springframework.data.mongodb.core.MongoTemplate;
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
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.deltafi.common.constant.DeltaFiConstants.INGRESS_ACTION;
import static org.deltafi.common.constant.DeltaFiConstants.USER_HEADER;
import static org.deltafi.common.test.TestConstants.MONGODB_CONTAINER;
import static org.deltafi.common.types.ActionState.QUEUED;
import static org.deltafi.common.types.DeltaFile.CURRENT_SCHEMA_VERSION;
import static org.deltafi.core.datafetchers.DeletePolicyDatafetcherTestHelper.*;
import static org.deltafi.core.datafetchers.DeltaFilesDatafetcherTestHelper.*;
import static org.deltafi.core.metrics.MetricsUtil.extendTagsForAction;
import static org.deltafi.core.metrics.MetricsUtil.tagsFor;
import static org.deltafi.core.plugin.PluginDataFetcherTestHelper.*;
import static org.deltafi.core.util.Constants.*;
import static org.deltafi.core.util.FlowBuilders.*;
import static org.deltafi.core.util.FullFlowExemplars.*;
import static org.deltafi.core.util.Util.*;
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
		"schedule.errorCount=false",
		"schedule.propertySync=false"})
@Testcontainers
class DeltaFiCoreApplicationTests {

	@Container
	public final static MongoDBContainer MONGO_DB_CONTAINER = new MongoDBContainer(MONGODB_CONTAINER);

	@DynamicPropertySource
	static void setProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.mongodb.uri", MONGO_DB_CONTAINER::getReplicaSetUrl);
	}

	@Autowired
	DgsQueryExecutor dgsQueryExecutor;

	@Autowired
	ResumePolicyService resumePolicyService;

	@Autowired
	DeltaFilesService deltaFilesService;

	@Autowired
	DeltaFiPropertiesService deltaFiPropertiesService;

	@Autowired
	DeleteRunner deleteRunner;

	@Autowired
	DeltaFileRepo deltaFileRepo;

	@Autowired
	ActionDescriptorRepo actionDescriptorRepo;

	@Autowired
	DeletePolicyRepo deletePolicyRepo;

	@Autowired
	PluginRepository pluginRepository;

	@Autowired
	TimedIngressFlowService timedIngressFlowService;

	@Autowired
	TransformFlowService transformFlowService;

	@Autowired
	EgressFlowService egressFlowService;

	@Autowired
	TransformFlowRepo transformFlowRepo;

	@Autowired
	TimedIngressFlowRepo timedIngressFlowRepo;

	@Autowired
	TimedIngressFlowPlanRepo timedIngressFlowPlanRepo;

	@Autowired
	EgressFlowRepo egressFlowRepo;

	@Autowired
	TransformFlowPlanRepo transformFlowPlanRepo;

	@Autowired
	EgressFlowPlanRepo egressFlowPlanRepo;

	@Autowired
	PluginVariableRepo pluginVariableRepo;

	@Autowired
	PluginVariableService pluginVariableService;

	@Autowired
	SystemPluginService systemPluginService;

	@Autowired
	ResumePolicyRepo resumePolicyRepo;

	@Autowired
	DeltaFiPropertiesRepo deltaFiPropertiesRepo;

	@Autowired
	EgressFlowPlanService egressFlowPlanService;

	@Autowired
	PluginImageRepositoryRepo pluginImageRepositoryRepo;

	@Autowired
	SystemSnapshotRepo systemSnapshotRepo;

	@Autowired
	MongoTemplate mongoTemplate;

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
	MetricService metricService;

	@MockBean
	ActionEventQueue actionEventQueue;

	@MockBean
	DeployerService deployerService;

	@MockBean
	CredentialProvider credentialProvider;

	@Autowired
	QueueManagementService queueManagementService;

	// mongo eats microseconds, jump through hoops
	private final OffsetDateTime MONGO_NOW = OffsetDateTime.of(LocalDateTime.ofEpochSecond(OffsetDateTime.now().toInstant().toEpochMilli(), 0, ZoneOffset.UTC), ZoneOffset.UTC);

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
		deltaFileRepo.deleteAll();
		resumePolicyRepo.deleteAll();
		resumePolicyService.refreshCache();
		loadConfig();

		Mockito.clearInvocations(actionEventQueue);

		// Set the security context for the tests that DgsQueryExecutor
		SecurityContextHolder.clearContext();
		SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
		Authentication authentication = new PreAuthenticatedAuthenticationToken("name", "pass", List.of(new SimpleGrantedAuthority(DeltaFiConstants.ADMIN_PERMISSION)));
		securityContext.setAuthentication(authentication);
		SecurityContextHolder.setContext(securityContext);

		Mockito.when(diskSpaceService.isContentStorageDepleted()).thenReturn(false);
	}

	void refreshFlowCaches() {
		transformFlowService.refreshCache();
		egressFlowService.refreshCache();
		timedIngressFlowService.refreshCache();
	}

	void loadConfig() {
		loadTransformConfig();
		loadEgressConfig();
		loadTimedIngressConfig();
	}

	void loadTransformConfig() {
		transformFlowRepo.deleteAll();

		EgressActionConfiguration ec = new EgressActionConfiguration("SampleEgressAction", "type");
		TransformActionConfiguration tc = new TransformActionConfiguration("Utf8TransformAction", "type");
		TransformActionConfiguration tc2 = new TransformActionConfiguration("SampleTransformAction", "type");

		TransformFlow sampleTransformFlow = buildRunningFlow(TRANSFORM_FLOW_NAME, ec, List.of(tc, tc2), false);
		TransformFlow retryFlow = buildRunningFlow("theTransformFlow", ec, null, false);
		TransformFlow childFlow = buildRunningFlow("transformChildFlow", ec, List.of(tc2), false);

		transformFlowRepo.saveAll(List.of(sampleTransformFlow, retryFlow, childFlow));
		refreshFlowCaches();
	}

	void configureTestEgress() {
		EgressActionConfiguration sampleEgress = new EgressActionConfiguration("SampleEgressAction", "type");

		EgressFlow sampleEgressFlow = buildRunningFlow(EGRESS_FLOW_NAME, sampleEgress, true);

		egressFlowRepo.save(sampleEgressFlow);
		egressFlowService.refreshCache();
	}

	void loadEgressConfig() {
		egressFlowRepo.deleteAll();

		EgressActionConfiguration sampleEgress = new EgressActionConfiguration("SampleEgressAction", "type");

		EgressFlow sampleEgressFlow = buildRunningFlow(EGRESS_FLOW_NAME, sampleEgress, false);

		EgressActionConfiguration errorEgress = new EgressActionConfiguration("ErrorEgressAction", "type");
		EgressFlow errorFlow = buildRunningFlow("error", errorEgress, false);

		egressFlowRepo.saveAll(List.of(sampleEgressFlow, errorFlow));
		egressFlowService.refreshCache();
	}

	void loadTimedIngressConfig() {
		timedIngressFlowRepo.deleteAll();
		timedIngressFlowRepo.save(buildTimedIngressFlow(FlowState.RUNNING));
		timedIngressFlowRepo.save(buildTimedIngressErrorFlow(FlowState.RUNNING));
		timedIngressFlowService.refreshCache();
	}

	@Test
	void contextLoads() {
		assertTrue(true);
		ConfigQueryInput input = ConfigQueryInput.newBuilder().configType(ConfigType.TRANSFORM_FLOW).build();
		assertFalse(transformFlowService.getConfigs(input).isEmpty());
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
		String id = getIdByPolicyName(AFTER_COMPLETE_POLICY);
		assertTrue(removeDeletePolicy(dgsQueryExecutor, id));
		assertEquals(2, deletePolicyRepo.count());
		assertFalse(removeDeletePolicy(dgsQueryExecutor, id));
	}

	@Test
	void testEnablePolicy() {
		replaceAllDeletePolicies(dgsQueryExecutor);
		assertTrue(enablePolicy(dgsQueryExecutor, getIdByPolicyName(OFFLINE_POLICY), true));
		assertTrue(enablePolicy(dgsQueryExecutor, getIdByPolicyName(AFTER_COMPLETE_POLICY), false));
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
				DiskSpaceDeletePolicy.builder()
						.id(idToUpdate)
						.name(DISK_SPACE_PERCENT_POLICY)
						.maxPercent(-1)
						.enabled(false)
						.build());
		checkUpdateResult(true, validationError, "maxPercent is invalid", idToUpdate, DISK_SPACE_PERCENT_POLICY, true);

		Result updateNameIsGood = updateDiskSpaceDeletePolicy(dgsQueryExecutor,
				DiskSpaceDeletePolicy.builder()
						.id(idToUpdate)
						.name("newName")
						.maxPercent(50)
						.enabled(false)
						.build());
		checkUpdateResult(true, updateNameIsGood, null, idToUpdate, "newName", false);

		Result notFoundError = updateDiskSpaceDeletePolicy(dgsQueryExecutor,
				DiskSpaceDeletePolicy.builder()
						.id("wrongId")
						.name("blah")
						.maxPercent(50)
						.enabled(true)
						.build());
		checkUpdateResult(true, notFoundError, "policy not found", idToUpdate, "newName", false);

		Result missingId = updateDiskSpaceDeletePolicy(dgsQueryExecutor,
				DiskSpaceDeletePolicy.builder()
						.name("blah")
						.maxPercent(50)
						.enabled(true)
						.build());
		checkUpdateResult(true, missingId, "id is missing", idToUpdate, "newName", false);

		addOnePolicy(dgsQueryExecutor);
		assertEquals(2, deletePolicyRepo.count());
		String secondId = getIdByPolicyName(DISK_SPACE_PERCENT_POLICY);
		Assertions.assertNotNull(secondId);
		assertNotEquals(secondId, idToUpdate);

		Result duplicateName = updateDiskSpaceDeletePolicy(dgsQueryExecutor,
				DiskSpaceDeletePolicy.builder()
						.id(idToUpdate)
						.name(DISK_SPACE_PERCENT_POLICY)
						.maxPercent(60)
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
				TimedDeletePolicy.builder()
						.id(idToUpdate)
						.name("blah")
						.afterComplete("ABC")
						.enabled(false)
						.build());
		checkUpdateResult(true, validationError, "Unable to parse duration for afterComplete", idToUpdate, DISK_SPACE_PERCENT_POLICY, true);

		Result notFoundError = updateTimedDeletePolicy(dgsQueryExecutor,
				TimedDeletePolicy.builder()
						.id("wrongId")
						.name("blah")
						.afterComplete("PT1H")
						.enabled(true)
						.build());
		checkUpdateResult(true, notFoundError, "policy not found", idToUpdate, DISK_SPACE_PERCENT_POLICY, true);

		Result goodUpdate = updateTimedDeletePolicy(dgsQueryExecutor,
				TimedDeletePolicy.builder()
						.id(idToUpdate)
						.name("newTypesAndName")
						.afterComplete("PT1H")
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
            assertInstanceOf(DiskSpaceDeletePolicy.class, policyList.get(0));
		} else {
            assertInstanceOf(TimedDeletePolicy.class, policyList.get(0));
		}
	}

	@Test
	void testGetDeletePolicies() {
		replaceAllDeletePolicies(dgsQueryExecutor);
		List<DeletePolicy> policyList = getDeletePolicies(dgsQueryExecutor);
		assertEquals(3, policyList.size());

		boolean foundAfterCompletePolicy = false;
		boolean foundOfflinePolicy = false;
		boolean foundDiskSpacePercent = false;
		Set<String> ids = new HashSet<>();

		for (DeletePolicy policy : policyList) {
			ids.add(policy.getId());
			if (policy instanceof DiskSpaceDeletePolicy diskPolicy) {
				if (diskPolicy.getName().equals(DISK_SPACE_PERCENT_POLICY)) {
					assertTrue(diskPolicy.isEnabled());
					foundDiskSpacePercent = true;
				}
			} else if (policy instanceof TimedDeletePolicy timedPolicy) {
				if (timedPolicy.getName().equals(AFTER_COMPLETE_POLICY)) {
					assertTrue(timedPolicy.isEnabled());
					assertEquals("PT2S", timedPolicy.getAfterComplete());
					assertNull(timedPolicy.getAfterCreate());
					assertNull(timedPolicy.getMinBytes());
					foundAfterCompletePolicy = true;

				} else if (timedPolicy.getName().equals(OFFLINE_POLICY)) {
					assertFalse(timedPolicy.isEnabled());
					assertEquals("PT2S", timedPolicy.getAfterCreate());
					assertNull(timedPolicy.getAfterComplete());
					assertEquals(1000, timedPolicy.getMinBytes());
					foundOfflinePolicy = true;
				}
			}
		}

		assertTrue(foundAfterCompletePolicy);
		assertTrue(foundOfflinePolicy);
		assertTrue(foundDiskSpacePercent);
		assertEquals(3, ids.size());
	}

	@Test
	void testDeleteRunnerPoliciesScheduled() {
		replaceAllDeletePolicies(dgsQueryExecutor);
		assertThat(deletePolicyRepo.count()).isEqualTo(3);
		List<DeletePolicyWorker> policiesScheduled = deleteRunner.refreshPolicies();
		assertThat(policiesScheduled).hasSize(2); // only 2 of 3 are enabled
		List<String> names = List.of(policiesScheduled.get(0).getName(),
				policiesScheduled.get(1).getName());
		assertTrue(names.containsAll(List.of(DISK_SPACE_PERCENT_POLICY, AFTER_COMPLETE_POLICY)));
	}

	@Test
	void deletePoliciesDontRaise() {
		deleteRunner.runDeletes();
	}

	@Test
	void testTransformUtf8() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postIngressDeltaFile(did));

		deltaFilesService.handleActionEvent(actionEvent("transformUtf8", did));

		verifyActionEventResults(postTransformUtf8DeltaFile(did), ActionContext.builder().flow(NORMALIZE_FLOW_NAME).name("sampleNormalize.SampleTransformAction").build());
		verifyCommonMetrics(ActionEventType.TRANSFORM, "Utf8TransformAction", NORMALIZE_FLOW_NAME, null, "type");
	}

	@SuppressWarnings("SameParameterValue")
	private void verifyCommonMetrics(ActionEventType actionEventType,
									 String actionName,
									 String ingressFlow,
									 String egressFlow,
									 String className) {
		Map<String, String> tags = tagsFor(actionEventType, actionName, ingressFlow, egressFlow);
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.FILES_IN, 1).addTags(tags));
		extendTagsForAction(tags, className);
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.ACTION_EXECUTION_TIME_MS, 1).addTags(tags));
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testTransform() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postTransformUtf8DeltaFile(did));

		deltaFilesService.handleActionEvent(actionEvent("transform", did));

		verifyActionEventResults(postTransformDeltaFile(did), ActionContext.builder().flow(NORMALIZE_FLOW_NAME).name("sampleNormalize.SampleLoadAction").build());

		DeltaFile deltaFile = deltaFileRepo.findById(did).orElseThrow();
		// check that the deleted metadata key worked
		Map<String, String> expectedMetadata = new HashMap<>(SOURCE_METADATA);
		expectedMetadata.putAll(TRANSFORM_METADATA);
		assertEquals(expectedMetadata, deltaFile.getMetadata());

		verifyCommonMetrics(ActionEventType.TRANSFORM, "SampleTransformAction", NORMALIZE_FLOW_NAME, null, "type");
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

		DeltaFile expected = postResumeTransformDeltaFile(did);
		verifyActionEventResults(expected, ActionContext.builder().flow(NORMALIZE_FLOW_NAME).name("sampleNormalize.SampleTransformAction").build());

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		deltaFile.errorAction(NORMALIZE_FLOW_NAME, "SampleTransformAction", OffsetDateTime.now(), OffsetDateTime.now(), "cause", "context");
		deltaFileRepo.save(deltaFile);

		List<PerActionUniqueKeyValues> errorMetadataUnion = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				"query getMetadata { errorMetadataUnion (dids: [\"" + did + "\"]) { action keyVals { key values } } }",
				"data." + DgsConstants.QUERY.ErrorMetadataUnion,
				new TypeRef<>() {});

		assertEquals(1, errorMetadataUnion.size());
		assertEquals("SampleTransformAction", errorMetadataUnion.get(0).getAction());
		assertEquals(List.of("AuthorizedBy", "anotherKey", "deleteMe"), errorMetadataUnion.get(0).getKeyVals().stream().map(UniqueKeyValues::getKey).sorted().toList());

		Mockito.verifyNoInteractions(metricService);
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

		Mockito.verifyNoInteractions(metricService);
	}

	// TODO - test this from a transform
	/*
	@Test
	void testLoadToColdQueue() throws IOException {
		String did = UUID.randomUUID().toString();
		DeltaFile postTransform = postTransformDeltaFile(did);
		deltaFileRepo.save(postTransform);
		queueManagementService.getColdQueues().put("SampleDomainType", 999999L);

		deltaFilesService.handleActionEvent(actionEvent("load", did));

		DeltaFile postLoadColdQueued = postLoadDeltaFile(did);
		postLoadColdQueued.getActions().get(postLoadColdQueued.getActions().size() - 1).setState(ActionState.COLD_QUEUED);
		verifyActionEventResults(postLoadColdQueued, ActionContext.builder().flow("sampleEnrich").name("sampleEnrich.SampleDomainAction").build());

		verifyCommonMetrics(ActionEventType.LOAD, "SampleLoadAction", NORMALIZE_FLOW_NAME, null, "type");
	}*/

	@Test
	void testColdRequeue() {
		String did = UUID.randomUUID().toString();
		DeltaFile postTransform = postTransformDeltaFile(did);
		postTransform.getActions().get(postTransform.getActions().size() - 1).setState(ActionState.COLD_QUEUED);
		deltaFileRepo.save(postTransform);

		queueManagementService.coldToWarm();

		verifyActionEventResults(postTransformDeltaFile(did), ActionContext.builder().flow(NORMALIZE_FLOW_NAME).name("sampleNormalize.SampleLoadAction").build());
	}

	// TODO - turn this into testing a split from a transform
	/*@Test
	void testReinject() throws IOException {
		String did = UUID.randomUUID().toString();
		DeltaFile postTransform = postTransformDeltaFile(did);
		deltaFileRepo.save(postTransform);

		deltaFilesService.handleActionEvent(actionEvent("reinject", did));

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertEquals(DeltaFileStage.COMPLETE, deltaFile.getStage());
		assertEquals(2, deltaFile.getChildDids().size());
		assertEquals(ActionState.REINJECTED, deltaFile.getActions().get(deltaFile.getActions().size() - 1).getState());

		List<DeltaFile> children = deltaFilesService.deltaFiles(0, 50, DeltaFilesFilter.newBuilder().dids(deltaFile.getChildDids()).build(), DeltaFileOrder.newBuilder().field("created").direction(DeltaFileDirection.ASC).build()).getDeltaFiles();
		assertEquals(2, children.size());

		DeltaFile child1 = children.get(0);
		assertEquals(DeltaFileStage.INGRESS, child1.getStage());
		assertEquals(Collections.singletonList(deltaFile.getDid()), child1.getParentDids());
		assertEquals("file1", child1.getSourceInfo().getFilename());
		assertEquals(0, child1.lastContent().get(0).getSegments().get(0).getOffset());
		assertEquals(2, child1.lastContent().size());

		DeltaFile child2 = children.get(1);
		assertEquals(DeltaFileStage.INGRESS, child2.getStage());
		assertEquals(Collections.singletonList(deltaFile.getDid()), child2.getParentDids());
		assertEquals("file2", child2.getSourceInfo().getFilename());
		assertEquals(250, child2.lastContent().get(0).getSegments().get(0).getOffset());
		assertEquals(1, child2.lastContent().size());

		Mockito.verify(actionEventQueue).putActions(actionInputListCaptor.capture(), anyBoolean());
		List<ActionInput> actionInputs = actionInputListCaptor.getValue();
		assertThat(actionInputs).hasSize(2);

		assertEquals(child1.forQueue(NORMALIZE_FLOW_NAME), actionInputs.get(0).getDeltaFileMessages().get(0));
		assertEquals(child2.forQueue(NORMALIZE_FLOW_NAME), actionInputs.get(1).getDeltaFileMessages().get(0));

		verifyCommonMetrics(ActionEventType.REINJECT, "SampleLoadAction", NORMALIZE_FLOW_NAME, null, "type");
	}*/

	// TODO - rewrite these with a transform action
	/*
	@Test
	void testEnrichWithUnicodeAnnotation() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postDomainDeltaFile(did));

		deltaFilesService.handleActionEvent(actionEvent("enrichUnicode", did));

		verifyActionEventResults(postEnrichDeltaFileWithUnicodeAnnotation(did), ActionContext.builder().flow("sampleEgress").name("sampleEgress.SampleFormatAction").build());
		verifyCommonMetrics(ActionEventType.ENRICH, "SampleEnrichAction", NORMALIZE_FLOW_NAME, null, "type");
	}

	@Test
	void testEnrichDidHasUnicode() {
		String did = "ĂȂȃЄ";
		deltaFileRepo.save(postDomainDeltaFile(did));

		org.assertj.core.api.Assertions.assertThatThrownBy(
						() -> deltaFilesService.handleActionEvent(actionEvent("enrichUnicodeDid")))
				.isInstanceOf(InvalidActionEventException.class)
				.hasMessageContaining("Invalid ActionEvent: DeltaFile ĂȂȃЄ not found");
	}

	@Test
	void testEnrichDidNotFound() {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postDomainDeltaFile(did));

		org.assertj.core.api.Assertions.assertThatThrownBy(
						() -> deltaFilesService.handleActionEvent(actionEvent("enrichDidNotFound", did)))
				.isInstanceOf(InvalidActionEventException.class)
				.hasMessageContaining("Invalid ActionEvent: DeltaFile xxx");
	}

	@Test
	void testEnrichMissingAction() {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postDomainDeltaFile(did));

		org.assertj.core.api.Assertions.assertThatThrownBy(
						() -> deltaFilesService.handleActionEvent(actionEvent("enrichMissingAction", did)))
				.isInstanceOf(InvalidActionEventException.class)
				.hasMessageContaining("Invalid ActionEvent: Missing action:");
	}

	@Test
	void testEnrichMissingDid() {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postDomainDeltaFile(did));

		org.assertj.core.api.Assertions.assertThatThrownBy(
						() -> deltaFilesService.handleActionEvent(actionEvent("enrichMissingDid", did)))
				.isInstanceOf(InvalidActionEventException.class)
				.hasMessageContaining("Invalid ActionEvent: Missing did:");
	}

	@Test
	void testEnrichWrongElement() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postDomainDeltaFile(did));

		deltaFilesService.handleActionEvent(actionEvent("enrichWrongElement", did));

		DeltaFile afterMutation = deltaFilesService.getDeltaFile(did);
		assertEqualsIgnoringDates(postEnrichInvalidDeltaFile(did), afterMutation);
	}*/

	void runErrorWithAutoResume(Integer autoResumeDelay, boolean withAnnotation) throws IOException {
		String did = UUID.randomUUID().toString();
		String policyName = null;
		DeltaFile original = postTransformDeltaFile(did);
		deltaFileRepo.save(original);

		if (autoResumeDelay != null) {
			BackOff backOff = BackOff.newBuilder()
					.delay(autoResumeDelay)
					.build();

			policyName = "policyName";
			ResumePolicy resumePolicy = new ResumePolicy();
			resumePolicy.setName(policyName);
			resumePolicy.setFlow(original.getSourceInfo().getFlow());
			resumePolicy.setMaxAttempts(2);
			resumePolicy.setBackOff(backOff);
			Result result = resumePolicyService.save(resumePolicy);
			assertTrue(result.getErrors().isEmpty());
		}

		if (withAnnotation) {
			deltaFilesService.handleActionEvent(actionEvent("errorWithAnnotation", did));
		} else {
			deltaFilesService.handleActionEvent(actionEvent("error", did));
		}

		DeltaFile actual = deltaFilesService.getDeltaFile(did);
		DeltaFile expected = postErrorDeltaFile(did, policyName, autoResumeDelay);
		if (withAnnotation) {
			expected.addAnnotations(Map.of("errorKey", "error metadata"));
		}
		assertEqualsIgnoringDates(expected, actual);

		Map<String, String> tags = tagsFor(ActionEventType.ERROR, "AuthorityValidateAction", NORMALIZE_FLOW_NAME, EGRESS_FLOW_NAME);
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.FILES_IN, 1).addTags(tags));
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.FILES_ERRORED, 1).addTags(tags));

		extendTagsForAction(tags, "type");
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.ACTION_EXECUTION_TIME_MS, 1).addTags(tags));
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testError() throws IOException {
		runErrorWithAutoResume(null, false);
	}

	@Test
	void testErrorWithAnnotation() throws IOException {
		runErrorWithAutoResume(null, true);
	}

	@Test
	void testAutoResume() throws IOException {
		runErrorWithAutoResume(100, false);
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

		verifyActionEventResults(postResumeDeltaFile(did, "sampleEgress", "AuthorityValidateAction", ActionType.TRANSFORM),
				ActionContext.builder().flow("sampleEgress").name("sampleEgress.AuthorityValidateAction").build());

		Mockito.verifyNoInteractions(metricService);
	}

	@Test
	void testResumeClearsAcknowledged() throws IOException {
		String did = UUID.randomUUID().toString();
		DeltaFile postErrorDeltaFile = postErrorDeltaFile(did);
		postErrorDeltaFile.acknowledgeError(OffsetDateTime.now(), "some reason");
		deltaFileRepo.save(postErrorDeltaFile);

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("resume"), did),
				"data." + DgsConstants.MUTATION.Resume,
				new TypeRef<>() {});

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertNull(deltaFile.getErrorAcknowledged());
		assertNull(deltaFile.getErrorAcknowledgedReason());
		Mockito.verifyNoInteractions(metricService);
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
		Mockito.verifyNoInteractions(metricService);
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
		Mockito.verifyNoInteractions(metricService);
	}

	@Captor
	private ArgumentCaptor<Metric> metricCaptor;

	@Test
	void testEgress() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postTransformDeltaFile(did));

		deltaFilesService.handleActionEvent(actionEvent("egress", did));

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertEqualsIgnoringDates(postEgressDeltaFile(did), deltaFile);

		Mockito.verify(actionEventQueue, never()).putActions(any(), anyBoolean());
		Map<String, String> tags = tagsFor(ActionEventType.EGRESS, "SampleEgressAction", NORMALIZE_FLOW_NAME, EGRESS_FLOW_NAME);
		Map<String, String> classTags = tagsFor(ActionEventType.EGRESS, "SampleEgressAction", NORMALIZE_FLOW_NAME, EGRESS_FLOW_NAME);
		classTags.put(DeltaFiConstants.CLASS, "type");

		Mockito.verify(metricService, Mockito.atLeast(5)).increment(metricCaptor.capture());
		List<Metric> metrics = metricCaptor.getAllValues();
		MatcherAssert.assertThat(
				metrics.stream().map(Metric::getName).collect(Collectors.toList()),
				Matchers.containsInAnyOrder(
						DeltaFiConstants.FILES_IN,
						DeltaFiConstants.FILES_OUT,
						DeltaFiConstants.BYTES_OUT,
						DeltaFiConstants.EXECUTION_TIME_MS,
						DeltaFiConstants.ACTION_EXECUTION_TIME_MS
				));
		for (Metric metric : metrics) {
			switch (metric.getName()) {
				case DeltaFiConstants.FILES_IN -> assertEquals(new Metric(DeltaFiConstants.FILES_IN, 1).addTags(tags), metric);
				case DeltaFiConstants.FILES_OUT -> assertEquals(new Metric(DeltaFiConstants.FILES_OUT, 1).addTag("destination", "final").addTags(tags), metric);
				case DeltaFiConstants.BYTES_OUT -> assertEquals(new Metric(DeltaFiConstants.BYTES_OUT, 42).addTag("destination", "final").addTags(tags), metric);
				case DeltaFiConstants.EXECUTION_TIME_MS ->
					// Dont care about value...
						assertEquals(new Metric(DeltaFiConstants.EXECUTION_TIME_MS, metric.getValue()).addTags(tags), metric);
				case DeltaFiConstants.ACTION_EXECUTION_TIME_MS ->
					// Dont care about value...
						assertEquals(new Metric(DeltaFiConstants.ACTION_EXECUTION_TIME_MS, metric.getValue()).addTags(classTags), metric);
			}
		}
	}

	@Test
	void testToEgressWithTestModeEgress() throws IOException {
		configureTestEgress();

		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postTransformDeltaFile(did));

		deltaFilesService.handleActionEvent(actionEvent("validateAuthority", did));

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);

		assertEqualsIgnoringDates(
				postTransformDeltaFileInTestMode(did, "sampleEgress", "SyntheticEgressActionForTestEgress"),
				deltaFile
		);
		MatcherAssert.assertThat(deltaFile.getTestModeReason(), containsString(EGRESS_FLOW_NAME));
		Mockito.verify(actionEventQueue, never()).putActions(any(), anyBoolean());
		verifyCommonMetrics(ActionEventType.TRANSFORM, "SampleTransformAction", NORMALIZE_FLOW_NAME, EGRESS_FLOW_NAME, "type");
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
		expected.getActions().get(0).setMetadata(Map.of("AuthorizedBy", "ABC", "sourceInfo.filename.original", "input.txt", "sourceInfo.flow.original", NORMALIZE_FLOW_NAME, "removeMe.original", "whatever", "AuthorizedBy.original", "XYZ", "anotherKey", "anotherValue"));
		expected.getActions().get(1).setFlow("theFlow");
		expected.getActions().get(1).setName("SampleLoadAction");
		expected.getSourceInfo().setFilename("newFilename");
		expected.getSourceInfo().setFlow("theFlow");
		expected.getSourceInfo().setMetadata(Map.of("AuthorizedBy", "ABC", "sourceInfo.filename.original", "input.txt", "sourceInfo.flow.original", NORMALIZE_FLOW_NAME, "removeMe.original", "whatever", "AuthorizedBy.original", "XYZ", "anotherKey", "anotherValue"));
		verifyActionEventResults(expected, ActionContext.builder().flow("theFlow").name("theFlow.SampleLoadAction").build());

		List<RetryResult> secondResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("replay"), did),
				"data." + DgsConstants.MUTATION.Replay,
				new TypeRef<>() {});

		assertEquals(1, secondResults.size());
		assertFalse(secondResults.get(0).getSuccess());
		Mockito.verifyNoInteractions(metricService);
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

		Mockito.verifyNoInteractions(metricService);
	}

	@Test
	void testSourceMetadataUnion() throws IOException {
		DeltaFile deltaFile1 = buildDeltaFile("did1", List.of(), Map.of("key", "val1"));
		DeltaFile deltaFile2 = buildDeltaFile("did2", List.of(), Map.of("key", "val2"));
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
		verifyFiltered(postIngressDeltaFile(did), TRANSFORM_FLOW_NAME, "Utf8TransformAction", true);
	}

	@Test
	void testFilterEgress() throws IOException {
		String did = UUID.randomUUID().toString();
		verifyFiltered(postTransformDeltaFile(did), "sampleEgress", "SampleEgressAction", true);
	}

	@SuppressWarnings("SameParameterValue")
	private void verifyFiltered(DeltaFile deltaFile, String flow, String filteredAction, boolean filteringAllowed) throws IOException {
		deltaFileRepo.save(deltaFile);

		deltaFilesService.handleActionEvent(filterActionEvent(deltaFile.getDid(), flow, filteredAction));

		DeltaFile actual = deltaFilesService.getDeltaFile(deltaFile.getDid());
		Action action = actual.getActions().stream().filter(a -> a.getName().equals(filteredAction)).findFirst().orElse(null);
		assert action != null;

		if (filteringAllowed) {
			assertEquals(ActionState.FILTERED, action.getState());
			assertEquals(DeltaFileStage.COMPLETE, actual.getStage());
			assertTrue(actual.getFiltered());
			assertEquals("you got filtered", action.getFilteredCause());
			assertEquals("here is why: blah", action.getFilteredContext());
      assertEquals("filter metadata", actual.getAnnotations().get("filterKey"));

			Mockito.verify(actionEventQueue, never()).putActions(any(), anyBoolean());
		} else {
			assertEquals(ActionState.ERROR, action.getState());
			assertEquals(DeltaFileStage.ERROR, actual.getStage());
			assertFalse(actual.getFiltered());
		}
	}

	@Test
	void setDeltaFileTtl() {
		assertEquals(Duration.ofDays(13), deltaFileRepo.getTtlExpiration());
	}

	@Test
	void findConfigsTest() {
		String name = "SampleTransformAction";

		ConfigQueryInput configQueryInput = ConfigQueryInput.newBuilder().configType(ConfigType.TRANSFORM_ACTION).name(name).build();

		DeltaFiConfigsProjectionRoot projection = new DeltaFiConfigsProjectionRoot()
				.name()
				.apiVersion()
				.onTransformActionConfiguration()
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

        assertInstanceOf(TransformActionConfiguration.class, configs.get(0));

		TransformActionConfiguration transformActionConfiguration = (TransformActionConfiguration) configs.get(0);
		assertEquals(name, transformActionConfiguration.getName());
	}

	@Test
	void testGetTransformFlowPlan() {
		clearForFlowTests();
		TransformFlowPlan transformFlowPlanA = new TransformFlowPlan("transformPlan", "description");
		transformFlowPlanA.setEgressAction(new EgressActionConfiguration("egress", "type"));
		TransformFlowPlan transformFlowPlanB = new TransformFlowPlan("b", "description");
		transformFlowPlanB.setEgressAction(new EgressActionConfiguration("egress", "type"));
		transformFlowPlanRepo.saveAll(List.of(transformFlowPlanA, transformFlowPlanB));
		TransformFlowPlan plan = FlowPlanDatafetcherTestHelper.getTransformFlowPlan(dgsQueryExecutor);
		assertThat(plan.getName()).isEqualTo("transformPlan");
	}

	@Test
	void testGetEgressFlowPlan() {
		clearForFlowTests();
		EgressFlowPlan egressFlowPlanA = new EgressFlowPlan("egressPlan", "description", new EgressActionConfiguration("egress", "type"));
		EgressFlowPlan egressFlowPlanB = new EgressFlowPlan("b", "description", new EgressActionConfiguration("egress", "type"));
		egressFlowPlanRepo.saveAll(List.of(egressFlowPlanA, egressFlowPlanB));
		EgressFlowPlan plan = FlowPlanDatafetcherTestHelper.getEgressFlowPlan(dgsQueryExecutor);
		assertThat(plan.getName()).isEqualTo("egressPlan");
	}

	@Test
	void testGetTimedIngressFlowPlan() {
		clearForFlowTests();
		TimedIngressFlowPlan timedIngressFlowPlanA = new TimedIngressFlowPlan("timedIngressPlan", FlowType.TIMED_INGRESS,
				"description", new TimedIngressActionConfiguration("timedIngress", "type"), "flow", null, "*/5 * * * * *");
		TimedIngressFlowPlan timedIngressFlowPlanB = new TimedIngressFlowPlan("b", FlowType.TIMED_INGRESS, "description",
				new TimedIngressActionConfiguration("timedIngress", "type"), "flow", null, "*/5 * * * * *");
		timedIngressFlowPlanRepo.saveAll(List.of(timedIngressFlowPlanA, timedIngressFlowPlanB));
		TimedIngressFlowPlan plan = FlowPlanDatafetcherTestHelper.getTimedIngressFlowPlan(dgsQueryExecutor);
		assertThat(plan.getName()).isEqualTo("timedIngressPlan");
	}

	@Test
	void testValidateTransformFlow() {
		clearForFlowTests();
		transformFlowRepo.save(buildTransformFlow(FlowState.STOPPED));
		TransformFlow transformFlow = FlowPlanDatafetcherTestHelper.validateTransformFlow(dgsQueryExecutor);
		assertThat(transformFlow.getFlowStatus()).isNotNull();
	}

	@Test
	void testValidateTimedIngressFlow() {
		clearForFlowTests();
		timedIngressFlowRepo.save(buildTimedIngressFlow(FlowState.STOPPED));
		TimedIngressFlow timedIngressFlow = FlowPlanDatafetcherTestHelper.validateTimedIngressFlow(dgsQueryExecutor);
		assertThat(timedIngressFlow.getFlowStatus()).isNotNull();
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
		Variable var = Variable.builder().name("var").description("description").defaultValue("value").required(false).build();
		PluginVariables variables = new PluginVariables();
		variables.setSourcePlugin(pluginCoordinates);
		variables.setVariables(List.of(var));

		TransformFlow transformFlow = new TransformFlow();
		transformFlow.setName("transform");
		transformFlow.setSourcePlugin(pluginCoordinates);

		EgressFlow egressFlow = new EgressFlow();
		egressFlow.setName("egress");
		egressFlow.setSourcePlugin(pluginCoordinates);

		TimedIngressFlow timedIngressFlow = new TimedIngressFlow();
		timedIngressFlow.setName("timedIngress");
		timedIngressFlow.setSourcePlugin(pluginCoordinates);

		Plugin plugin = new Plugin();
		plugin.setPluginCoordinates(pluginCoordinates);
		pluginRepository.save(plugin);
		pluginVariableRepo.save(variables);
		transformFlowRepo.save(transformFlow);
		egressFlowRepo.save(egressFlow);
		timedIngressFlowRepo.save(timedIngressFlow);
		refreshFlowCaches();

		List<Flows> flows = FlowPlanDatafetcherTestHelper.getFlows(dgsQueryExecutor);
		assertThat(flows).hasSize(1);
		Flows pluginFlows = flows.get(0);
		assertThat(pluginFlows.getSourcePlugin().getArtifactId()).isEqualTo("test-actions");
		assertThat(pluginFlows.getTransformFlows().get(0).getName()).isEqualTo("transform");
		assertThat(pluginFlows.getEgressFlows().get(0).getName()).isEqualTo("egress");
		assertThat(pluginFlows.getTimedIngressFlows().get(0).getName()).isEqualTo("timedIngress");
	}

	@Test
	void testGetFlowsByState() {
		clearForFlowTests();
		TransformFlow stoppedFlow = buildTransformFlow(FlowState.STOPPED);
		stoppedFlow.setName("stopped");

		TransformFlow invalidFlow = buildTransformFlow(FlowState.INVALID);
		invalidFlow.setName("invalid");

		TransformFlow runningFlow = buildTransformFlow(FlowState.RUNNING);
		runningFlow.setName("running");

		transformFlowRepo.saveAll(List.of(stoppedFlow, invalidFlow, runningFlow));
		refreshFlowCaches();

		assertThat(transformFlowService.getFlowNamesByState(null)).hasSize(3).contains("stopped", "invalid", "running");
		assertThat(transformFlowService.getFlowNamesByState(FlowState.STOPPED)).hasSize(1).contains("stopped");
		assertThat(transformFlowService.getFlowNamesByState(FlowState.INVALID)).hasSize(1).contains("invalid");
		assertThat(transformFlowService.getFlowNamesByState(FlowState.RUNNING)).hasSize(1).contains("running");
	}

	@Test
	void testGetFlowsQuery() {
		clearForFlowTests();

		timedIngressFlowRepo.save(buildTimedIngressFlow(FlowState.STOPPED));
		transformFlowRepo.save(buildTransformFlow(FlowState.STOPPED));
		egressFlowRepo.save(buildEgressFlow(FlowState.STOPPED));
		refreshFlowCaches();

		FlowNames flows = FlowPlanDatafetcherTestHelper.getFlowNames(dgsQueryExecutor);
		assertThat(flows.getTimedIngress()).hasSize(1).contains(TIMED_INGRESS_FLOW_NAME);
		assertThat(flows.getTransform()).hasSize(1).contains(TRANSFORM_FLOW_NAME);
		assertThat(flows.getEgress()).hasSize(1).contains(EGRESS_FLOW_NAME);
	}

	@Test
	void getRunningFlows() {
		clearForFlowTests();

		transformFlowRepo.save(buildTransformFlow(FlowState.STOPPED));
		assertTrue(FlowPlanDatafetcherTestHelper.startTransformFlow(dgsQueryExecutor));

		egressFlowRepo.save(buildEgressFlow(FlowState.STOPPED));
		assertTrue(FlowPlanDatafetcherTestHelper.startEgressFlow(dgsQueryExecutor));

		SystemFlows flows = FlowPlanDatafetcherTestHelper.getRunningFlows(dgsQueryExecutor);
		assertThat(flows.getTransform()).hasSize(1).matches(transformFlows -> TRANSFORM_FLOW_NAME.equals(transformFlows.get(0).getName()));
		assertThat(flows.getEgress()).hasSize(1).matches(egressFlows -> EGRESS_FLOW_NAME.equals(egressFlows.get(0).getName()));

		assertTrue(FlowPlanDatafetcherTestHelper.stopEgressFlow(dgsQueryExecutor));
		SystemFlows updatedFlows = FlowPlanDatafetcherTestHelper.getRunningFlows(dgsQueryExecutor);
		assertThat(updatedFlows.getTransform()).hasSize(1);
		assertThat(updatedFlows.getEgress()).isEmpty();
	}

	@Test
	void getAllFlows() {
		clearForFlowTests();

		TransformFlow transformFlow = new TransformFlow();
		transformFlow.setName(TRANSFORM_FLOW_NAME);

		TimedIngressFlow timedIngressFlow = new TimedIngressFlow();
		timedIngressFlow.setName(TIMED_INGRESS_FLOW_NAME);

		EgressFlow egressFlow = new EgressFlow();
		egressFlow.setName(EGRESS_FLOW_NAME);

		transformFlowRepo.save(transformFlow);
		timedIngressFlowRepo.save(timedIngressFlow);
		egressFlowRepo.save(egressFlow);
		refreshFlowCaches();

		SystemFlows flows = FlowPlanDatafetcherTestHelper.getAllFlows(dgsQueryExecutor);
		assertThat(flows.getTransform()).hasSize(1).matches(transformFlows -> TRANSFORM_FLOW_NAME.equals(transformFlows.get(0).getName()));
		assertThat(flows.getTimedIngress()).hasSize(1).matches(normalizeFlows -> NORMALIZE_FLOW_NAME.equals(normalizeFlows.get(0).getName()));
		assertThat(flows.getEgress()).hasSize(1).matches(egressFlows -> EGRESS_FLOW_NAME.equals(egressFlows.get(0).getName()));
	}

	@Test
	void getTransformFlow() {
		clearForFlowTests();
		TransformFlow transformFlow = new TransformFlow();
		transformFlow.setName(TRANSFORM_FLOW_NAME);
		transformFlowRepo.save(transformFlow);

		TransformFlow foundFlow = FlowPlanDatafetcherTestHelper.getTransformFlow(dgsQueryExecutor);
		assertThat(foundFlow).isNotNull();
		assertThat(foundFlow.getName()).isEqualTo(TRANSFORM_FLOW_NAME);
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
	void getActionNamesByFamily() {
		clearForFlowTests();

		transformFlowRepo.save(buildTransformFlow(FlowState.STOPPED));
		egressFlowRepo.save(buildEgressFlow(FlowState.STOPPED));
		timedIngressFlowRepo.save(buildTimedIngressFlow(FlowState.STOPPED));
		refreshFlowCaches();

		List<ActionFamily> actionFamilies = FlowPlanDatafetcherTestHelper.getActionFamilies(dgsQueryExecutor);
		assertThat(actionFamilies).hasSize(9);

		assertThat(getActionNames(actionFamilies, "INGRESS")).hasSize(1).contains(INGRESS_ACTION);
		assertThat(getActionNames(actionFamilies, "TRANSFORM")).hasSize(2).contains("Utf8TransformAction", "SampleTransformAction");
		assertThat(getActionNames(actionFamilies, "EGRESS")).hasSize(1).contains("SampleEgressAction");
		assertThat(getActionNames(actionFamilies, "TIMED_INGRESS")).hasSize(1).contains("SampleTimedIngressAction");
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

	@Test
	void testSaveTransformFlowPlan() {
		clearForFlowTests();
		TransformFlow transformFlow = FlowPlanDatafetcherTestHelper.saveTransformFlowPlan(dgsQueryExecutor);
		assertThat(transformFlow).isNotNull();
	}

	@Test
	void testSaveEgressFlowPlan() {
		clearForFlowTests();
		EgressFlow egressFlow = FlowPlanDatafetcherTestHelper.saveEgressFlowPlan(dgsQueryExecutor);
		assertThat(egressFlow).isNotNull();
	}

	@Test
	void testSaveTimedIngressFlowPlan() {
		clearForFlowTests();
		TimedIngressFlow timedIngressFlow = FlowPlanDatafetcherTestHelper.saveTimedIngressFlowPlan(dgsQueryExecutor);
		assertThat(timedIngressFlow).isNotNull();
	}

	@Test
	void testRemoveTransformFlowPlan() {
		clearForFlowTests();
		TransformFlowPlan transformFlowPlan = new TransformFlowPlan("flowPlan", null, null);
		transformFlowPlanRepo.save(transformFlowPlan);
		assertThatThrownBy(() -> FlowPlanDatafetcherTestHelper.removeTransformFlowPlan(dgsQueryExecutor))
				.isInstanceOf(QueryException.class)
				.hasMessageContaining("Flow plan flowPlan is not a system-plugin flow plan and cannot be removed");

		transformFlowPlan.setSourcePlugin(systemPluginService.getSystemPluginCoordinates());
		transformFlowPlanRepo.save(transformFlowPlan);
		assertTrue(FlowPlanDatafetcherTestHelper.removeTransformFlowPlan(dgsQueryExecutor));
	}

	@Test
	void testRemoveEgressFlowPlan() {
		clearForFlowTests();
		EgressFlowPlan egressFlowPlan = new EgressFlowPlan("flowPlan", null, null, null);
		egressFlowPlanRepo.save(egressFlowPlan);
		assertThatThrownBy(() -> FlowPlanDatafetcherTestHelper.removeEgressFlowPlan(dgsQueryExecutor))
				.isInstanceOf(QueryException.class)
				.hasMessageContaining("Flow plan flowPlan is not a system-plugin flow plan and cannot be removed");

		egressFlowPlan.setSourcePlugin(systemPluginService.getSystemPluginCoordinates());
		egressFlowPlanRepo.save(egressFlowPlan);
		assertTrue(FlowPlanDatafetcherTestHelper.removeEgressFlowPlan(dgsQueryExecutor));
	}

	@Test
	void testRemoveTimedIngressFlowPlan() {
		clearForFlowTests();
		TimedIngressFlowPlan timedIngressFlowPlan = new TimedIngressFlowPlan("flowPlan", FlowType.TIMED_INGRESS, null,
				null, null, null, null);
		timedIngressFlowPlanRepo.save(timedIngressFlowPlan);
		assertThatThrownBy(() -> FlowPlanDatafetcherTestHelper.removeTimedIngressFlowPlan(dgsQueryExecutor))
				.isInstanceOf(QueryException.class)
				.hasMessageContaining("Flow plan flowPlan is not a system-plugin flow plan and cannot be removed");

		timedIngressFlowPlan.setSourcePlugin(systemPluginService.getSystemPluginCoordinates());
		timedIngressFlowPlanRepo.save(timedIngressFlowPlan);
		assertTrue(FlowPlanDatafetcherTestHelper.removeTimedIngressFlowPlan(dgsQueryExecutor));
	}

	@Test
	void testRemovePluginVariables() {
		clearForFlowTests();
		PluginCoordinates system = systemPluginService.getSystemPluginCoordinates();
		List<Variable> variables = List.of(Variable.builder().name("a").dataType(VariableDataType.STRING).value("test").build());
		pluginVariableService.saveVariables(system, variables);
		assertTrue(FlowPlanDatafetcherTestHelper.removePluginVariables(dgsQueryExecutor));
		assertThat(pluginVariableService.getVariablesByPlugin(system)).isEmpty();
	}

	@Test
	void testStartTransformFlow() {
		clearForFlowTests();
		transformFlowRepo.save(buildTransformFlow(FlowState.STOPPED));
		assertTrue(FlowPlanDatafetcherTestHelper.startTransformFlow(dgsQueryExecutor));
	}

	@Test
	void testStopTransformFlow() {
		clearForFlowTests();
		transformFlowRepo.save(buildTransformFlow(FlowState.RUNNING));
		assertTrue(FlowPlanDatafetcherTestHelper.stopTransformFlow(dgsQueryExecutor));
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
	void testStartTimedIngressFlow() {
		clearForFlowTests();
		timedIngressFlowRepo.save(buildTimedIngressFlow(FlowState.STOPPED));
		assertTrue(FlowPlanDatafetcherTestHelper.startTimedIngressFlow(dgsQueryExecutor));
	}

	@Test
	void testStopTimedIngressFlow() {
		clearForFlowTests();
		timedIngressFlowRepo.save(buildTimedIngressFlow(FlowState.RUNNING));
		assertTrue(FlowPlanDatafetcherTestHelper.stopTimedIngressFlow(dgsQueryExecutor));
	}

	@Test
	void testSetMemoTimedIngressWhenStopped() {
		clearForFlowTests();
		timedIngressFlowRepo.save(buildTimedIngressFlow(FlowState.STOPPED));
		assertFalse(FlowPlanDatafetcherTestHelper.setTimedIngressMemo(dgsQueryExecutor, null));
		assertTrue(FlowPlanDatafetcherTestHelper.setTimedIngressMemo(dgsQueryExecutor, "100"));
	}

	@Test
	void testSetMemoTimedIngressWhenRunning() {
		clearForFlowTests();
		timedIngressFlowRepo.save(buildTimedIngressFlow(FlowState.RUNNING));
		assertFalse(FlowPlanDatafetcherTestHelper.setTimedIngressMemo(dgsQueryExecutor, "100"));
	}

	@Test
	void testSavePluginVariables() {
		assertTrue(FlowPlanDatafetcherTestHelper.savePluginVariables(dgsQueryExecutor));
		PluginVariables variables = pluginVariableRepo.findById(systemPluginService.getSystemPluginCoordinates()).orElse(null);
		assertThat(variables).isNotNull();
		assertThat(variables.getVariables()).hasSize(1).anyMatch(v -> v.getName().equals("var"));
	}

	@Test
	void testSetPluginVariableValues() {
		PluginVariables variables = new PluginVariables();
		variables.setSourcePlugin(FlowPlanDatafetcherTestHelper.PLUGIN_COORDINATES);
		variables.setVariables(List.of(Variable.builder().name("key").value("test").description("description").dataType(VariableDataType.STRING).build()));
		pluginVariableRepo.save(variables);
		assertTrue(FlowPlanDatafetcherTestHelper.setPluginVariableValues(dgsQueryExecutor));
	}

	@Test
	void testResumePolicyDatafetcher() {
		List<Result> results = ResumePolicyDatafetcherTestHelper.loadResumePolicyWithDuplicate(dgsQueryExecutor);
		assertTrue(results.get(0).isSuccess());
		assertFalse(results.get(1).isSuccess());
		assertTrue(results.get(1).getErrors().contains("duplicate name or criteria"));
		assertTrue(results.get(2).getErrors().contains("duplicate name or criteria"));
		assertTrue(results.get(3).isSuccess());
		assertEquals(2, resumePolicyRepo.count());

		List<ResumePolicy> policies = ResumePolicyDatafetcherTestHelper.getAllResumePolicies(dgsQueryExecutor);
		assertEquals(2, policies.size());
		String idToUse;
		// Result are not ordered explicitly
		if (ResumePolicyDatafetcherTestHelper.isDefaultFlow(policies.get(0))) {
			idToUse = policies.get(0).getId();
			assertTrue(ResumePolicyDatafetcherTestHelper.matchesDefault(policies.get(0)));
		} else {
			idToUse = policies.get(1).getId();
			assertTrue(ResumePolicyDatafetcherTestHelper.matchesDefault(policies.get(1)));
		}

		ResumePolicy policy = ResumePolicyDatafetcherTestHelper.getResumePolicy(dgsQueryExecutor, idToUse);
		assertTrue(ResumePolicyDatafetcherTestHelper.matchesDefault(
				policy));

		Result updateResult = ResumePolicyDatafetcherTestHelper.updateResumePolicy(dgsQueryExecutor, "wrong-id");
		assertFalse(updateResult.isSuccess());
		assertTrue(updateResult.getErrors().contains("policy not found"));

		updateResult = ResumePolicyDatafetcherTestHelper.updateResumePolicy(dgsQueryExecutor, idToUse);
		assertTrue(updateResult.isSuccess());

		ResumePolicy updatedPolicy = ResumePolicyDatafetcherTestHelper.getResumePolicy(dgsQueryExecutor, idToUse);
		assertTrue(ResumePolicyDatafetcherTestHelper.matchesUpdated(
				updatedPolicy));

		boolean wasDeleted = ResumePolicyDatafetcherTestHelper.removeResumePolicy(dgsQueryExecutor, idToUse);
		assertTrue(wasDeleted);
		assertEquals(1, resumePolicyRepo.count());

		wasDeleted = ResumePolicyDatafetcherTestHelper.removeResumePolicy(dgsQueryExecutor, idToUse);
		assertFalse(wasDeleted);

		ResumePolicy missing = ResumePolicyDatafetcherTestHelper.getResumePolicy(dgsQueryExecutor, idToUse);
		assertNull(missing);
	}

	@Test
	void testApplyResumePolicies() throws IOException {
		List<Result> results = ResumePolicyDatafetcherTestHelper.loadResumePolicyWithDuplicate(dgsQueryExecutor);
		assertTrue(results.get(0).isSuccess());

		Result applyResultNoPolicies = ResumePolicyDatafetcherTestHelper.applyResumePolicies(dgsQueryExecutor, Collections.emptyList());
		assertFalse(applyResultNoPolicies.isSuccess());
		assertTrue(applyResultNoPolicies.getErrors().contains("Must provide one or more policy names"));
		assertTrue(applyResultNoPolicies.getInfo().isEmpty());

		Result applyResultInvalidPolicy = ResumePolicyDatafetcherTestHelper.applyResumePolicies(dgsQueryExecutor, List.of(
				ResumePolicyDatafetcherTestHelper.POLICY_NAME1,
				"not-found",
				ResumePolicyDatafetcherTestHelper.POLICY_NAME2));
		assertFalse(applyResultInvalidPolicy.isSuccess());
		assertEquals(1, applyResultInvalidPolicy.getErrors().size());
		assertTrue(applyResultInvalidPolicy.getErrors().contains("Policy name not-found not found"));
		assertTrue(applyResultInvalidPolicy.getInfo().isEmpty());

		Result applyResultGoodNames = ResumePolicyDatafetcherTestHelper.applyResumePolicies(dgsQueryExecutor, List.of(
				ResumePolicyDatafetcherTestHelper.POLICY_NAME1,
				ResumePolicyDatafetcherTestHelper.POLICY_NAME1, // dupe ok
				ResumePolicyDatafetcherTestHelper.POLICY_NAME2));
		assertTrue(applyResultGoodNames.isSuccess());
		assertTrue(applyResultGoodNames.getErrors().isEmpty());
		assertEquals(2, applyResultGoodNames.getInfo().size());
		assertTrue(applyResultGoodNames.getInfo().contains("No DeltaFile errors can be resumed by policy policyName1"));
		assertTrue(applyResultGoodNames.getInfo().contains("No DeltaFile errors can be resumed by policy policyName2"));

		// Real apply test
		String did1 = UUID.randomUUID().toString();
		String did2 = UUID.randomUUID().toString();
		String did3 = UUID.randomUUID().toString();
		DeltaFile before1 = postTransformDeltaFile(did1);
		deltaFileRepo.save(before1);
		DeltaFile before2 = postTransformDeltaFile(did2);
		deltaFileRepo.save(before2);
		DeltaFile before3 = postTransformDeltaFile(did3);
		before3.getSourceInfo().setFlow("flow3");
		deltaFileRepo.save(before3);

		deltaFilesService.handleActionEvent(actionEvent("error", did1));
		deltaFilesService.handleActionEvent(actionEvent("error", did2));
		deltaFilesService.handleActionEvent(actionEvent("error", did3));

		DeltaFile during1 = deltaFilesService.getDeltaFile(did1);
		DeltaFile during2 = deltaFilesService.getDeltaFile(did2);
		DeltaFile during3 = deltaFilesService.getDeltaFile(did3);

		assertNull(during1.getNextAutoResume());
		assertNull(during2.getNextAutoResume());
		assertNull(during3.getNextAutoResume());

		final String errorCause = "Authority XYZ not recognized";

		List<Result> loadA = ResumePolicyDatafetcherTestHelper.loadResumePolicy(dgsQueryExecutor,
				true, "policy1", during1.getSourceInfo().getFlow(), null);
		List<Result> loadB = ResumePolicyDatafetcherTestHelper.loadResumePolicy(dgsQueryExecutor,
				false, "policy2", null, errorCause);

		assertTrue(loadA.get(0).isSuccess());
		assertTrue(loadB.get(0).isSuccess());

		Result applyResults = ResumePolicyDatafetcherTestHelper.applyResumePolicies(dgsQueryExecutor, List.of("policy1", "policy2"));
		assertTrue(applyResults.isSuccess());
		assertTrue(applyResults.getErrors().isEmpty());
		assertEquals(2, applyResults.getInfo().size());
		assertEquals("Applied policy1 policy to 2 DeltaFiles", applyResults.getInfo().get(0));
		assertEquals("Applied policy2 policy to 1 DeltaFiles", applyResults.getInfo().get(1));

		DeltaFile final1 = deltaFilesService.getDeltaFile(did1);
		DeltaFile final2 = deltaFilesService.getDeltaFile(did2);
		DeltaFile final3 = deltaFilesService.getDeltaFile(did3);

		assertNotNull(final1.getNextAutoResume());
		assertNotNull(final2.getNextAutoResume());
		assertNotNull(final3.getNextAutoResume());

		assertEquals("policy1", final1.getNextAutoResumeReason());
		assertEquals("policy1", final2.getNextAutoResumeReason());
		assertEquals("policy2", final3.getNextAutoResumeReason());
	}

	@Test
	void testResumePolicyDryRun() {
		DeltaFile deltaFile = buildErrorDeltaFile("did1", "flow1", "errorCause", "context", MONGO_NOW);
		deltaFileRepo.save(deltaFile);

		DeltaFile deltaFile2 = buildErrorDeltaFile("did2", "flow2", "errorCause", "context", MONGO_NOW);
		deltaFileRepo.save(deltaFile2);

		Result missingName = ResumePolicyDatafetcherTestHelper.resumePolicyDryRun(dgsQueryExecutor, "", "flow1", "message");
		assertFalse(missingName.isSuccess());
		assertTrue(missingName.getErrors().contains("missing name"));
		assertTrue(missingName.getInfo().isEmpty());

		Result noMatch = ResumePolicyDatafetcherTestHelper.resumePolicyDryRun(dgsQueryExecutor, "PolicyName", "flow1", "message");
		assertTrue(noMatch.isSuccess());
		assertTrue(noMatch.getErrors().isEmpty());
		assertTrue(noMatch.getInfo().contains("No DeltaFile errors can be resumed by policy PolicyName"));

		Result oneMatch = ResumePolicyDatafetcherTestHelper.resumePolicyDryRun(dgsQueryExecutor, "PolicyName", "flow1", "errorCause");
		assertTrue(oneMatch.isSuccess());
		assertTrue(oneMatch.getErrors().isEmpty());
		assertTrue(oneMatch.getInfo().contains("Can apply PolicyName policy to 1 DeltaFiles"));

		Result twoMatches = ResumePolicyDatafetcherTestHelper.resumePolicyDryRun(dgsQueryExecutor, "PolicyName", null, "errorCause");
		assertTrue(twoMatches.isSuccess());
		assertTrue(twoMatches.getErrors().isEmpty());
		assertTrue(twoMatches.getInfo().contains("Can apply PolicyName policy to 2 DeltaFiles"));
	}

	@Test
	void deltaFile() {
		DeltaFile expected = deltaFilesService.ingress(INGRESS_INPUT, OffsetDateTime.now(), OffsetDateTime.now());

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
		DeltaFile deltaFile = buildErrorDeltaFile("did", "flow", "errorCause", "context", MONGO_NOW);
		deltaFile.setContentDeleted(MONGO_NOW);
		deltaFile.setContentDeletedReason("contentDeletedReason");
		deltaFile.setNextAutoResume(MONGO_NOW);
		deltaFile.setNextAutoResumeReason("nextAutoResumeReason");
		deltaFileRepo.save(deltaFile);

		DeltaFiles expected = DeltaFiles.builder()
				.offset(0)
				.count(1)
				.totalCount(1)
				.deltaFiles(List.of(deltaFile))
				.build();

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
				new DeltaFilesGraphQLQuery.Builder()
						.limit(5)
						.filter(DeltaFilesFilter.newBuilder().stage(DeltaFileStage.ERROR).build())
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
		DeltaFile input = deltaFilesService.ingress(INGRESS_INPUT, OffsetDateTime.now(), OffsetDateTime.now());
		DeltaFile second = deltaFilesService.ingress(INGRESS_INPUT_2, OffsetDateTime.now(), OffsetDateTime.now());

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(input.getDid());
		deltaFile.errorAction(FLOW, "SampleLoadAction", START_TIME, STOP_TIME, "blah", "blah");
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
		assertEquals(QUEUED, afterResumeFile.getActions().get(2).getState());
		// StateMachine will queue the failed loadAction again leaving the DeltaFile in the IN_FLIGHT stage
		assertEquals(DeltaFileStage.IN_FLIGHT, afterResumeFile.getStage());
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


		Result mockResult = Result.successResult();
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
	void testFindReadyForAutoResume() {
		Action ingress = Action.builder().flow(NORMALIZE_FLOW_NAME).name("ingress").modified(MONGO_NOW).state(ActionState.COMPLETE).build();
		Action hit = Action.builder().flow(NORMALIZE_FLOW_NAME).name("hit").modified(MONGO_NOW).state(ActionState.ERROR).build();
		Action miss = Action.builder().flow(NORMALIZE_FLOW_NAME).name("miss").modified(MONGO_NOW).state(ActionState.ERROR).build();
		Action notSet = Action.builder().flow(NORMALIZE_FLOW_NAME).name("notSet").modified(MONGO_NOW).state(ActionState.ERROR).build();
		Action other = Action.builder().flow(NORMALIZE_FLOW_NAME).name("other").modified(MONGO_NOW).state(ActionState.COMPLETE).build();

		DeltaFile shouldResume = buildDeltaFile("did", NORMALIZE_FLOW_NAME, DeltaFileStage.ERROR, MONGO_NOW, MONGO_NOW);
		shouldResume.setNextAutoResume(MONGO_NOW.minusSeconds(1000));
		shouldResume.setActions(Arrays.asList(ingress, hit, other));
		deltaFileRepo.save(shouldResume);

		DeltaFile shouldNotResume = buildDeltaFile("did2", NORMALIZE_FLOW_NAME, DeltaFileStage.ERROR, MONGO_NOW, MONGO_NOW);
		shouldNotResume.setNextAutoResume(MONGO_NOW.plusSeconds(1000));
		shouldNotResume.setActions(Arrays.asList(ingress, miss));
		deltaFileRepo.save(shouldNotResume);

		DeltaFile notResumable = buildDeltaFile("did3", NORMALIZE_FLOW_NAME, DeltaFileStage.ERROR, MONGO_NOW, MONGO_NOW);
		notResumable.setActions(Arrays.asList(ingress, notSet));
		deltaFileRepo.save(notResumable);

		DeltaFile cancelled = buildDeltaFile("did4", NORMALIZE_FLOW_NAME, DeltaFileStage.CANCELLED, MONGO_NOW, MONGO_NOW);
		cancelled.setNextAutoResume(MONGO_NOW.minusSeconds(1000));
		cancelled.setActions(Arrays.asList(ingress, hit, other));
		deltaFileRepo.save(cancelled);

		DeltaFile contentDeleted = buildDeltaFile("did5", NORMALIZE_FLOW_NAME, DeltaFileStage.ERROR, MONGO_NOW, MONGO_NOW);
		contentDeleted.setNextAutoResume(MONGO_NOW.minusSeconds(1000));
		contentDeleted.setActions(Arrays.asList(ingress, hit, other));
		contentDeleted.setContentDeleted(MONGO_NOW);
		deltaFileRepo.save(contentDeleted);

		DeltaFile shouldAlsoResume = buildDeltaFile("did6", NORMALIZE_FLOW_NAME, DeltaFileStage.ERROR, MONGO_NOW, MONGO_NOW);
		shouldAlsoResume.setNextAutoResume(MONGO_NOW.minusSeconds(1000));
		shouldAlsoResume.setActions(Arrays.asList(ingress, hit, other));
		deltaFileRepo.save(shouldAlsoResume);

		List<DeltaFile> hits = deltaFileRepo.findReadyForAutoResume(MONGO_NOW);
		assertEquals(3, hits.size());
		assertEquals(shouldResume.getDid(), hits.get(0).getDid());
		assertEquals(contentDeleted.getDid(), hits.get(1).getDid());
		assertEquals(shouldAlsoResume.getDid(), hits.get(2).getDid());

		assertEquals(2, deltaFilesService.autoResume(MONGO_NOW));

		Mockito.verify(metricService).increment
				(new Metric(DeltaFiConstants.FILES_AUTO_RESUMED, 2)
						.addTag(DeltaFiConstants.INGRESS_FLOW, NORMALIZE_FLOW_NAME));
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testUpdateForRequeue() {
		Action shouldRequeue = Action.builder().name("hit").modified(MONGO_NOW.minusSeconds(1000)).state(QUEUED).build();
		Action excludedRequeue = Action.builder().name("excluded").modified(MONGO_NOW.minusSeconds(1000)).state(QUEUED).build();
		Action shouldStay = Action.builder().name("miss").modified(MONGO_NOW.plusSeconds(1000)).state(QUEUED).build();

		DeltaFile hit = buildDeltaFile("did", null, DeltaFileStage.IN_FLIGHT, MONGO_NOW, MONGO_NOW.minusSeconds(1000));
		hit.setActions(Arrays.asList(shouldRequeue, shouldStay));
		deltaFileRepo.save(hit);

		DeltaFile miss = buildDeltaFile("did2", null, DeltaFileStage.IN_FLIGHT, MONGO_NOW, MONGO_NOW.plusSeconds(1000));
		miss.setActions(Arrays.asList(shouldStay, shouldStay));
		deltaFileRepo.save(miss);

		DeltaFile miss2 = buildDeltaFile("did3", null, DeltaFileStage.COMPLETE, MONGO_NOW, MONGO_NOW.minusSeconds(1000));
		miss2.setActions(Arrays.asList(shouldStay, shouldStay));
		deltaFileRepo.save(miss2);

		DeltaFile excludedByDid = buildDeltaFile("did4", null, DeltaFileStage.IN_FLIGHT, MONGO_NOW, MONGO_NOW.minusSeconds(1000));
		excludedByDid.setActions(Arrays.asList(shouldRequeue, shouldStay));
		deltaFileRepo.save(excludedByDid);

		DeltaFile excludedByAction = buildDeltaFile("did5", null, DeltaFileStage.IN_FLIGHT, MONGO_NOW, MONGO_NOW.minusSeconds(1000));
		excludedByAction.setActions(Arrays.asList(excludedRequeue, shouldStay));
		deltaFileRepo.save(excludedByAction);

		List<DeltaFile> hits = deltaFileRepo.updateForRequeue(MONGO_NOW, 30, Set.of("excluded", "anotherAction"), Set.of("did4", "did500"));

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
	void batchedBulkDeleteByDidIn() {
		List<DeltaFile> deltaFiles = Stream.of("a", "b", "c").map(Util::buildDeltaFile).toList();
		deltaFileRepo.saveAll(deltaFiles);

		assertEquals(3, deltaFileRepo.count());

		deltaFileRepo.batchedBulkDeleteByDidIn(Arrays.asList("a", "c"));

		assertEquals(1, deltaFileRepo.count());
		assertEquals("b", deltaFileRepo.findAll().get(0).getDid());
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testFindForDeleteCreatedBefore() {
		DeltaFile deltaFile1 = buildDeltaFile("1", null, DeltaFileStage.COMPLETE, OffsetDateTime.now().minusSeconds(1), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile("2", null, DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile2.acknowledgeError(OffsetDateTime.now(), "reason");
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = buildDeltaFile("3", null, DeltaFileStage.IN_FLIGHT, OffsetDateTime.now().plusSeconds(2), OffsetDateTime.now().plusSeconds(2));
		deltaFileRepo.save(deltaFile3);
		DeltaFile deltaFile4 = buildDeltaFile("4", null, DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile2.acknowledgeError(OffsetDateTime.now(), "reason");
		deltaFile4.setContentDeleted(OffsetDateTime.now());
		deltaFileRepo.save(deltaFile4);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForTimedDelete(OffsetDateTime.now().plusSeconds(1), null, 0, null, false, 10);
		assertEquals(List.of(deltaFile1.getDid(), deltaFile2.getDid()), deltaFiles.stream().map(DeltaFile::getDid).toList());
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testFindForDeleteCreatedBeforeBatchLimit() {
		DeltaFile deltaFile1 = buildDeltaFile("1", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile("2", null, DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = buildDeltaFile("3", null, DeltaFileStage.IN_FLIGHT, OffsetDateTime.now().plusSeconds(2), OffsetDateTime.now().plusSeconds(2));
		deltaFileRepo.save(deltaFile3);
		DeltaFile deltaFile4 = buildDeltaFile("4", null, DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile4.setContentDeleted(OffsetDateTime.now());
		deltaFileRepo.save(deltaFile4);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForTimedDelete(OffsetDateTime.now().plusSeconds(1), null, 0, null, false, 1);
		assertEquals(List.of(deltaFile1.getDid()), deltaFiles.stream().map(DeltaFile::getDid).toList());
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testFindForDeleteCreatedBeforeWithMetadata() {
		DeltaFile deltaFile1 = buildDeltaFile("1", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile1.setContentDeleted(OffsetDateTime.now());
		deltaFileRepo.save(deltaFile1);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForTimedDelete(OffsetDateTime.now().plusSeconds(1), null, 0, null, true, 10);
		assertEquals(List.of(deltaFile1.getDid()), deltaFiles.stream().map(DeltaFile::getDid).toList());
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testFindForDeleteCompletedBefore() {
		DeltaFile deltaFile1 = buildDeltaFile("1", null, DeltaFileStage.COMPLETE, OffsetDateTime.now().minusSeconds(1), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile("2", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now().plusSeconds(2));
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = buildDeltaFile("3", null, DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile3);
		DeltaFile deltaFile4 = buildDeltaFile("4", null, DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile4.acknowledgeError(OffsetDateTime.now(), null);
		deltaFile4.updateFlags();
		deltaFileRepo.save(deltaFile4);
		DeltaFile deltaFile5 = buildDeltaFile("5", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile5.setPendingAnnotationsForFlows(Set.of("a"));
		deltaFile5.updateFlags();
		deltaFileRepo.save(deltaFile5);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForTimedDelete(null, OffsetDateTime.now().plusSeconds(1), 0, null, false, 10);
		assertEquals(List.of(deltaFile1.getDid(), deltaFile4.getDid()), deltaFiles.stream().map(DeltaFile::getDid).toList());
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testFindForDeleteWithFlow() {
		DeltaFile deltaFile1 = buildDeltaFile("1", "a", DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile("2", "b", DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile2);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForTimedDelete(OffsetDateTime.now().plusSeconds(1), null, 0, "a", false, 10);
		assertEquals(List.of(deltaFile1.getDid()), deltaFiles.stream().map(DeltaFile::getDid).toList());
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testFindForDelete_alreadyMarkedDeleted() {
		OffsetDateTime oneSecondAgo = OffsetDateTime.now().minusSeconds(1);

		DeltaFile deltaFile1 = buildDeltaFile("1", null, DeltaFileStage.COMPLETE, oneSecondAgo, oneSecondAgo);
		deltaFile1.setContentDeleted(oneSecondAgo);
		deltaFileRepo.save(deltaFile1);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForTimedDelete(OffsetDateTime.now(), null, 0, null, false, 10);
		assertTrue(deltaFiles.isEmpty());
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testFindForDeleteDiskSpace() {
		DeltaFile deltaFile1 = buildDeltaFile("1", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now().minusSeconds(5));
		deltaFile1.setTotalBytes(100L);
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile("2", null, DeltaFileStage.COMPLETE, OffsetDateTime.now().plusSeconds(2), OffsetDateTime.now());
		deltaFile2.setTotalBytes(300L);
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = buildDeltaFile("3", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now().plusSeconds(5));
		deltaFile3.setTotalBytes(500L);
		deltaFileRepo.save(deltaFile3);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForDiskSpaceDelete(250L, null, 100);
		assertEquals(List.of(deltaFile1.getDid(), deltaFile2.getDid()), deltaFiles.stream().map(DeltaFile::getDid).toList());
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testFindForDeleteDiskSpaceAll() {
		DeltaFile deltaFile1 = buildDeltaFile("1", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile1.setTotalBytes(100L);
		deltaFile1.updateFlags();
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile("2", null, DeltaFileStage.COMPLETE, OffsetDateTime.now().plusSeconds(1), OffsetDateTime.now().plusSeconds(2));
		deltaFile2.setTotalBytes(300L);
		deltaFile2.updateFlags();
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = buildDeltaFile("3", null, DeltaFileStage.COMPLETE, OffsetDateTime.now().plusSeconds(2), OffsetDateTime.now());
		deltaFile3.setTotalBytes(500L);
		deltaFile3.updateFlags();
		deltaFileRepo.save(deltaFile3);
		DeltaFile deltaFile4 = buildDeltaFile("4", null, DeltaFileStage.COMPLETE, OffsetDateTime.now().plusSeconds(3), OffsetDateTime.now());
		deltaFile4.setTotalBytes(500L);
		deltaFile4.setContentDeleted(OffsetDateTime.now());
		deltaFile4.updateFlags();
		deltaFileRepo.save(deltaFile4);
		DeltaFile deltaFile5 = buildDeltaFile("5", null, DeltaFileStage.COMPLETE, OffsetDateTime.now().plusSeconds(4), OffsetDateTime.now());
		deltaFile5.setTotalBytes(0L);
		deltaFile5.updateFlags();
		deltaFileRepo.save(deltaFile5);
		DeltaFile deltaFile6 = buildDeltaFile("6", null, DeltaFileStage.IN_FLIGHT, OffsetDateTime.now().plusSeconds(5), OffsetDateTime.now());
		deltaFile6.setTotalBytes(50L);
		deltaFile6.updateFlags();
		deltaFileRepo.save(deltaFile6);
		DeltaFile deltaFile7 = buildDeltaFile("7", null, DeltaFileStage.COMPLETE, OffsetDateTime.now().plusSeconds(6), OffsetDateTime.now());
		deltaFile7.setTotalBytes(1000L);
		deltaFile7.setPendingAnnotationsForFlows(Set.of("a"));
		deltaFile7.updateFlags();
		deltaFileRepo.save(deltaFile7);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForDiskSpaceDelete(2500L, null, 100);
		assertEquals(Stream.of(deltaFile1.getDid(), deltaFile2.getDid(), deltaFile3.getDid()).sorted().toList(), deltaFiles.stream().map(DeltaFile::getDid).sorted().toList());
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testFindForDeleteDiskSpaceBatchSizeLimited() {
		DeltaFile deltaFile1 = buildDeltaFile("1", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile1.setTotalBytes(100L);
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile("2", null, DeltaFileStage.COMPLETE, OffsetDateTime.now().plusSeconds(2), OffsetDateTime.now().plusSeconds(1));
		deltaFile2.setTotalBytes(300L);
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = buildDeltaFile("3", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now().plusSeconds(2));
		deltaFile3.setTotalBytes(500L);
		deltaFileRepo.save(deltaFile3);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForDiskSpaceDelete(2500L, null, 2);
		assertEquals(List.of(deltaFile1.getDid(), deltaFile2.getDid()), deltaFiles.stream().map(DeltaFile::getDid).toList());
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testFindForDeleteDiskSpaceBatchSizeFlow() {
		DeltaFile deltaFile1 = buildDeltaFile("1", "a", DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile1.setTotalBytes(100L);
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile("2", "a", DeltaFileStage.COMPLETE, OffsetDateTime.now().plusSeconds(1), OffsetDateTime.now().plusSeconds(2));
		deltaFile2.setTotalBytes(300L);
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = buildDeltaFile("3", "b", DeltaFileStage.COMPLETE, OffsetDateTime.now().plusSeconds(2), OffsetDateTime.now());
		deltaFile3.setTotalBytes(500L);
		deltaFileRepo.save(deltaFile3);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForDiskSpaceDelete(2500L, "a", 100);
		assertEquals(List.of(deltaFile1.getDid(), deltaFile2.getDid()), deltaFiles.stream().map(DeltaFile::getDid).toList());
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testDeltaFiles_all() {
		DeltaFile deltaFile1 = buildDeltaFile("1", null, DeltaFileStage.COMPLETE, MONGO_NOW.minusSeconds(2), MONGO_NOW.minusSeconds(2));
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile("2", null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.plusSeconds(2));
		deltaFileRepo.save(deltaFile2);

		DeltaFiles deltaFiles = deltaFileRepo.deltaFiles(null, 50, new DeltaFilesFilter(), null, null);
		assertEquals(deltaFiles.getDeltaFiles(), List.of(deltaFile2, deltaFile1));
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testDeltaFiles_limit() {
		DeltaFile deltaFile1 = buildDeltaFile("1", null, DeltaFileStage.COMPLETE, MONGO_NOW.minusSeconds(2), MONGO_NOW.minusSeconds(2));
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile("2", null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.plusSeconds(2));
		deltaFileRepo.save(deltaFile2);

		DeltaFiles deltaFiles = deltaFileRepo.deltaFiles(null, 1, new DeltaFilesFilter(), null, null);
		assertEquals(1, deltaFiles.getCount());
		assertEquals(2, deltaFiles.getTotalCount());

		deltaFiles = deltaFileRepo.deltaFiles(null, 2, new DeltaFilesFilter(), null, null);
		assertEquals(2, deltaFiles.getCount());
		assertEquals(2, deltaFiles.getTotalCount());

		deltaFiles = deltaFileRepo.deltaFiles(null, 100, new DeltaFilesFilter(), null, null);
		assertEquals(2, deltaFiles.getCount());
		assertEquals(2, deltaFiles.getTotalCount());

		deltaFiles = deltaFileRepo.deltaFiles(1, 100, new DeltaFilesFilter(), null, null);
		assertEquals(1, deltaFiles.getCount());
		assertEquals(2, deltaFiles.getTotalCount());
	}

	@Test
	void testDeltaFiles_offset() {
		DeltaFile deltaFile1 = buildDeltaFile("1", null, DeltaFileStage.COMPLETE, MONGO_NOW.minusSeconds(2), MONGO_NOW.plusSeconds(2));
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile("2", null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));
		deltaFileRepo.save(deltaFile2);

		DeltaFiles deltaFiles = deltaFileRepo.deltaFiles(0, 50, new DeltaFilesFilter(), null, null);
		assertEquals(0, deltaFiles.getOffset());
		assertEquals(List.of(deltaFile1, deltaFile2), deltaFiles.getDeltaFiles());

		deltaFiles = deltaFileRepo.deltaFiles(1, 50, new DeltaFilesFilter(), null, null);
		assertEquals(1, deltaFiles.getOffset());
		assertEquals(List.of(deltaFile2), deltaFiles.getDeltaFiles());

		deltaFiles = deltaFileRepo.deltaFiles(2, 50, new DeltaFilesFilter(), null, null);
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
				DeltaFileOrder.newBuilder().direction(DeltaFileDirection.ASC).field("created").build(), null);
		assertEquals(List.of(deltaFile1, deltaFile2), deltaFiles.getDeltaFiles());

		deltaFiles = deltaFileRepo.deltaFiles(null, 50, new DeltaFilesFilter(),
				DeltaFileOrder.newBuilder().direction(DeltaFileDirection.DESC).field("created").build(), null);
		assertEquals(List.of(deltaFile2, deltaFile1), deltaFiles.getDeltaFiles());

		deltaFiles = deltaFileRepo.deltaFiles(null, 50, new DeltaFilesFilter(),
				DeltaFileOrder.newBuilder().direction(DeltaFileDirection.ASC).field("modified").build(), null);
		assertEquals(List.of(deltaFile2, deltaFile1), deltaFiles.getDeltaFiles());

		deltaFiles = deltaFileRepo.deltaFiles(null, 50, new DeltaFilesFilter(),
				DeltaFileOrder.newBuilder().direction(DeltaFileDirection.DESC).field("modified").build(), null);
		assertEquals(List.of(deltaFile1, deltaFile2), deltaFiles.getDeltaFiles());
	}

	@Test
	void testDeltaFiles_filter() {
		DeltaFile deltaFile1 = buildDeltaFile("1", null, DeltaFileStage.COMPLETE, MONGO_NOW.minusSeconds(2), MONGO_NOW.plusSeconds(2));
		deltaFile1.setIngressBytes(100L);
		deltaFile1.setTotalBytes(1000L);
		deltaFile1.addAnnotations(Map.of("a.1", "first", "common", "value"));
		deltaFile1.setContentDeleted(MONGO_NOW);
		deltaFile1.setSourceInfo(new SourceInfo("filename1", "flow1", Map.of("key1", "value1", "key2", "value2")));
		deltaFile1.setActions(List.of(Action.builder().name("action1")
				.state(ActionState.COMPLETE)
				.content(List.of(new Content("formattedFilename1", "mediaType")))
				.metadata(Map.of("formattedKey1", "formattedValue1", "formattedKey2", "formattedValue2"))
				.build()));
		deltaFile1.acknowledgeError(MONGO_NOW, null);
		deltaFile1.incrementRequeueCount();
		deltaFile1.addEgressFlow("MyEgressFlow");
		deltaFile1.setTestModeReason("TestModeReason");
		deltaFileRepo.save(deltaFile1);

		DeltaFile deltaFile2 = buildDeltaFile("2", null, DeltaFileStage.ERROR, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));
		deltaFile2.setIngressBytes(200L);
		deltaFile2.setTotalBytes(2000L);
		deltaFile2.addAnnotations(Map.of("a.2", "first", "common", "value"));
		deltaFile2.setSourceInfo(new SourceInfo("filename2", "flow2", Map.of()));
		deltaFile2.setActions(List.of(
				Action.builder().name("action1")
						.state(ActionState.ERROR)
						.errorCause("Cause")
						.build(),
				Action.builder().name("action2")
						.state(ActionState.COMPLETE)
						.content(List.of(new Content("formattedFilename2", "mediaType")))
						.build()));
		deltaFile2.setEgressed(true);
		deltaFile2.setFiltered(true);
		deltaFile2.addEgressFlow("MyEgressFlow");
		deltaFile2.addEgressFlow("MyEgressFlow2");
		deltaFileRepo.save(deltaFile2);

		DeltaFile deltaFile3 = buildDeltaFile("3", null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(3), MONGO_NOW.minusSeconds(3));
		deltaFile3.setIngressBytes(300L);
		deltaFile3.setTotalBytes(3000L);
		deltaFile3.addAnnotations(Map.of("b.2", "first", "common", "value"));
		deltaFile3.setSourceInfo(new SourceInfo("filename3", "flow3", Map.of()));
		deltaFile3.setActions(List.of(
				Action.builder()
						.name("action2")
						.state(ActionState.FILTERED)
						.filteredCause("Coffee")
						.filteredContext("and donuts")
						.build(),
				Action.builder()
						.name("action2")
						.state(ActionState.COMPLETE)
						.content(List.of(new Content("formattedFilename3", "mediaType")))
						.build()));
		deltaFile3.setEgressed(true);
		deltaFile3.setFiltered(true);
		deltaFile3.addEgressFlow("MyEgressFlow3");
		deltaFileRepo.save(deltaFile3);

		testFilter(DeltaFilesFilter.newBuilder().testMode(true).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().testMode(false).build(), deltaFile2, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().createdAfter(MONGO_NOW).build(), deltaFile2, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().createdBefore(MONGO_NOW).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().contentDeleted(true).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().contentDeleted(false).build(), deltaFile2, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().modifiedAfter(MONGO_NOW).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().modifiedBefore(MONGO_NOW).build(), deltaFile2, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().requeueCountMin(1).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().requeueCountMin(0).build(), deltaFile1, deltaFile2, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().ingressBytesMin(50L).build(), deltaFile1, deltaFile2, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().ingressBytesMin(150L).build(), deltaFile2, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().ingressBytesMax(250L).build(), deltaFile1, deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().ingressBytesMax(150L).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().ingressBytesMax(100L).ingressBytesMin(100L).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().totalBytesMin(500L).build(), deltaFile1, deltaFile2, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().totalBytesMin(1500L).build(), deltaFile2, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().totalBytesMax(2500L).build(), deltaFile1, deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().totalBytesMax(1500L).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().totalBytesMax(1000L).totalBytesMin(1000L).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().stage(DeltaFileStage.COMPLETE).build(), deltaFile1, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().sourceInfo(SourceInfoFilter.newBuilder().metadata(List.of(new KeyValue("key1", "value1"))).build()).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().sourceInfo(SourceInfoFilter.newBuilder().metadata(List.of(new KeyValue("key1", "value1"), new KeyValue("key2", "value2"))).build()).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().sourceInfo(SourceInfoFilter.newBuilder().metadata(List.of(new KeyValue("key1", "value1"), new KeyValue("key2", "value1"))).build()).build());
		testFilter(DeltaFilesFilter.newBuilder().actions(Collections.emptyList()).build(), deltaFile1, deltaFile2, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().actions(List.of("action1")).build(), deltaFile1, deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().actions(List.of("action1", "action2")).build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().errorCause("^Cause$").build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().filtered(true).filteredCause("^Coffee$").build(), deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().filteredCause("off").build(), deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().filteredCause("nope").build());
		testFilter(DeltaFilesFilter.newBuilder().dids(Collections.emptyList()).build(), deltaFile1, deltaFile2, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().dids(Collections.singletonList("1")).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().dids(List.of("1", "3")).build(), deltaFile1, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().dids(List.of("1", "2")).build(), deltaFile1, deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().dids(List.of("5", "4")).build());
		testFilter(DeltaFilesFilter.newBuilder().errorAcknowledged(true).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().errorAcknowledged(false).build(), deltaFile2, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().egressed(false).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().egressed(true).build(), deltaFile2, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().filtered(false).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().filtered(true).build(), deltaFile2, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().annotations(List.of(new KeyValue("common", "value"))).build(), deltaFile1, deltaFile2, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().annotations(List.of(new KeyValue("a.1", "first"))).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().annotations(List.of(new KeyValue("a.1", "first"), new KeyValue("common", "value"))).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().annotations(List.of(new KeyValue("a.1", "first"), new KeyValue("common", "value"), new KeyValue("extra", "missing"))).build());
		testFilter(DeltaFilesFilter.newBuilder().annotations(List.of(new KeyValue("a.1", "first"), new KeyValue("common", "miss"))).build());
		testFilter(DeltaFilesFilter.newBuilder().egressFlows(List.of("MyEgressFlowz")).build());
		testFilter(DeltaFilesFilter.newBuilder().egressFlows(List.of("MyEgressFlow")).build(), deltaFile1, deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().egressFlows(List.of("MyEgressFlow2")).build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().egressFlows(List.of("MyEgressFlow", "MyEgressFlow2")).build(), deltaFile1, deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().egressFlows(List.of("MyEgressFlow", "MyEgressFlow3")).build(), deltaFile1, deltaFile2, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().sourceInfo(SourceInfoFilter.newBuilder().ingressFlows(List.of("flow1", "flow2")).build()).build(), deltaFile1, deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().sourceInfo(SourceInfoFilter.newBuilder().ingressFlows(List.of("flow2")).build()).build(), deltaFile2);
	}

	@Test
	void testQueryByFilterMessage() {
		// Not filtered
		DeltaFile deltaFile1 = buildDeltaFile("1", null, DeltaFileStage.COMPLETE, MONGO_NOW, MONGO_NOW);
		deltaFile1.setActions(List.of(Action.builder().name("action1").build()));
		deltaFileRepo.save(deltaFile1);
		// Not filtered, with errorCause
		DeltaFile deltaFile2 = buildDeltaFile("2", null, DeltaFileStage.ERROR, MONGO_NOW.plusSeconds(1), MONGO_NOW.plusSeconds(1));
		deltaFile2.setActions(List.of(Action.builder().name("action1").state(ActionState.ERROR).errorCause("Error reason 1").build()));
		deltaFileRepo.save(deltaFile2);
		// Filtered, reason 1
		DeltaFile deltaFile3 = buildDeltaFile("3", null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.plusSeconds(2));
		deltaFile3.setActions(List.of(Action.builder().name("action1").state(ActionState.FILTERED).errorCause("Filtered reason 1").build()));
		deltaFile3.setFiltered(true);
		deltaFileRepo.save(deltaFile3);
		// Filtered, reason 2
		DeltaFile deltaFile4 = buildDeltaFile("4", null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(3), MONGO_NOW.plusSeconds(3));
		deltaFile4.setActions(List.of(Action.builder().name("action1").state(ActionState.ERROR).filteredCause("Filtered reason 2").build()));
		deltaFile4.setFiltered(true);
		deltaFileRepo.save(deltaFile4);
		DeltaFile deltaFile5 = buildDeltaFile("5", null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(3), MONGO_NOW.plusSeconds(3));
		deltaFile5.setActions(List.of(Action.builder().name("action1").state(ActionState.FILTERED).filteredCause("Filtered reason 2").build()));
		deltaFile5.setFiltered(true);
		deltaFileRepo.save(deltaFile5);

		testFilter(DeltaFilesFilter.newBuilder().errorCause("reason").build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().filteredCause("reason").build(), deltaFile5);
	}

	@Test
	void testQueryByCanReplay() {
		DeltaFile noContent = buildDeltaFile("1", null, DeltaFileStage.COMPLETE, MONGO_NOW, MONGO_NOW);
		noContent.setContentDeleted(MONGO_NOW);
		noContent.setEgressed(true);
		DeltaFile hasReplayDate = buildDeltaFile("2", null, DeltaFileStage.COMPLETE, MONGO_NOW, MONGO_NOW);
		hasReplayDate.setReplayed(MONGO_NOW);
		DeltaFile replayable = buildDeltaFile("3", null, DeltaFileStage.COMPLETE, MONGO_NOW, MONGO_NOW);

		deltaFileRepo.saveAll(List.of(noContent, hasReplayDate, replayable));

		testFilter(DeltaFilesFilter.newBuilder().replayable(true).build(), replayable);
		testFilter(DeltaFilesFilter.newBuilder().replayable(false).build(), hasReplayDate, noContent);

		// make sure the content or replay criteria is properly nested within the outer and criteria
		testFilter(DeltaFilesFilter.newBuilder().replayable(false).egressed(true).build(), noContent);
	}

	@Test
	void testQueryByIsReplayed() {
		DeltaFile hasReplayDate = buildDeltaFile("2", null, DeltaFileStage.COMPLETE, MONGO_NOW, MONGO_NOW);
		hasReplayDate.setReplayed(MONGO_NOW);
		DeltaFile noReplayDate = buildDeltaFile("3", null, DeltaFileStage.COMPLETE, MONGO_NOW, MONGO_NOW);

		deltaFileRepo.saveAll(List.of(hasReplayDate, noReplayDate));

		testFilter(DeltaFilesFilter.newBuilder().replayed(true).build(), hasReplayDate);
		testFilter(DeltaFilesFilter.newBuilder().replayed(false).build(), noReplayDate);
	}

	@Test
	void testFilterByTerminalStage() {
		DeltaFile ingress = buildDeltaFile("1", null, DeltaFileStage.IN_FLIGHT, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));
		DeltaFile enrich = buildDeltaFile("2", null, DeltaFileStage.IN_FLIGHT, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));
		DeltaFile egress = buildDeltaFile("3", null, DeltaFileStage.IN_FLIGHT, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));
		DeltaFile complete = buildDeltaFile("4", null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));
		DeltaFile error = buildDeltaFile("5", null, DeltaFileStage.ERROR, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));
		error.acknowledgeError(MONGO_NOW, "acked");
		DeltaFile cancelled = buildDeltaFile("6", null, DeltaFileStage.CANCELLED, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));
		deltaFileRepo.saveAll(List.of(ingress, enrich, egress, complete, error, cancelled));
		testFilter(DeltaFilesFilter.newBuilder().terminalStage(true).build(), cancelled, error, complete);
		testFilter(DeltaFilesFilter.newBuilder().terminalStage(false).build(), egress, enrich, ingress);
		testFilter(DeltaFilesFilter.newBuilder().stage(DeltaFileStage.CANCELLED).terminalStage(false).build());
		testFilter(DeltaFilesFilter.newBuilder().stage(DeltaFileStage.IN_FLIGHT).terminalStage(true).build());
	}

	@Test
	void testFilterByPendingAnnotations() {
		DeltaFile pending = buildDeltaFile("1", "a", DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));
		pending.setPendingAnnotationsForFlows(Set.of("a"));
		DeltaFile notPending = buildDeltaFile("2", "a", DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));

		deltaFileRepo.saveAll(List.of(pending, notPending));
		testFilter(DeltaFilesFilter.newBuilder().pendingAnnotations(true).build(), pending);
		testFilter(DeltaFilesFilter.newBuilder().pendingAnnotations(false).build(), notPending);
	}

	@Test
	void testFilterByFilename() {
		DeltaFile deltaFile = new DeltaFile();
		SourceInfo sourceInfo = SourceInfo.builder().filename("filename").build();
		deltaFile.setSourceInfo(sourceInfo);
		deltaFile.setSchemaVersion(CURRENT_SCHEMA_VERSION);

		DeltaFile deltaFile1 = new DeltaFile();
		SourceInfo sourceInfo1 = SourceInfo.builder().filename("file").build();
		deltaFile1.setSourceInfo(sourceInfo1);
		deltaFile1.setSchemaVersion(CURRENT_SCHEMA_VERSION);

		deltaFileRepo.saveAll(List.of(deltaFile1, deltaFile));

		testFilter(filenameFilter("iLe", true, true));
		testFilter(filenameFilter("iLe", true, false), deltaFile, deltaFile1);
		testFilter(filenameFilter("ile", true, true), deltaFile, deltaFile1);
		testFilter(filenameFilter("ilen", true, true), deltaFile);
		testFilter(filenameFilter("^FILE", true, false), deltaFile, deltaFile1);

		testFilter(filenameFilter("FILE", false, true));
		testFilter(filenameFilter("FILE", false, false), deltaFile1);
		testFilter(filenameFilter("file", false, true), deltaFile1);
	}

	private DeltaFilesFilter filenameFilter(String filename, boolean regex, boolean caseSensitive) {
		FilenameFilter filenameFilter = FilenameFilter.newBuilder().filename(filename).regex(regex).caseSensitive(caseSensitive).build();
		return DeltaFilesFilter.newBuilder().sourceInfo(SourceInfoFilter.newBuilder().filenameFilter(filenameFilter).build()).build();
	}

	private void testFilter(DeltaFilesFilter filter, DeltaFile... expected) {
		DeltaFiles deltaFiles = deltaFileRepo.deltaFiles(null, 50, filter, null, null);
		assertEquals(new ArrayList<>(Arrays.asList(expected)), deltaFiles.getDeltaFiles());
	}

	@Test
	void testAnnotationKeys() {
		assertThat(deltaFilesService.annotationKeys()).isEmpty();

		DeltaFile nullKeys = new DeltaFile();
		nullKeys.setDid("nullKeys");
		nullKeys.setAnnotationKeys(null);
		deltaFileRepo.save(nullKeys);

		assertThat(deltaFilesService.annotationKeys()).isEmpty();

		DeltaFile emptyKeys = new DeltaFile();
		emptyKeys.setDid("emptyKeys");
		emptyKeys.setAnnotationKeys(Set.of());
		deltaFileRepo.save(emptyKeys);

		assertThat(deltaFilesService.annotationKeys()).isEmpty();

		DeltaFile withKeys = new DeltaFile();
		withKeys.setDid("withKeys");
		withKeys.setAnnotationKeys(Set.of("a", "b"));

		DeltaFile otherDomain = new DeltaFile();
		otherDomain.setDid("otherDomain");
		otherDomain.setAnnotationKeys(Set.of("b", "c", "d"));
		deltaFileRepo.saveAll(List.of(withKeys, otherDomain));

		assertThat(deltaFilesService.annotationKeys()).hasSize(4).contains("a", "b", "c", "d");
	}

	@Test
	void deleteTransformFlowPlanByPlugin() {
		clearForFlowTests();
		PluginCoordinates pluginToDelete = PluginCoordinates.builder().groupId("group").artifactId("deltafi-actions").version("1.0.0").build();

		TransformFlowPlan transformFlowPlanA = new TransformFlowPlan("a", null, null);
		transformFlowPlanA.setSourcePlugin(pluginToDelete);
		TransformFlowPlan transformFlowPlanB = new TransformFlowPlan("b", null, null);
		transformFlowPlanB.setSourcePlugin(pluginToDelete);
		TransformFlowPlan transformFlowPlanC = new TransformFlowPlan("c", null, null);
		transformFlowPlanC.setSourcePlugin(PluginCoordinates.builder().groupId("group2").artifactId("deltafi-actions").version("1.0.0").build());
		transformFlowPlanRepo.saveAll(List.of(transformFlowPlanA, transformFlowPlanB, transformFlowPlanC));
		assertThat(transformFlowPlanRepo.deleteBySourcePlugin(pluginToDelete)).isEqualTo(2);
		assertThat(transformFlowPlanRepo.count()).isEqualTo(1);
	}

	@Test
	void deleteTransformFlowByPlugin() {
		clearForFlowTests();
		PluginCoordinates pluginToDelete = PluginCoordinates.builder().groupId("group").artifactId("deltafi-actions").version("1.0.0").build();

		TransformFlow transformFlowA = new TransformFlow();
		transformFlowA.setName("a");
		transformFlowA.setSourcePlugin(pluginToDelete);

		TransformFlow transformFlowB = new TransformFlow();
		transformFlowB.setName("b");
		transformFlowB.setSourcePlugin(pluginToDelete);

		TransformFlow transformFlowC = new TransformFlow();
		transformFlowC.setName("c");
		transformFlowC.setSourcePlugin(PluginCoordinates.builder().groupId("group2").artifactId("deltafi-actions").version("1.0.0").build());
		transformFlowRepo.saveAll(List.of(transformFlowA, transformFlowB, transformFlowC));
		assertThat(transformFlowRepo.deleteBySourcePlugin(pluginToDelete)).isEqualTo(2);
		assertThat(transformFlowRepo.count()).isEqualTo(1);
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
	void testFindFlowsByGroupAndArtifact() {
		clearForFlowTests();
		PluginCoordinates newCoordinates = PluginCoordinates.builder().groupId("group").artifactId("deltafi-actions").version("2.0.0").build();

		TransformFlow transformFlowA = buildTransformFlow("a", newCoordinates);
		TransformFlow transformFlowB = buildTransformFlow("b", newCoordinates);
		TransformFlow transformFlowC = buildTransformFlow("c", "group", "deltafi-actions", "1.0.0");
		TransformFlow diffGroup = buildTransformFlow("d", "group2", "deltafi-actions", "1.0.0");
		TransformFlow diffArtifactId = buildTransformFlow("e", "group", "deltafi-actions2", "1.0.0");
		transformFlowRepo.saveAll(List.of(transformFlowA, transformFlowB, transformFlowC, diffGroup, diffArtifactId));
		refreshFlowCaches();

		List<TransformFlow> found = transformFlowRepo.findByGroupIdAndArtifactId("group", "deltafi-actions");
		assertThat(found).hasSize(3).contains(transformFlowA, transformFlowB, transformFlowC);
	}

	@Test
	void testFindFlowsPlanByGroupAndArtifact() {
		clearForFlowTests();
		PluginCoordinates newCoordinates = PluginCoordinates.builder().groupId("group").artifactId("deltafi-actions").version("2.0.0").build();

		TransformFlowPlan transformFlowPlanA = buildTransformFlowPlan("a", newCoordinates);
		TransformFlowPlan transformFlowPlanB = buildTransformFlowPlan("b", newCoordinates);
		TransformFlowPlan transformFlowPlanC = buildTransformFlowPlan("c", "group", "deltafi-actions", "1.0.0");
		TransformFlowPlan diffGroup = buildTransformFlowPlan("d", "group2", "deltafi-actions", "1.0.0");
		TransformFlowPlan diffArtifactId = buildTransformFlowPlan("e", "group", "deltafi-actions-2", "1.0.0");

		transformFlowPlanRepo.saveAll(List.of(transformFlowPlanA, transformFlowPlanB, transformFlowPlanC, diffGroup, diffArtifactId));
		refreshFlowCaches();

		List<TransformFlowPlan> found = transformFlowPlanRepo.findByGroupIdAndArtifactId("group", "deltafi-actions");
		assertThat(found).hasSize(3).contains(transformFlowPlanA, transformFlowPlanB, transformFlowPlanC);
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
		assertThat(pluginVariableRepo.findIgnoringVersion(newVersion.getGroupId(), newVersion.getArtifactId())).hasSize(1).contains(variables);
	}

	@Test
	void testResetAllUnmaskedVariableValues() {
		PluginVariables variables = new PluginVariables();
		PluginCoordinates coords = PluginCoordinates.builder().groupId("org").artifactId("reset-test").version("1").build();
		variables.setSourcePlugin(coords);

		Variable notSet = Util.buildVariable("notSet", null, "default");
		Variable notSetAndMasked = Util.buildVariable("notSetAndMasked", null, "default");
		notSetAndMasked.setMasked(true);
		Variable setValue = Util.buildVariable("setValue", "value", "default");
		Variable setValueAndMasked = Util.buildVariable("setValueAndMasked", "value", "default");
		setValueAndMasked.setMasked(true);

		variables.setVariables(List.of(notSet, notSetAndMasked, setValue, setValueAndMasked));

		pluginVariableRepo.save(variables);

		pluginVariableRepo.resetAllUnmaskedVariableValues();

		Map<String, Variable> updatedVars = pluginVariableRepo.findById(coords).orElseThrow()
				.getVariables().stream().collect(Collectors.toMap(Variable::getName, Function.identity()));

		assertThat(updatedVars.get("notSet")).isEqualTo(notSet);
		assertThat(updatedVars.get("notSetAndMasked")).isEqualTo(notSetAndMasked);
		assertThat(updatedVars.get("setValueAndMasked")).isEqualTo(setValueAndMasked);

		Variable updatedSetValue = updatedVars.get("setValue");
		assertThat(updatedSetValue).isNotEqualTo(setValue);
		assertThat(updatedSetValue.getValue()).isNull();
	}

	@Test
	void testConcurrentPluginVariableRegistration() {
		IntStream.range(0, 100).forEach(this::testConcurrentPluginVariableRegistration);
	}

	void testConcurrentPluginVariableRegistration(int ignoreI) {
		PluginCoordinates oldVersion = PluginCoordinates.builder().groupId("org").artifactId("deltafi").version("1").build();
		PluginCoordinates newVersion = PluginCoordinates.builder().groupId("org").artifactId("deltafi").version("2").build();

		// Save the original set of variables with values set
		PluginVariables originalPluginVariables = new PluginVariables();
		List<Variable> variableList = Stream.of("var1", "var2", "var3", "var4").map(Util::buildOriginalVariable).toList();
		originalPluginVariables.setVariables(variableList);
		originalPluginVariables.setSourcePlugin(oldVersion);
		pluginVariableRepo.save(originalPluginVariables);

		// new set of variables that need to get the set value added in
		List<Variable> newVariables = Stream.of("var2", "var3", "var4", "var5").map(Util::buildNewVariable).toList();

		final int numMockPlugins = 15;
		Executor mockRegistryExecutor = Executors.newFixedThreadPool(3);
		List<CompletableFuture<Void>> futures = IntStream.range(0, numMockPlugins)
				.mapToObj(i -> submitNewVariables(mockRegistryExecutor, newVersion, newVariables))
				.toList();

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[numMockPlugins])).join();

		PluginVariables afterRegistrations = pluginVariableRepo.findById(newVersion).orElse(null);
		assertThat(afterRegistrations).isNotNull();
		List<Variable> varsAfter = afterRegistrations.getVariables();
		assertThat(varsAfter).hasSize(4);

		for (Variable variable : varsAfter) {
			assertThat(variable.getDefaultValue()).isEqualTo("new default value");
			assertThat(variable.getDescription()).isEqualTo("describe new default value");

			if (variable.getName().equals("var1")) {
				Assertions.fail("Var 1 should no longer exist");
			} else if (variable.getName().equals("var5"))
				assertThat(variable.getValue()).isNull();
			else {
				assertThat(variable.getValue()).isEqualTo("set value");
			}
		}

	}

	private CompletableFuture<Void> submitNewVariables(Executor executor, PluginCoordinates pluginCoordinates, List<Variable> variables) {
		return CompletableFuture.runAsync(() -> pluginVariableService.saveVariables(pluginCoordinates, variables), executor);
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
						.filter(ErrorSummaryFilter.builder().modifiedBefore(plusTwo).build())
						.orderBy(DeltaFileOrder.newBuilder().field("flow").direction(DeltaFileDirection.DESC).build())
						.build(),
				ERRORS_BY_FLOW_PROJECTION_ROOT
		);

		SummaryByFlow actual = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQLQueryRequest.serialize(),
				"data." + DgsConstants.QUERY.ErrorSummaryByFlow,
				SummaryByFlow.class
		);

		assertEquals(3, actual.count());
		assertEquals(0, actual.offset());
		assertEquals(3, actual.totalCount());
		assertEquals(3, actual.countPerFlow().size());
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

		SummaryByFlow firstPage = deltaFilesService.getErrorSummaryByFlow(
				0, 2, null, null);

		assertEquals(2, firstPage.count());
		assertEquals(0, firstPage.offset());
		assertEquals(3, firstPage.totalCount());
		assertEquals(2, firstPage.countPerFlow().size());

		assertEquals("flow1", firstPage.countPerFlow().get(0).getFlow());
		assertEquals("flow2", firstPage.countPerFlow().get(1).getFlow());

		assertEquals(2, firstPage.countPerFlow().get(0).getCount());
		assertEquals(1, firstPage.countPerFlow().get(1).getCount());

		assertTrue(firstPage.countPerFlow().get(0).getDids().containsAll(List.of("1", "4")));
		assertTrue(firstPage.countPerFlow().get(1).getDids().contains("3"));

		SummaryByFlow secondPage = deltaFilesService.getErrorSummaryByFlow(
				2, 2, null, null);

		assertEquals(1, secondPage.count());
		assertEquals(2, secondPage.offset());
		assertEquals(3, secondPage.totalCount());
		assertEquals(1, secondPage.countPerFlow().size());

		assertEquals("flow3", secondPage.countPerFlow().get(0).getFlow());
		assertEquals(1, secondPage.countPerFlow().get(0).getCount());
		assertTrue(secondPage.countPerFlow().get(0).getDids().contains("5"));

		DeltaFile deltaFile6 = buildDeltaFile("6", "flow3", DeltaFileStage.ERROR, now, minusTwo);
		deltaFileRepo.save(deltaFile6);

		DeltaFile deltaFile7 = buildDeltaFile("7", "flow3", DeltaFileStage.ERROR, now, minusTwo);
		deltaFileRepo.save(deltaFile7);

		SummaryByFlow filterByTime = deltaFilesService.getErrorSummaryByFlow(
				0, 99, ErrorSummaryFilter.builder()
						.modifiedBefore(now)
						.build(),
				DeltaFileOrder.newBuilder()
						.field("Count")
						.direction(DeltaFileDirection.DESC)
						.build());

		assertEquals(2, filterByTime.count());
		assertEquals(0, filterByTime.offset());
		assertEquals(2, filterByTime.totalCount());
		assertEquals(2, filterByTime.countPerFlow().size());

		assertEquals(2, filterByTime.countPerFlow().get(0).getCount());
		assertEquals(1, filterByTime.countPerFlow().get(1).getCount());

		assertEquals("flow3", filterByTime.countPerFlow().get(0).getFlow());
		assertEquals("flow2", filterByTime.countPerFlow().get(1).getFlow());

		SummaryByFlow filterByFlow = deltaFilesService.getErrorSummaryByFlow(
				0, 99, ErrorSummaryFilter.builder()
						.flow("flow3")
						.modifiedBefore(now)
						.build(),
				DeltaFileOrder.newBuilder()
						.field("Flow")
						.direction(DeltaFileDirection.ASC)
						.build());

		assertEquals(1, filterByFlow.count());
		assertEquals(0, filterByFlow.offset());
		assertEquals(1, filterByFlow.totalCount());
		assertEquals(1, filterByFlow.countPerFlow().size());
		assertEquals(2, filterByFlow.countPerFlow().get(0).getCount());
		assertEquals("flow3", filterByFlow.countPerFlow().get(0).getFlow());

		SummaryByFlow noneFound = deltaFilesService.getErrorSummaryByFlow(
				0, 99, ErrorSummaryFilter.builder()
						.flow("flowNotFound")
						.modifiedBefore(now)
						.build(), null);

		assertEquals(0, noneFound.count());
		assertEquals(0, noneFound.offset());
		assertEquals(0, noneFound.totalCount());
		assertEquals(0, noneFound.countPerFlow().size());
	}

	@Test
	void testErrorCountsByFlow() {
		OffsetDateTime now = OffsetDateTime.now();

		DeltaFile deltaFile1 = buildDeltaFile("1", "flow1", DeltaFileStage.ERROR, now, null);
		deltaFileRepo.save(deltaFile1);

		DeltaFile deltaFile2 = buildDeltaFile("2", "flow1", DeltaFileStage.COMPLETE, now, now);
		deltaFileRepo.save(deltaFile2);

		DeltaFile deltaFile3 = buildDeltaFile("3", "flow2", DeltaFileStage.ERROR, now, null);
		deltaFileRepo.save(deltaFile3);

		DeltaFile deltaFile4 = buildDeltaFile("4", "flow1", DeltaFileStage.ERROR, now, now);
		deltaFileRepo.save(deltaFile4);

		DeltaFile deltaFile5 = buildDeltaFile("5", "flow3", DeltaFileStage.ERROR, now, null);
		deltaFileRepo.save(deltaFile5);

		Set<String> flowSet = new HashSet<>(Arrays.asList("flow1", "flow2", "flow3"));
		Map<String, Integer> errorCountsByFlow = deltaFileRepo.errorCountsByFlow(flowSet);

		assertEquals(3, errorCountsByFlow.size());
		assertEquals(2, errorCountsByFlow.get("flow1").intValue());
		assertEquals(1, errorCountsByFlow.get("flow2").intValue());
		assertEquals(1, errorCountsByFlow.get("flow3").intValue());

		// Test with a non-existing flow in the set
		flowSet.add("flowNotFound");
		errorCountsByFlow = deltaFileRepo.errorCountsByFlow(flowSet);

		assertEquals(3, errorCountsByFlow.size());
		assertNull(errorCountsByFlow.get("flowNotFound"));

		// Test with an empty set
		flowSet.clear();
		errorCountsByFlow = deltaFileRepo.errorCountsByFlow(flowSet);

		assertEquals(0, errorCountsByFlow.size());
	}

	@Test
	void testGetErrorSummaryByFlowFilterAcknowledged() {
		OffsetDateTime now = OffsetDateTime.now();
		OffsetDateTime plusTwo = OffsetDateTime.now().plusMinutes(2);
		loadDeltaFilesWithActionErrors(now, plusTwo);

		ErrorSummaryFilter filterAck = ErrorSummaryFilter.builder()
				.errorAcknowledged(true)
				.flow("f3")
				.build();

		SummaryByFlow resultsAck = deltaFilesService.getErrorSummaryByFlow(
				0, 99, filterAck, null);

		assertEquals(1, resultsAck.count());
		assertEquals(1, resultsAck.countPerFlow().size());
		assertEquals(1, resultsAck.countPerFlow().get(0).getCount());
		assertEquals("f3", resultsAck.countPerFlow().get(0).getFlow());
		assertTrue(resultsAck.countPerFlow().get(0).getDids().contains("6"));

		ErrorSummaryFilter filterNoAck = ErrorSummaryFilter.builder()
				.errorAcknowledged(false)
				.flow("f3")
				.build();

		SummaryByFlow resultsNoAck = deltaFilesService.getErrorSummaryByFlow(
				0, 99, filterNoAck, null);

		assertEquals(1, resultsNoAck.count());
		assertEquals(1, resultsNoAck.countPerFlow().size());
		assertEquals(2, resultsNoAck.countPerFlow().get(0).getCount());
		assertEquals("f3", resultsNoAck.countPerFlow().get(0).getFlow());
		assertTrue(resultsNoAck.countPerFlow().get(0).getDids().containsAll(List.of("7", "8")));

		ErrorSummaryFilter filterFlowOnly = ErrorSummaryFilter.builder()
				.flow("f3")
				.build();

		SummaryByFlow resultsForFlow = deltaFilesService.getErrorSummaryByFlow(
				0, 99, filterFlowOnly, null);

		assertEquals(1, resultsForFlow.count());
		assertEquals(1, resultsForFlow.countPerFlow().size());
		assertEquals(3, resultsForFlow.countPerFlow().get(0).getCount());
		assertEquals("f3", resultsForFlow.countPerFlow().get(0).getFlow());
		assertTrue(resultsForFlow.countPerFlow().get(0).getDids().containsAll(List.of("6", "7", "8")));
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
						.filter(ErrorSummaryFilter.builder().modifiedBefore(plusTwo).build())
						.orderBy(DeltaFileOrder.newBuilder().field("flow").direction(DeltaFileDirection.DESC).build())
						.build(),
				ERRORS_BY_MESSAGE_PROJECTION_ROOT
		);

		SummaryByFlowAndMessage actual = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQLQueryRequest.serialize(),
				"data." + DgsConstants.QUERY.ErrorSummaryByMessage,
				SummaryByFlowAndMessage.class
		);

		assertEquals(5, actual.count());
		assertEquals(0, actual.offset());
		assertEquals(7, actual.totalCount());
		assertEquals(5, actual.countPerMessage().size());
	}

	@Test
	void testGetFilteredSummaryByFlowDatafetcher() {
		OffsetDateTime plusTwo = OffsetDateTime.now().plusMinutes(2);
		loadFilteredDeltaFiles(plusTwo);

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
				new FilteredSummaryByFlowGraphQLQuery.Builder()
						.limit(5)
						.filter(FilteredSummaryFilter.builder().modifiedBefore(plusTwo).build())
						.orderBy(DeltaFileOrder.newBuilder().field("flow").direction(DeltaFileDirection.DESC).build())
						.build(),
				FILTERED_BY_FlOW_PROJECTION_ROOT
		);

		SummaryByFlow actual = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQLQueryRequest.serialize(),
				"data." + DgsConstants.QUERY.FilteredSummaryByFlow,
				SummaryByFlow.class
		);

		assertEquals(1, actual.count());
		assertEquals(0, actual.offset());
		assertEquals(1, actual.totalCount());
		assertEquals(1, actual.countPerFlow().size());
		List<String> expectedDids = List.of("did");
		CountPerFlow message0 = actual.countPerFlow().get(0);
		assertThat(message0.getFlow()).isEqualTo(NORMALIZE_FLOW_NAME);
		assertThat(message0.getDids()).isEqualTo(expectedDids);
	}

	@Test
	void testGetFilteredSummaryByMessageDatafetcher() {
		OffsetDateTime plusTwo = OffsetDateTime.now().plusMinutes(2);
		loadFilteredDeltaFiles(plusTwo);

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
				new FilteredSummaryByMessageGraphQLQuery.Builder()
						.limit(5)
						.filter(FilteredSummaryFilter.builder().modifiedBefore(plusTwo).build())
						.orderBy(DeltaFileOrder.newBuilder().field("flow").direction(DeltaFileDirection.DESC).build())
						.build(),
				FILTERED_BY_MESSAGE_PROJECTION_ROOT
		);

		SummaryByFlowAndMessage actual = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQLQueryRequest.serialize(),
				"data." + DgsConstants.QUERY.FilteredSummaryByMessage,
				SummaryByFlowAndMessage.class
		);

		assertEquals(2, actual.count());
		assertEquals(0, actual.offset());
		assertEquals(2, actual.totalCount());
		assertEquals(2, actual.countPerMessage().size());
		List<String> expectedDids = List.of("did");
		CountPerMessage message0 = actual.countPerMessage().get(0);
		CountPerMessage message1 = actual.countPerMessage().get(1);
		assertThat(message0.getDids()).isEqualTo(expectedDids);
		assertThat(message0.getMessage()).isEqualTo("filtered two");
		assertThat(message1.getDids()).isEqualTo(expectedDids);
		assertThat(message1.getMessage()).isEqualTo("filtered one");
	}

	private void loadFilteredDeltaFiles(OffsetDateTime plusTwo) {
		deltaFileRepo.deleteAll();

		DeltaFile deltaFile = postTransformDeltaFile("did");
		deltaFile.setFiltered(true);
		deltaFile.getActions().add(filteredAction("filtered one"));
		deltaFile.getActions().add(filteredAction("filtered two"));

		DeltaFile tooNew = postTransformDeltaFile("did2");
		tooNew.setFiltered(true);
		tooNew.getSourceInfo().setFlow("other");
		tooNew.setModified(plusTwo);
		tooNew.getActions().add(filteredAction("another message"));

		DeltaFile notMarkedFiltered = postTransformDeltaFile("did3");
		notMarkedFiltered.setFiltered(null);
		notMarkedFiltered.getSourceInfo().setFlow("other");
		notMarkedFiltered.setModified(plusTwo);
		notMarkedFiltered.getActions().add(filteredAction("another message"));

		deltaFileRepo.saveAll(List.of(deltaFile, tooNew, notMarkedFiltered));
	}

	private Action filteredAction(String message) {
		Action action = new Action();
		action.setName("someAction");
		action.setFlow(NORMALIZE_FLOW_NAME);
		action.setFilteredCause(message);
		action.setState(ActionState.FILTERED);
		return action;
	}

	@Test
	void testGetErrorSummaryByMessage() {
		OffsetDateTime now = OffsetDateTime.now();
		OffsetDateTime plusTwo = OffsetDateTime.now().plusMinutes(2);

		loadDeltaFilesWithActionErrors(now, plusTwo);

		SummaryByFlowAndMessage fullSummary = deltaFilesService.getErrorSummaryByMessage(
				0, 99, null, null);

		assertEquals(0, fullSummary.offset());
		assertEquals(7, fullSummary.count());
		assertEquals(7, fullSummary.totalCount());
		assertEquals(7, fullSummary.countPerMessage().size());

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

		SummaryByFlowAndMessage orderByFlow = deltaFilesService.getErrorSummaryByMessage(
				0, 4, null,
				DeltaFileOrder.newBuilder()
						.direction(DeltaFileDirection.ASC)
						.field("Flow").build());

		assertEquals(0, orderByFlow.offset());
		assertEquals(4, orderByFlow.count());
		assertEquals(7, orderByFlow.totalCount());
		assertEquals(4, orderByFlow.countPerMessage().size());

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

		ErrorSummaryFilter filterBefore = ErrorSummaryFilter.builder()
				.modifiedBefore(plusOne).build();

		SummaryByFlowAndMessage resultsBefore = deltaFilesService.getErrorSummaryByMessage(
				0, 99, filterBefore, null);

		assertEquals(0, resultsBefore.offset());
		assertEquals(6, resultsBefore.count());
		assertEquals(6, resultsBefore.totalCount());
		assertEquals(6, resultsBefore.countPerMessage().size());

		// no 'causeA' entry
		matchesCounterPerMessage(resultsBefore, 0, "causeX", "f1", List.of("2"));
		matchesCounterPerMessage(resultsBefore, 1, "causeX", "f2", List.of("1", "3"));
		matchesCounterPerMessage(resultsBefore, 2, "causeY", "f2", List.of("3"));
		matchesCounterPerMessage(resultsBefore, 3, "causeZ", "f1", List.of("9"));
		matchesCounterPerMessage(resultsBefore, 4, "causeZ", "f2", List.of("10"));
		matchesCounterPerMessage(resultsBefore, 5, "causeZ", "f3", List.of("6", "7", "8"));

		ErrorSummaryFilter filterAfter = ErrorSummaryFilter.builder()
				.modifiedAfter(plusOne).build();

		SummaryByFlowAndMessage resultAfter = deltaFilesService.getErrorSummaryByMessage(
				0, 99, filterAfter, null);

		assertEquals(0, resultAfter.offset());
		assertEquals(1, resultAfter.count());
		assertEquals(1, resultAfter.totalCount());
		assertEquals(1, resultAfter.countPerMessage().size());
		matchesCounterPerMessage(resultAfter, 0, "causeA", "f1", List.of("4", "5"));
	}

	@Test
	void testGetErrorSummaryByMessagePaging() {
		OffsetDateTime now = OffsetDateTime.now();
		OffsetDateTime plusTwo = OffsetDateTime.now().plusMinutes(2);

		loadDeltaFilesWithActionErrors(now, plusTwo);

		ErrorSummaryFilter filter = ErrorSummaryFilter.builder()
				.errorAcknowledged(false)
				.flow("f2").build();
		DeltaFileOrder order = DeltaFileOrder.newBuilder()
				.direction(DeltaFileDirection.DESC)
				.field("Count").build();

		SummaryByFlowAndMessage firstPage = deltaFilesService.getErrorSummaryByMessage(
				0, 2, filter, order);

		assertEquals(0, firstPage.offset());
		assertEquals(2, firstPage.count());
		assertEquals(3, firstPage.totalCount());
		assertEquals(2, firstPage.countPerMessage().size());
		matchesCounterPerMessage(firstPage, 0, "causeX", "f2", List.of("1", "3"));
		matchesCounterPerMessage(firstPage, 1, "causeZ", "f2", List.of("10"));

		SummaryByFlowAndMessage pageTwo = deltaFilesService.getErrorSummaryByMessage(
				2, 2, filter, order);

		assertEquals(2, pageTwo.offset());
		assertEquals(1, pageTwo.count());
		assertEquals(3, pageTwo.totalCount());
		assertEquals(1, pageTwo.countPerMessage().size());
		matchesCounterPerMessage(pageTwo, 0, "causeY", "f2", List.of("3"));

		SummaryByFlowAndMessage invalidPage = deltaFilesService.getErrorSummaryByMessage(
				4, 2, filter, order);

		// there was only enough data for two pages
		assertEquals(4, invalidPage.offset());
		assertEquals(0, invalidPage.count());
		assertEquals(3, invalidPage.totalCount());
		assertEquals(0, invalidPage.countPerMessage().size());
	}

	@Test
	void testGetErrorSummaryByMessageNoneFound() {
		OffsetDateTime now = OffsetDateTime.now();
		OffsetDateTime plusTwo = OffsetDateTime.now().plusMinutes(2);

		loadDeltaFilesWithActionErrors(now, plusTwo);

		SummaryByFlowAndMessage noneFound = deltaFilesService.getErrorSummaryByMessage(
				0, 99, ErrorSummaryFilter.builder()
						.flow("flowNotFound").build(), null);

		assertEquals(0, noneFound.offset());
		assertEquals(0, noneFound.count());
		assertEquals(0, noneFound.totalCount());
		assertEquals(0, noneFound.countPerMessage().size());

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
		deltaFileWithAck.acknowledgeError(now, null);
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

	@Test
	void updateProperties() {
		DeltaFiProperties current = deltaFiPropertiesService.getDeltaFiProperties();
		assertThat(current.getSystemName()).isEqualTo("DeltaFi");
		assertThat(current.getSetProperties()).isEmpty();

		assertThat(deltaFiPropertiesRepo.updateProperties(Map.of(PropertyType.SYSTEM_NAME, "newName"))).isTrue();

		deltaFiPropertiesService.refreshProperties();
		current = deltaFiPropertiesService.getDeltaFiProperties();

		assertThat(current.getSystemName()).isEqualTo("newName");
		assertThat(current.getSetProperties()).hasSize(1).contains(PropertyType.SYSTEM_NAME.name());

		// already newName no changes made, return false
		assertThat(deltaFiPropertiesRepo.updateProperties(Map.of(PropertyType.SYSTEM_NAME, "newName"))).isFalse();
	}

	@Test
	void unsetProperties() {
		DeltaFiProperties deltaFiProperties = new DeltaFiProperties();
		deltaFiProperties.setSystemName("newName");
		deltaFiProperties.getSetProperties().add(PropertyType.SYSTEM_NAME.name());

		deltaFiPropertiesRepo.save(deltaFiProperties);
		deltaFiPropertiesService.refreshProperties();

		DeltaFiProperties current = deltaFiPropertiesService.getDeltaFiProperties();
		assertThat(current.getSystemName()).isEqualTo("newName");
		assertThat(current.getSetProperties()).hasSize(1).contains(PropertyType.SYSTEM_NAME.name());


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

	@Test
	void testImportSnapshot() {
		SystemSnapshot snapshot = SystemSnapshotDatafetcherTestHelper.importSystemSnapshot(dgsQueryExecutor);
		assertThat(snapshot).isEqualTo(SystemSnapshotDatafetcherTestHelper.expectedSnapshot());
	}

	@Test
	void restoreSnapshot() {
		systemSnapshotRepo.save(SystemSnapshotDatafetcherTestHelper.expectedSnapshot());
		Result result = SystemSnapshotDatafetcherTestHelper.restoreSnapshot(dgsQueryExecutor);

		// this is not successful b/c it is trying to start flows and put flows in test mode that no longer exist
		assertThat(result.isSuccess()).isFalse();
	}

	@Test
	void setEgressFlowExpectedAnnotations() {
		clearForFlowTests();
		EgressFlow egressFlow = new EgressFlow();
		egressFlow.setName("egress-flow");
		egressFlow.setExpectedAnnotations(Set.of("a", "b"));
		egressFlowRepo.save(egressFlow);

		assertThat(egressFlowRepo.updateExpectedAnnotations("egress-flow", Set.of("b", "a", "c"))).isTrue();
		assertThat(egressFlowRepo.findById("egress-flow").orElseThrow().getExpectedAnnotations()).hasSize(3).containsAll(Set.of("a", "b", "c"));
	}

	@Test
	void testRemoveFlowFromPendingAnnotationsForFlowsList() {
		String keepFlow = "keep";
		String removeFlow = "remove";
		DeltaFile keepOne = buildDeltaFile("keepOne");
		keepOne.setPendingAnnotationsForFlows(Set.of(keepFlow));
		DeltaFile removeOneKeepOne = buildDeltaFile("removeOneKeepOne");
		removeOneKeepOne.setPendingAnnotationsForFlows(Set.of(keepFlow, removeFlow));
		DeltaFile removeBecomesNull = buildDeltaFile("removeBecomesNull");
		removeBecomesNull.setPendingAnnotationsForFlows(Set.of(removeFlow));
		DeltaFile four = buildDeltaFile("noChange");

		deltaFileRepo.saveAll(List.of(keepOne, removeOneKeepOne, removeBecomesNull, four));
		deltaFileRepo.removePendingAnnotationsForFlow(removeFlow);

		assertThat(deltaFileRepo.findById("keepOne").orElseThrow().getPendingAnnotationsForFlows()).hasSize(1).contains(keepFlow);
		assertThat(deltaFileRepo.findById("removeOneKeepOne").orElseThrow().getPendingAnnotationsForFlows()).hasSize(1).contains(keepFlow);
		assertThat(deltaFileRepo.findById("removeBecomesNull").orElseThrow().getPendingAnnotationsForFlows()).isNull();
		assertThat(deltaFileRepo.findById("noChange").orElseThrow().getPendingAnnotationsForFlows()).isNull();
	}

	@Test
	void testUpdatePendingAnnotationsForFlows() {
		Set<String> pendingForFlows = Set.of("f");
		DeltaFile completeAfterChange = buildDeltaFile("a");
		completeAfterChange.setPendingAnnotationsForFlows(pendingForFlows);
		completeAfterChange.addAnnotations(Map.of("a", "value"));

		DeltaFile waitingForA = buildDeltaFile("b");
		waitingForA.setPendingAnnotationsForFlows(pendingForFlows);

		DeltaFile differentFlow = buildDeltaFile("c");
		differentFlow.setPendingAnnotationsForFlows(Set.of("f2"));

		DeltaFile d = buildDeltaFile("d");
		d.setPendingAnnotationsForFlows(pendingForFlows);
		completeAfterChange.addAnnotations(Map.of("d", "value"));

		deltaFileRepo.saveAll(List.of(completeAfterChange, waitingForA, differentFlow, d));
		deltaFilesService.updatePendingAnnotationsForFlows("f", Set.of("a"));

		Util.assertEqualsIgnoringDates(waitingForA, deltaFilesService.getDeltaFile("b"));
		Util.assertEqualsIgnoringDates(differentFlow, deltaFilesService.getDeltaFile("c"));
		Util.assertEqualsIgnoringDates(d, deltaFilesService.getDeltaFile("d"));

		DeltaFile updated = deltaFilesService.getDeltaFile("a");
		assertThat(updated.getPendingAnnotationsForFlows()).isNull();
	}

	private DeltaFile loadDeltaFile(String did) {
		return deltaFileRepo.findById(did).orElse(null);
	}

	private void verifyActionEventResults(DeltaFile expected, ActionContext... forActions) {
		DeltaFile afterMutation = deltaFilesService.getDeltaFile(expected.getDid());
		assertEqualsIgnoringDates(expected, afterMutation);

		Mockito.verify(actionEventQueue).putActions(actionInputListCaptor.capture(), anyBoolean());
		List<ActionInput> actionInputs = actionInputListCaptor.getValue();
		assertThat(actionInputs).hasSize(forActions.length);
		for (int i = 0; i < forActions.length; i++) {
			ActionInput actionInput = actionInputs.get(i);
			assertThat(actionInput.getActionContext().getFlow()).isEqualTo(forActions[i].getFlow());
			assertThat(actionInput.getActionContext().getName()).isEqualTo(forActions[i].getName());
			assertEquals(expected.forQueue(), actionInput.getDeltaFileMessages().get(0));
		}
	}

	void clearForFlowTests() {
		transformFlowRepo.deleteAll();
		transformFlowPlanRepo.deleteAll();
		transformFlowService.refreshCache();
		egressFlowRepo.deleteAll();
		egressFlowPlanRepo.deleteAll();
		egressFlowService.refreshCache();
		timedIngressFlowRepo.deleteAll();
		timedIngressFlowPlanRepo.deleteAll();
		timedIngressFlowService.refreshCache();
		pluginVariableRepo.deleteAll();
	}

	@Test
	void annotations() {
		deltaFileRepo.insert(DeltaFile.builder()
				.annotations(Map.of("x", "1", "y", "2"))
				.annotationKeys(Set.of("x", "y"))
				.build());
		deltaFileRepo.insert(DeltaFile.builder()
				.annotations(Map.of("y", "3", "z", "4"))
				.annotationKeys(Set.of("y", "z"))
				.build());

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(new AnnotationKeysGraphQLQuery());

		List<String> actual = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQLQueryRequest.serialize(),
				"data." + DgsConstants.QUERY.AnnotationKeys,
				new TypeRef<>() {}
		);

		assertEquals(List.of("x", "y", "z"), actual);
	}

	@Test
	void annotationsEmpty() {
		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(new AnnotationKeysGraphQLQuery());

		List<String> actual = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQLQueryRequest.serialize(),
				"data." + DgsConstants.QUERY.AnnotationKeys,
				new TypeRef<>() {}
		);

		assertEquals(Collections.emptyList(), actual);
	}

	private ResponseEntity<String> ingress(String filename, byte[] body) {
		HttpHeaders headers = new HttpHeaders();
		if (filename != null) {
			headers.add("Filename", filename);
		}
		headers.add("Flow", FLOW);
		headers.add("Metadata", METADATA);
		headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM);
		headers.add(USER_HEADER, USERNAME);
		headers.add(DeltaFiConstants.PERMISSIONS_HEADER, DeltaFiConstants.ADMIN_PERMISSION);
		HttpEntity<byte[]> request = new HttpEntity<>(body, headers);

		return restTemplate.postForEntity("/deltafile/ingress", request, String.class);
	}

	DeltaFile ingressedFromAction(String did, String ingressFlow) {
		Content content = new Content("filename", "application/text", new Segment("uuid", 0, 36, did));
		Map<String, String> metadata = Map.of("smoke", "test");
		Action ingressAction = Action.builder()
				.flow(ingressFlow)
				.name("SampleTimedIngressAction")
				.type(ActionType.INGRESS)
				.state(ActionState.COMPLETE)
				.created(OffsetDateTime.now())
				.modified(OffsetDateTime.now())
				.content(List.of(content))
				.metadata(metadata)
				.build();

		Action utf8Action = Action.builder()
				.flow(TRANSFORM_FLOW_NAME)
				.name("Utf8TransformAction")
				.type(ActionType.INGRESS)
				.state(ActionState.QUEUED)
				.created(OffsetDateTime.now())
				.modified(OffsetDateTime.now())
				.build();

		return DeltaFile.builder()
				.schemaVersion(CURRENT_SCHEMA_VERSION)
				.did(did)
				.parentDids(new ArrayList<>())
				.childDids(new ArrayList<>())
				.ingressBytes(36L)
				.sourceInfo(new SourceInfo("filename", NORMALIZE_FLOW_NAME, metadata))
				.stage(DeltaFileStage.IN_FLIGHT)
				.created(OffsetDateTime.now())
				.modified(OffsetDateTime.now())
				.actions(new ArrayList<>(List.of(ingressAction, utf8Action)))
				.egress(new ArrayList<>())
				.egressed(false)
				.filtered(false)
				.build();
	}

	@Test
	@SneakyThrows
	void testIngressFromAction() {
		String did = UUID.randomUUID().toString();

		timedIngressFlowService.setLastRun(TIMED_INGRESS_FLOW_NAME, OffsetDateTime.now(), "taskedDid");
		deltaFilesService.handleActionEvent(actionEvent("ingress", did));

		verifyActionEventResults(ingressedFromAction(did, TIMED_INGRESS_FLOW_NAME),
				ActionContext.builder().flow("sampleNormalize").name("sampleNormalize.Utf8TransformAction").build());

		Map<String, String> tags = tagsFor(ActionEventType.INGRESS, "SampleTimedIngressAction", NORMALIZE_FLOW_NAME, null);
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.FILES_IN, 1).addTags(tags));
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.BYTES_IN, 36).addTags(tags));

		extendTagsForAction(tags, "type");
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.ACTION_EXECUTION_TIME_MS, 1).addTags(tags));

		Mockito.verifyNoMoreInteractions(metricService);
	}

	DeltaFile ingressedFromActionWithError(String did) {
		DeltaFile deltaFile = ingressedFromAction(did, TIMED_INGRESS_ERROR_FLOW_NAME);
		deltaFile.getActions().remove(1);
		Action action = deltaFile.getActions().get(0);
		action.setState(ActionState.ERROR);
		action.setName("SampleTimedIngressErrorAction");
		action.setErrorCause("Flow is not installed: missingFlow");
		deltaFile.setStage(DeltaFileStage.ERROR);
		deltaFile.getSourceInfo().setFlow("missingFlow");
		return deltaFile;
	}

	@Test
	@SneakyThrows
	void testIngressErrorFromAction() {
		String did = UUID.randomUUID().toString();

		timedIngressFlowService.setLastRun(TIMED_INGRESS_ERROR_FLOW_NAME, OffsetDateTime.now(), "taskedDid");
		deltaFilesService.handleActionEvent(actionEvent("ingressError", did));

		DeltaFile actual = deltaFilesService.getDeltaFile(did);
		DeltaFile expected = ingressedFromActionWithError(did);
		assertEqualsIgnoringDates(expected, actual);

		Map<String, String> tags = tagsFor(ActionEventType.INGRESS, "SampleTimedIngressErrorAction", "missingFlow", null);
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.FILES_IN, 1).addTags(tags));
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.BYTES_IN, 36).addTags(tags));
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.FILES_ERRORED, 1).addTags(tags));

		extendTagsForAction(tags, "type");
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.ACTION_EXECUTION_TIME_MS, 1).addTags(tags));

		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	@SneakyThrows
	void testIngress() {
		String did1 = "did1";
		Content content1 = new Content(FILENAME, MEDIA_TYPE, new Segment(FILENAME, 0, CONTENT_DATA.length(), did1));
		String did2 = "did2";
		Content content2 = new Content(FILENAME, MEDIA_TYPE, new Segment(FILENAME, 0, CONTENT_DATA.length(), did2));
		List<IngressResult> ingressResults = List.of(
				new IngressResult(FLOW, did1, content1),
				new IngressResult(FLOW, did2, content2));

		Mockito.when(ingressService.ingress(eq(FLOW), eq(FILENAME), eq(MEDIA_TYPE), eq(USERNAME), eq(METADATA), any(), any()))
				.thenReturn(ingressResults);

		ResponseEntity<String> response = ingress(FILENAME, CONTENT_DATA.getBytes());
		assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());
		assertEquals(String.join(",", did1, did2), response.getBody());
	}

	@Test
	@SneakyThrows
	void testIngress_missingFilename() {
		Mockito.when(ingressService.ingress(eq(FLOW), isNull(), eq(MEDIA_TYPE), eq(USERNAME), eq(METADATA), any(), any()))
				.thenThrow(new IngressMetadataException(""));

		ResponseEntity<String> response = ingress(null, CONTENT_DATA.getBytes());
		assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatusCode().value());
	}

	@Test
	@SneakyThrows
	void testIngress_disabled() {
		Mockito.when(ingressService.ingress(eq(FLOW), eq(FILENAME), eq(MEDIA_TYPE), eq(USERNAME), eq(METADATA), any(), any()))
				.thenThrow(new IngressUnavailableException(""));

		ResponseEntity<String> response = ingress(FILENAME, CONTENT_DATA.getBytes());
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), response.getStatusCode().value());
	}

	@Test
	@SneakyThrows
	void testIngress_storageLimit() {
		Mockito.when(ingressService.ingress(eq(FLOW), eq(FILENAME), eq(MEDIA_TYPE), eq(USERNAME), eq(METADATA), any(), any()))
				.thenThrow(new IngressStorageException(""));

		ResponseEntity<String> response = ingress(FILENAME, CONTENT_DATA.getBytes());
		assertEquals(HttpStatus.INSUFFICIENT_STORAGE.value(), response.getStatusCode().value());
	}

	@Test
	@SneakyThrows
	void testIngress_internalServerError() {
		Mockito.when(ingressService.ingress(eq(FLOW), eq(FILENAME), eq(MEDIA_TYPE), eq(USERNAME), eq(METADATA), any(), any()))
				.thenThrow(new RuntimeException());

		ResponseEntity<String> response = ingress(FILENAME, CONTENT_DATA.getBytes());
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getStatusCode().value());
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

	@Test
	void testSetContentDeletedByDidIn() {
		DeltaFile deltaFile1 = buildDeltaFile("1", null, DeltaFileStage.COMPLETE, MONGO_NOW.minusSeconds(2), MONGO_NOW.minusSeconds(2));
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile("2", null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.plusSeconds(2));
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = buildDeltaFile("3", null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.plusSeconds(2));
		deltaFileRepo.save(deltaFile3);

		List<String> dids = new ArrayList<>();
		dids.add("1");
		dids.add("3");
		Random rand = new Random();
		for (int i = 0; i < 3000; i++) {
			String str = String.valueOf(4 + rand.nextInt(10000));
			dids.add(str);
		}
		Collections.shuffle(dids);

		deltaFileRepo.setContentDeletedByDidIn(dids, MONGO_NOW, "MyPolicy");
		DeltaFiles deltaFiles = deltaFileRepo.deltaFiles(null, 50, new DeltaFilesFilter(), null, null);
		deltaFile1.setContentDeleted(MONGO_NOW);
		deltaFile1.setContentDeletedReason("MyPolicy");
		deltaFile3.setContentDeleted(MONGO_NOW);
		deltaFile3.setContentDeletedReason("MyPolicy");
		assertEquals(List.of(deltaFile3, deltaFile2, deltaFile1), deltaFiles.getDeltaFiles());
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testDeleteMultipleBatches() {
		for (int i = 0; i < 1500; i++) {
			DeltaFile deltaFile = DeltaFile.builder()
					.did("abc" + i)
					.created(OffsetDateTime.now().minusDays(1))
					.totalBytes(10)
					.build();
			deltaFile.updateFlags();
			deltaFileRepo.save(deltaFile);
		}
		assertEquals(1500, deltaFileRepo.count());
		boolean moreToDelete = deltaFilesService.timedDelete(OffsetDateTime.now(), null, 0L, null, "policyName", true);
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.DELETED_FILES, 1000).addTag("policy", "policyName"));
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.DELETED_BYTES, 10000).addTag("policy", "policyName"));
		Mockito.verifyNoMoreInteractions(metricService);
		assertTrue(moreToDelete);

		// ensure that it deleted the first batch
		assertEquals(500, deltaFileRepo.count());
	}

	@Test
	void testDiskSpaceDeleteCompleteAndAcked() {
		DeltaFile error = DeltaFile.builder()
					.did("error")
					.created(OffsetDateTime.now())
					.totalBytes(1)
					.stage(DeltaFileStage.ERROR)
					.build();
		error.updateFlags();

		DeltaFile complete = DeltaFile.builder()
				.did("complete")
				.created(OffsetDateTime.now())
				.totalBytes(2)
				.stage(DeltaFileStage.COMPLETE)
				.build();
		complete.updateFlags();

		DeltaFile errorAcked = DeltaFile.builder()
				.did("errorAcked")
				.created(OffsetDateTime.now())
				.totalBytes(4)
				.stage(DeltaFileStage.ERROR)
				.errorAcknowledged(OffsetDateTime.now())
				.build();
		errorAcked.updateFlags();

		deltaFileRepo.saveAll(List.of(error, complete, errorAcked));
		deltaFilesService.diskSpaceDelete(500, null, "policyName");
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.DELETED_FILES, 2).addTag("policy", "policyName"));
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.DELETED_BYTES, 6).addTag("policy", "policyName"));
		Mockito.verifyNoMoreInteractions(metricService);

		assertEquals(3, deltaFileRepo.count());
	}

	@Test
	void testTimedDeleteContentAlreadyDeleted() {
		DeltaFile complete = DeltaFile.builder()
				.did("complete")
				.created(OffsetDateTime.now())
				.totalBytes(1)
				.stage(DeltaFileStage.COMPLETE)
				.schemaVersion(CURRENT_SCHEMA_VERSION)
				.build();
		complete.updateFlags();

		DeltaFile contentDeleted = DeltaFile.builder()
				.did("contentDeleted")
				.created(OffsetDateTime.now())
				.totalBytes(2)
				.stage(DeltaFileStage.COMPLETE)
				.contentDeleted(OffsetDateTime.now())
				.schemaVersion(CURRENT_SCHEMA_VERSION)
				.build();
		contentDeleted.updateFlags();

		deltaFileRepo.saveAll(List.of(complete, contentDeleted));
		boolean moreToDelete = deltaFilesService.timedDelete(OffsetDateTime.now().plusSeconds(5), null, 0L, null, "policyName", true);
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.DELETED_FILES, 2).addTag("policy", "policyName"));
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.DELETED_BYTES, 1).addTag("policy", "policyName"));
		Mockito.verifyNoMoreInteractions(metricService);
		assertFalse(moreToDelete);

		assertEquals(0, deltaFileRepo.count());
	}

	@Test
	void testDeltaFileStats() {
		DeltaFileStats none = deltaFilesService.deltaFileStats();
		assertEquals(0, none.getTotalCount());
		assertEquals(0L, none.getInFlightCount());
		assertEquals(0L, none.getInFlightBytes());

		DeltaFile deltaFile1 = Util.emptyDeltaFile("1", "flow", List.of());
		deltaFile1.setTotalBytes(1L);
		deltaFile1.setReferencedBytes(2L);
		deltaFile1.setStage(DeltaFileStage.IN_FLIGHT);
		deltaFile1.setInFlight(true);

		DeltaFile deltaFile2 = Util.emptyDeltaFile("2", "flow", List.of());
		deltaFile2.setTotalBytes(2L);
		deltaFile2.setReferencedBytes(4L);
		deltaFile2.setContentDeleted(OffsetDateTime.now());
		deltaFile2.setStage(DeltaFileStage.IN_FLIGHT);
		deltaFile2.setInFlight(true);

		DeltaFile deltaFile3 = Util.emptyDeltaFile("3", "flow", List.of());
		deltaFile3.setTotalBytes(4L);
		deltaFile3.setReferencedBytes(8L);
		deltaFile3.setStage(DeltaFileStage.COMPLETE);
		deltaFile3.setInFlight(false);

		deltaFileRepo.saveAll(List.of(deltaFile1, deltaFile2, deltaFile3));

		DeltaFileStats all = deltaFilesService.deltaFileStats();
		assertEquals(3, all.getTotalCount());
		assertEquals(6L, all.getInFlightBytes());
		assertEquals(2L, all.getInFlightCount());
	}

	@Test
	void testTransformFlowTransformUtf8() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(transformFlowPostIngressDeltaFile(did));

		deltaFilesService.handleActionEvent(actionEvent("transformFlowTransformUtf8", did));

		verifyActionEventResults(transformFlowPostTransformUtf8DeltaFile(did),
				ActionContext.builder().flow("sampleTransform").name("sampleTransform.SampleTransformAction").build());

		verifyCommonMetrics(ActionEventType.TRANSFORM, "Utf8TransformAction", TRANSFORM_FLOW_NAME, null, "type");
	}

	@Test
	void testTransformFlowTransform() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(transformFlowPostTransformUtf8DeltaFile(did));

		deltaFilesService.handleActionEvent(actionEvent("transformFlowTransform", did));

		verifyActionEventResults(transformFlowPostTransformDeltaFile(did),
				ActionContext.builder().flow("sampleTransform").name("sampleTransform.SampleEgressAction").build());

		verifyCommonMetrics(ActionEventType.TRANSFORM, "SampleTransformAction", TRANSFORM_FLOW_NAME, null, "type");
	}

	@Test
	void testTransformFlowEgress() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(transformFlowPostTransformDeltaFile(did));

		deltaFilesService.handleActionEvent(actionEvent("transformFlowEgress", did));

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertEqualsIgnoringDates(transformFlowPostEgressDeltaFile(did), deltaFile);

		Mockito.verify(actionEventQueue, never()).putActions(any(), anyBoolean());
		Map<String, String> tags = tagsFor(ActionEventType.EGRESS, "SampleEgressAction", TRANSFORM_FLOW_NAME, TRANSFORM_FLOW_NAME);
		Map<String, String> classTags = tagsFor(ActionEventType.EGRESS, "SampleEgressAction", TRANSFORM_FLOW_NAME, TRANSFORM_FLOW_NAME);
		classTags.put(DeltaFiConstants.CLASS, "type");

		Mockito.verify(metricService, Mockito.atLeast(5)).increment(metricCaptor.capture());
		List<Metric> metrics = metricCaptor.getAllValues();
		MatcherAssert.assertThat(
				metrics.stream().map(Metric::getName).collect(Collectors.toList()),
				Matchers.containsInAnyOrder(
						DeltaFiConstants.FILES_IN,
						DeltaFiConstants.FILES_OUT,
						DeltaFiConstants.BYTES_OUT,
						DeltaFiConstants.EXECUTION_TIME_MS,
						DeltaFiConstants.ACTION_EXECUTION_TIME_MS
				));
		for (Metric metric : metrics) {
			switch (metric.getName()) {
				case DeltaFiConstants.FILES_IN -> assertEquals(new Metric(DeltaFiConstants.FILES_IN, 1).addTags(tags), metric);
				case DeltaFiConstants.FILES_OUT -> assertEquals(new Metric(DeltaFiConstants.FILES_OUT, 1).addTag("destination", "final").addTags(tags), metric);
				case DeltaFiConstants.BYTES_OUT -> assertEquals(new Metric(DeltaFiConstants.BYTES_OUT, 42).addTag("destination", "final").addTags(tags), metric);
				case DeltaFiConstants.EXECUTION_TIME_MS ->
					// Dont care about value...
						assertEquals(new Metric(DeltaFiConstants.EXECUTION_TIME_MS, metric.getValue()).addTags(tags), metric);
				case DeltaFiConstants.ACTION_EXECUTION_TIME_MS ->
					// Dont care about value...
						assertEquals(new Metric(DeltaFiConstants.ACTION_EXECUTION_TIME_MS, metric.getValue()).addTags(classTags), metric);
			}
		}
	}

	@Test
	void testTransformFlowMultipleEgress() throws IOException {
		String did = UUID.randomUUID().toString();
		DeltaFile postUtf8Transform = transformFlowPostTransformUtf8DeltaFile(did);
		deltaFileRepo.save(postUtf8Transform);

		deltaFilesService.handleActionEvent(actionEvent("transformFlowTransformMultiple", did));

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertEquals(DeltaFileStage.COMPLETE, deltaFile.getStage());
		assertEquals(2, deltaFile.getChildDids().size());
		assertEquals(ActionState.REINJECTED, deltaFile.getActions().get(deltaFile.getActions().size()-1).getState());

		List<DeltaFile> children = deltaFilesService.deltaFiles(0, 50, DeltaFilesFilter.newBuilder().dids(deltaFile.getChildDids()).build(), DeltaFileOrder.newBuilder().field("created").direction(DeltaFileDirection.ASC).build()).getDeltaFiles();
		assertEquals(2, children.size());

		DeltaFile child1 = children.get(0);
		assertEquals(DeltaFileStage.IN_FLIGHT, child1.getStage());
		assertFalse(child1.getTestMode());
		assertEquals(Collections.singletonList(deltaFile.getDid()), child1.getParentDids());
		assertEquals("input.txt", child1.getSourceInfo().getFilename());
		assertEquals(0, child1.lastCompleteAction().getContent().get(0).getSegments().get(0).getOffset());

		DeltaFile child2 = children.get(1);
		assertEquals(DeltaFileStage.IN_FLIGHT, child2.getStage());
		assertFalse(child2.getTestMode());
		assertEquals(Collections.singletonList(deltaFile.getDid()), child2.getParentDids());
		assertEquals("input.txt", child2.getSourceInfo().getFilename());
		assertEquals(250, child2.lastCompleteAction().getContent().get(0).getSegments().get(0).getOffset());

		Mockito.verify(actionEventQueue).putActions(actionInputListCaptor.capture(), anyBoolean());
		assertEquals(2, actionInputListCaptor.getValue().size());
		assertEquals(child1.getDid(), actionInputListCaptor.getValue().get(0).getActionContext().getDid());
		assertEquals(child2.getDid(), actionInputListCaptor.getValue().get(1).getActionContext().getDid());

		verifyCommonMetrics(ActionEventType.TRANSFORM, "SampleTransformAction", TRANSFORM_FLOW_NAME, null, "type");
	}

	@Test
	void testCountUnacknowledgedErrors() {
		List<DeltaFile> deltaFiles = new ArrayList<>();
		for (int i = 0; i < 50005; i++) {
			DeltaFile deltaFile = Util.buildDeltaFile(Integer.toString(i), List.of());
			deltaFile.setStage(DeltaFileStage.ERROR);

			if (i >= 50001) {
				deltaFile.acknowledgeError(OffsetDateTime.now(), "acked");
			}
			deltaFiles.add(deltaFile);
		}
		deltaFileRepo.saveAll(deltaFiles);

		assertEquals(50005, deltaFileRepo.count());
		assertEquals(50001, deltaFilesService.countUnacknowledgedErrors());
	}

	@Test
	void queuesCollectingTransformActionOnMaxNum() {
		String transformFlowName = "multi-transform";
		TransformFlow transformFlow = new TransformFlow();
		transformFlow.setName(transformFlowName);
		TransformActionConfiguration transformAction = new TransformActionConfiguration("CollectingTransformAction",
				"org.deltafi.action.SomeCollectingTransformAction");
		transformAction.setCollect(new CollectConfiguration(Duration.parse("PT1H"), null, 2, null));
		transformFlow.getTransformActions().add(transformAction);
		transformFlow.getFlowStatus().setState(FlowState.RUNNING);
		transformFlowRepo.insert(transformFlow);
		transformFlowService.refreshCache();

		IngressEventItem ingress1 = new IngressEventItem(UUID.randomUUID().toString(), FILENAME, transformFlowName, null,
				Collections.emptyList());
		deltaFilesService.ingress(ingress1, OffsetDateTime.now(), OffsetDateTime.now());

		IngressEventItem ingress2 = new IngressEventItem(UUID.randomUUID().toString(), "file-2", transformFlowName, null,
				Collections.emptyList());
		deltaFilesService.ingress(ingress2, OffsetDateTime.now(), OffsetDateTime.now());

		Mockito.verify(actionEventQueue).putActions(actionInputListCaptor.capture(), Mockito.anyBoolean());
		verifyActionInputs(actionInputListCaptor.getValue(), ingress1.getDid(), ingress2.getDid(), transformFlowName,
				transformAction.getName());
	}

	private void verifyActionInputs(List<ActionInput> actionInputs, String did1, String did2, String actionFlow,
			String actionName) {
		assertThat(actionInputs).hasSize(1);

		DeltaFile parent1 = deltaFileRepo.findById(did1).orElseThrow();
		Action action = parent1.actionNamed(actionFlow, actionName).orElseThrow();
		assertEquals(ActionState.COLLECTING, action.getState());

		DeltaFile parent2 = deltaFileRepo.findById(did2).orElseThrow();
		action = parent2.actionNamed(actionFlow, actionName).orElseThrow();
		assertEquals(ActionState.COLLECTING, action.getState());

		ActionInput actionInput = actionInputs.get(0);
		assertEquals(2, actionInput.getDeltaFileMessages().size());
		assertEquals(parent1.forQueue(), actionInput.getDeltaFileMessages().get(0));
		assertEquals(parent2.forQueue(), actionInput.getDeltaFileMessages().get(1));
	}

	@Test
	void queuesCollectingTransformActionOnTimeout() {
		String transformFlowName = "multi-transform";
		TransformFlow transformFlow = new TransformFlow();
		transformFlow.setName(transformFlowName);
		TransformActionConfiguration transformAction = new TransformActionConfiguration("CollectingTransformAction",
				"org.deltafi.action.SomeCollectingTransformAction");
		transformAction.setCollect(new CollectConfiguration(Duration.parse("PT3S"), null, 5, null));
		transformFlow.getTransformActions().add(transformAction);
		transformFlow.getFlowStatus().setState(FlowState.RUNNING);
		transformFlowRepo.insert(transformFlow);
		transformFlowService.refreshCache();

		IngressEventItem ingress1 = new IngressEventItem(UUID.randomUUID().toString(), FILENAME, transformFlowName, null,
				Collections.emptyList());
		deltaFilesService.ingress(ingress1, OffsetDateTime.now(), OffsetDateTime.now());

		IngressEventItem ingress2 = new IngressEventItem(UUID.randomUUID().toString(), "file-2", transformFlowName, null,
				Collections.emptyList());
		deltaFilesService.ingress(ingress2, OffsetDateTime.now(), OffsetDateTime.now());

		Mockito.verify(actionEventQueue, Mockito.timeout(5000))
				.putActions(actionInputListCaptor.capture(), Mockito.anyBoolean());
		verifyActionInputs(actionInputListCaptor.getValue(), ingress1.getDid(), ingress2.getDid(), transformFlowName,
				transformAction.getName());
	}

	@Test
	void queuesCollectingTransformActionOnMaxNumGrouping() {
		String transformFlowName = "multi-transform";
		TransformFlow transformFlow = new TransformFlow();
		transformFlow.setName(transformFlowName);
		TransformActionConfiguration transformAction = new TransformActionConfiguration("CollectingTransformAction",
				"org.deltafi.action.SomeCollectingTransformAction");
		transformAction.setCollect(new CollectConfiguration(Duration.parse("PT1H"), null, 2, "a"));
		transformFlow.getTransformActions().add(transformAction);
		transformFlow.getFlowStatus().setState(FlowState.RUNNING);
		transformFlowRepo.insert(transformFlow);
		transformFlowService.refreshCache();

		IngressEventItem ingress1 = new IngressEventItem(UUID.randomUUID().toString(),
				FILENAME, transformFlowName, Map.of("a", "1"), Collections.emptyList());
		deltaFilesService.ingress(ingress1, OffsetDateTime.now(), OffsetDateTime.now());

		IngressEventItem ingress2 = new IngressEventItem(UUID.randomUUID().toString(),
				"file-2", transformFlowName, Map.of("a", "2"), Collections.emptyList());
		deltaFilesService.ingress(ingress2, OffsetDateTime.now(), OffsetDateTime.now());

		IngressEventItem ingress3 = new IngressEventItem(UUID.randomUUID().toString(),
				"file-3", transformFlowName, Map.of("a", "2"), Collections.emptyList());
		deltaFilesService.ingress(ingress3, OffsetDateTime.now(), OffsetDateTime.now());

		IngressEventItem ingress4 = new IngressEventItem(UUID.randomUUID().toString(),
				"file-4", transformFlowName, Map.of("a", "1"), Collections.emptyList());
		deltaFilesService.ingress(ingress4, OffsetDateTime.now(), OffsetDateTime.now());

		Mockito.verify(actionEventQueue, Mockito.times(2))
				.putActions(actionInputListCaptor.capture(), Mockito.anyBoolean());
		List<List<ActionInput>> actionInputLists = actionInputListCaptor.getAllValues();
		verifyActionInputs(actionInputLists.get(0), ingress2.getDid(), ingress3.getDid(), transformFlowName, transformAction.getName());
		verifyActionInputs(actionInputLists.get(1), ingress1.getDid(), ingress4.getDid(), transformFlowName, transformAction.getName());
	}

	@Test
	void failsCollectingTransformActionOnMinNum() {
		String transformFlowName = "multi-transform";
		TransformFlow transformFlow = new TransformFlow();
		transformFlow.setName(transformFlowName);
		TransformActionConfiguration transformAction = new TransformActionConfiguration("CollectingTransformAction",
				"org.deltafi.action.SomeCollectingTransformAction");
		transformAction.setCollect(new CollectConfiguration(Duration.parse("PT3S"), 3, 5, null));
		transformFlow.getTransformActions().add(transformAction);
		transformFlow.getFlowStatus().setState(FlowState.RUNNING);
		transformFlowRepo.insert(transformFlow);
		transformFlowService.refreshCache();

		IngressEventItem ingress1 = new IngressEventItem(UUID.randomUUID().toString(), FILENAME, transformFlowName, null,
				Collections.emptyList());
		deltaFilesService.ingress(ingress1, OffsetDateTime.now(), OffsetDateTime.now());

		IngressEventItem ingress2 = new IngressEventItem(UUID.randomUUID().toString(), "file-2", transformFlowName, null,
				Collections.emptyList());
		deltaFilesService.ingress(ingress2, OffsetDateTime.now(), OffsetDateTime.now());

		await().atMost(5, TimeUnit.SECONDS).until(() -> hasErroredAction(ingress1.getDid(), transformFlowName, transformAction.getName()));
		await().atMost(5, TimeUnit.SECONDS).until(() -> hasErroredAction(ingress2.getDid(), transformFlowName, transformAction.getName()));
	}

	private boolean hasErroredAction(String did, String actionFlow, String actionName) {
		DeltaFile deltaFile = deltaFileRepo.findById(did).orElseThrow();
		Action action = deltaFile.actionNamed(actionFlow, actionName).orElseThrow();
		return action.getState() == ActionState.ERROR;
	}

	@Test
	void testResumeAggregate() throws IOException {
		String transformFlowName = "multi-transform";
		TransformFlow transformFlow = new TransformFlow();
		transformFlow.setName(transformFlowName);
		TransformActionConfiguration transformAction = new TransformActionConfiguration("CollectingTransformAction",
				"org.deltafi.action.SomeCollectingTransformAction");
		transformAction.setCollect(new CollectConfiguration(Duration.parse("PT1H"), null, 2, null));
		transformFlow.getTransformActions().add(transformAction);
		transformFlow.getFlowStatus().setState(FlowState.RUNNING);
		transformFlowRepo.insert(transformFlow);
		transformFlowService.refreshCache();

		DeltaFile parent1 = Util.emptyDeltaFile("1", transformFlow.getName());
		deltaFileRepo.save(parent1);
		DeltaFile parent2 = Util.emptyDeltaFile("2", transformFlow.getName());
		deltaFileRepo.save(parent2);

		String did = UUID.randomUUID().toString();
		List<String> parentDids = List.of(parent1.getDid(), parent2.getDid());
		DeltaFile aggregate = Util.emptyDeltaFile(did, transformFlow.getName());
		aggregate.setAggregate(true);
		aggregate.setParentDids(parentDids);
		aggregate.setStage(DeltaFileStage.ERROR);
		aggregate.queueNewAction(transformFlowName, transformAction.getName(), transformAction.getActionType(), false);
		aggregate.errorAction(transformFlowName, transformAction.getName(), START_TIME, STOP_TIME, "collect action failed", "message");
		deltaFileRepo.save(aggregate);

		List<RetryResult> retryResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("resumeAggregate"), did),
				"data." + DgsConstants.MUTATION.Resume,
				new TypeRef<>() {});

		assertEquals(1, retryResults.size());
		assertEquals(did, retryResults.get(0).getDid());
		assertTrue(retryResults.get(0).getSuccess());

		Mockito.verify(actionEventQueue).putActions(actionInputListCaptor.capture(), Mockito.anyBoolean());
		List<ActionInput> actionInputs = actionInputListCaptor.getValue();
		assertThat(actionInputs).hasSize(1);

		assertEquals(parentDids, actionInputs.get(0).getActionContext().getCollectedDids());
		assertEquals(parent1.forQueue(), actionInputs.get(0).getDeltaFileMessages().get(0));
		assertEquals(parent2.forQueue(), actionInputs.get(0).getDeltaFileMessages().get(1));
	}
}
