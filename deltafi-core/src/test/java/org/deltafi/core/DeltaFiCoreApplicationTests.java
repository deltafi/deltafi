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
import org.deltafi.common.types.FlowType;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.datafetchers.FlowPlanDatafetcherTestHelper;
import org.deltafi.core.datafetchers.PropertiesDatafetcherTestHelper;
import org.deltafi.core.datafetchers.ResumePolicyDatafetcherTestHelper;
import org.deltafi.core.delete.DeletePolicyWorker;
import org.deltafi.core.delete.DeleteRunner;
import org.deltafi.core.exceptions.IngressMetadataException;
import org.deltafi.core.exceptions.IngressStorageException;
import org.deltafi.core.exceptions.IngressUnavailableException;
import org.deltafi.core.exceptions.InvalidActionEventException;
import org.deltafi.core.generated.DgsConstants;
import org.deltafi.core.generated.client.*;
import org.deltafi.core.generated.types.ConfigType;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.integration.IntegrationDataFetcherTestHelper;
import org.deltafi.core.integration.TestResultRepo;
import org.deltafi.core.metrics.MetricService;
import org.deltafi.core.plugin.PluginRepository;
import org.deltafi.core.plugin.SystemPluginService;
import org.deltafi.core.plugin.deployer.DeployerService;
import org.deltafi.core.plugin.deployer.credential.CredentialProvider;
import org.deltafi.core.plugin.deployer.image.PluginImageRepository;
import org.deltafi.core.plugin.deployer.image.PluginImageRepositoryRepo;
import org.deltafi.core.repo.*;
import org.deltafi.core.services.*;
import org.deltafi.core.services.analytics.AnalyticEventService;
import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.snapshot.SystemSnapshotDatafetcherTestHelper;
import org.deltafi.core.snapshot.SystemSnapshotRepo;
import org.deltafi.core.types.PluginVariables;
import org.deltafi.core.types.ResumePolicy;
import org.deltafi.core.types.*;
import org.deltafi.core.types.DataSource;
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
import java.time.Clock;
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
import static org.deltafi.common.constant.DeltaFiConstants.*;
import static org.deltafi.common.test.TestConstants.MONGODB_CONTAINER;
import static org.deltafi.common.types.ActionState.*;
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
		"schedule.propertySync=false",
		"cold.queue.refresh.duration=PT1H"})
@Testcontainers
class DeltaFiCoreApplicationTests {

	@Container
	public static final MongoDBContainer MONGO_DB_CONTAINER = new MongoDBContainer(MONGODB_CONTAINER);
	public static final String SAMPLE_EGRESS_ACTION = "SampleEgressAction";
	public static final String COLLECTING_TRANSFORM_ACTION = "CollectingTransformAction";
	public static final String COLLECT_TOPIC = "collect-topic";

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
	DataSourceService dataSourceService;

	@Autowired
	TransformFlowService transformFlowService;

	@Autowired
	EgressFlowService egressFlowService;

	@Autowired
	TransformFlowRepo transformFlowRepo;

	@Autowired
	DataSourceRepo dataSourceRepo;

	@Autowired
	DataSourcePlanRepo dataSourcePlanRepo;

	@Autowired
	EgressFlowRepo egressFlowRepo;

	@Autowired
	TransformFlowPlanRepo transformFlowPlanRepo;

	@Autowired
	EgressFlowPlanRepo egressFlowPlanRepo;

	@Autowired
	TestResultRepo testResultRepo;

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

    @Autowired
    private Clock clock;

    @MockBean
    AnalyticEventService analyticEventService;

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
		dataSourceService.refreshCache();
	}

	void loadConfig() {
		loadTransformConfig();
		loadEgressConfig();
		loadDataSources();
	}

	void loadTransformConfig() {
		transformFlowRepo.deleteAll();

		TransformFlow sampleTransformFlow = buildRunningTransformFlow(TRANSFORM_FLOW_NAME, TRANSFORM_ACTIONS, false);
		sampleTransformFlow.setSubscribe(Set.of(new Rule(TRANSFORM_TOPIC)));
		sampleTransformFlow.setPublish(publishRules(EGRESS_TOPIC));
		TransformFlow retryFlow = buildRunningTransformFlow("theTransformFlow", null, false);
		TransformFlow childFlow = buildRunningTransformFlow("transformChildFlow", List.of(TRANSFORM2), false);

		transformFlowRepo.saveAll(List.of(sampleTransformFlow, retryFlow, childFlow));
		refreshFlowCaches();
	}

	void loadEgressConfig() {
		egressFlowRepo.deleteAll();

		EgressFlow sampleEgressFlow = buildRunningEgressFlow(EGRESS_FLOW_NAME, EGRESS, false);
		sampleEgressFlow.setSubscribe(Set.of(new Rule(EGRESS_TOPIC)));

		EgressActionConfiguration errorEgress = new EgressActionConfiguration("ErrorEgressAction", "type");
		EgressFlow errorFlow = buildRunningEgressFlow("error", errorEgress, false);

		egressFlowRepo.saveAll(List.of(sampleEgressFlow, errorFlow));
		egressFlowService.refreshCache();
	}

	void loadDataSources() {
		dataSourceRepo.deleteAll();
		dataSourceRepo.save(buildRestDataSource(FlowState.RUNNING));
		dataSourceRepo.save(buildTimedDataSource(FlowState.RUNNING));
		dataSourceRepo.save(buildTimedDataSourceError(FlowState.RUNNING));
		dataSourceService.refreshCache();
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
		UUID id = getIdByPolicyName(AFTER_COMPLETE_POLICY);
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

	private UUID getIdByPolicyName(String name) {
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
		UUID idToUpdate = getIdByPolicyName(DISK_SPACE_PERCENT_POLICY);

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

		UUID notFoundUUID = UUID.randomUUID();
		Result notFoundError = updateDiskSpaceDeletePolicy(dgsQueryExecutor,
				DiskSpaceDeletePolicy.builder()
						.id(notFoundUUID)
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
		UUID secondId = getIdByPolicyName(DISK_SPACE_PERCENT_POLICY);
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
		UUID idToUpdate = getIdByPolicyName(DISK_SPACE_PERCENT_POLICY);

		Result validationError = updateTimedDeletePolicy(dgsQueryExecutor,
				TimedDeletePolicy.builder()
						.id(idToUpdate)
						.name("blah")
						.afterComplete("ABC")
						.enabled(false)
						.build());
		checkUpdateResult(true, validationError, "Unable to parse duration for afterComplete", idToUpdate, DISK_SPACE_PERCENT_POLICY, true);

		UUID wrongUUID = UUID.randomUUID();
		Result notFoundError = updateTimedDeletePolicy(dgsQueryExecutor,
				TimedDeletePolicy.builder()
						.id(wrongUUID)
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

	private void checkUpdateResult(boolean disk, Result result, String error, UUID id, String name, boolean enabled) {
		if (error == null) {
			assertTrue(result.isSuccess());
		} else {
			assertFalse(result.isSuccess());
			assertTrue(result.getErrors().contains(error));
		}

		assertEquals(1, deletePolicyRepo.count());
		List<DeletePolicy> policyList = getDeletePolicies(dgsQueryExecutor);
		assertEquals(1, policyList.size());
		assertEquals(id, policyList.getFirst().getId());
		assertEquals(name, policyList.getFirst().getName());
		assertEquals(enabled, policyList.getFirst().isEnabled());

		if (disk) {
            assertInstanceOf(DiskSpaceDeletePolicy.class, policyList.getFirst());
		} else {
            assertInstanceOf(TimedDeletePolicy.class, policyList.getFirst());
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
		Set<UUID> ids = new HashSet<>();

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
		List<String> names = List.of(policiesScheduled.getFirst().getName(),
				policiesScheduled.get(1).getName());
		assertTrue(names.containsAll(List.of(DISK_SPACE_PERCENT_POLICY, AFTER_COMPLETE_POLICY)));
	}

	@Test
	void deletePoliciesDontRaise() {
		deleteRunner.runDeletes();
	}

	@SuppressWarnings("SameParameterValue")
	private void verifyCommonMetrics(ActionEventType actionEventType,
									 String actionName,
									 String dataSource,
									 String egressFlow,
									 String className) {
		Map<String, String> tags = tagsFor(actionEventType, actionName, dataSource, egressFlow);
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.FILES_IN, 1).addTags(tags));
		extendTagsForAction(tags, className);
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.ACTION_EXECUTION_TIME_MS, 1).addTags(tags));
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testResumeTransform() throws IOException {
		UUID did = UUID.randomUUID();
		deltaFileRepo.save(postTransformHadErrorDeltaFile(did));

		List<RetryResult> retryResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("resumeTransform"), did),
				"data." + DgsConstants.MUTATION.Resume,
				new TypeRef<>() {});

		assertEquals(1, retryResults.size());
		assertEquals(did, retryResults.getFirst().getDid());
		assertTrue(retryResults.getFirst().getSuccess());

		DeltaFile expected = postResumeTransformDeltaFile(did);
		verifyActionEventResults(expected, ActionContext.builder().flowName(TRANSFORM_FLOW_NAME).actionName("SampleTransformAction").build());

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		deltaFile.getFlows().get(1).lastAction().error(OffsetDateTime.now(), OffsetDateTime.now(), OffsetDateTime.now(), "cause", "context");
		deltaFileRepo.save(deltaFile);

		List<PerActionUniqueKeyValues> errorMetadataUnion = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				"query getMetadata { errorMetadataUnion (dids: [\"" + did + "\"]) { action keyVals { key values } } }",
				"data." + DgsConstants.QUERY.ErrorMetadataUnion,
				new TypeRef<>() {});

		assertEquals(1, errorMetadataUnion.size());
		assertEquals("SampleTransformAction", errorMetadataUnion.getFirst().getAction());
		assertEquals(List.of("AuthorizedBy", "anotherKey"), errorMetadataUnion.getFirst().getKeyVals().stream().map(UniqueKeyValues::getKey).sorted().toList());

		Mockito.verifyNoInteractions(metricService);
	}

	@Test
	void testResumeContentDeleted() throws IOException {
		UUID did = UUID.randomUUID();
		DeltaFile contentDeletedDeltaFile = postTransformHadErrorDeltaFile(did);
		contentDeletedDeltaFile.setContentDeleted(OffsetDateTime.now());
		contentDeletedDeltaFile.setContentDeletedReason("aged off");
		deltaFileRepo.save(contentDeletedDeltaFile);

		List<RetryResult> retryResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("resumeTransform"), did),
				"data." + DgsConstants.MUTATION.Resume,
				new TypeRef<>() {});

		assertEquals(1, retryResults.size());
		assertEquals(did, retryResults.getFirst().getDid());
		assertFalse(retryResults.getFirst().getSuccess());

		Mockito.verifyNoInteractions(metricService);
	}

	@Test
	void testTransformToColdQueue() throws IOException {
		UUID did = UUID.randomUUID();
		DeltaFile postTransformUtf8 = postTransformUtf8DeltaFile(did);
		deltaFileRepo.save(postTransformUtf8);
		queueManagementService.getColdQueues().put(SAMPLE_EGRESS_ACTION, 999999L);

		deltaFilesService.handleActionEvent(actionEvent("transform", did));

		DeltaFile postTransformColdQueued = postTransformDeltaFile(did);
		postTransformColdQueued.getFlows().get(2).lastAction().setState(ActionState.COLD_QUEUED);
		verifyActionEventResults(postTransformColdQueued,
				ActionContext.builder().flowName(EGRESS_FLOW_NAME).actionName(SAMPLE_EGRESS_ACTION).build());

		verifyCommonMetrics(ActionEventType.TRANSFORM, "SampleTransformAction", REST_DATA_SOURCE_NAME, null, "type");

		queueManagementService.getColdQueues().remove(SAMPLE_EGRESS_ACTION);
	}

	@Test
	void testColdRequeue() {
		UUID did = UUID.randomUUID();
		DeltaFile postTransform = postTransformDeltaFile(did);
		DeltaFileFlow egressFlow = postTransform.getFlows().get(2);
		egressFlow.lastAction().setState(ActionState.COLD_QUEUED);
		deltaFileRepo.save(postTransform);

		queueManagementService.coldToWarm();

		verifyActionEventResults(postTransformDeltaFile(did), ActionContext.builder().flowName(EGRESS_FLOW_NAME).actionName(SAMPLE_EGRESS_ACTION).build());
	}

	@Test
	void testTransformDidNotFound() throws IOException {
		UUID did = UUID.randomUUID();
		deltaFileRepo.save(postTransformUtf8DeltaFile(did));
		UUID wrongDid = UUID.randomUUID();

		ActionEvent event = actionEvent("transformDidNotFound", wrongDid);
		org.assertj.core.api.Assertions.assertThatThrownBy(
						() -> deltaFilesService.handleActionEvent(event))
				.isInstanceOf(InvalidActionEventException.class)
				.hasMessageContaining("Invalid ActionEvent: DeltaFile %s not found.".formatted(wrongDid));
	}

	@Test
	void testTransformWithUnicodeAnnotation() throws IOException {
		UUID did = UUID.randomUUID();
		deltaFileRepo.save(postTransformUtf8DeltaFile(did));

		deltaFilesService.handleActionEvent(actionEvent("transformUnicode", did));

		verifyActionEventResults(postTransformDeltaFileWithUnicodeAnnotation(did), ActionContext.builder().flowName(EGRESS_FLOW_NAME).actionName(SAMPLE_EGRESS_ACTION).build());
		verifyCommonMetrics(ActionEventType.TRANSFORM, "SampleTransformAction", REST_DATA_SOURCE_NAME, null, "type");
	}

	@Test
	void testTransformMissingAction() throws IOException {
		ActionEvent actionEvent = actionEvent("transformMissingAction", UUID.randomUUID());
		org.assertj.core.api.Assertions.assertThatThrownBy(
						() -> deltaFilesService.validateActionEventHeader(actionEvent))
				.isInstanceOf(InvalidActionEventException.class)
				.hasMessageContaining("Invalid ActionEvent: Missing action");
	}

	@Test
	void testTransformMissingDid() throws IOException {
		ActionEvent actionEvent = actionEvent("transformMissingDid", UUID.randomUUID());
		org.assertj.core.api.Assertions.assertThatThrownBy(
						() -> deltaFilesService.validateActionEventHeader(actionEvent))
				.isInstanceOf(InvalidActionEventException.class)
				.hasMessageContaining("Invalid ActionEvent: Missing did");
	}

	@Test
	void testTransformWrongElement() throws IOException {
		UUID did = UUID.randomUUID();
		deltaFileRepo.save(postTransformUtf8DeltaFile(did));

		deltaFilesService.handleActionEvent(actionEvent("transformWrongElement", did));

		DeltaFile afterMutation = deltaFilesService.getDeltaFile(did);
		assertEqualsIgnoringDates(postTransformInvalidDeltaFile(did), afterMutation);
	}

	void runErrorWithAutoResume(Integer autoResumeDelay, boolean withAnnotation) throws IOException {
		UUID did = UUID.randomUUID();
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
			resumePolicy.setDataSource(original.getDataSource());
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

		Map<String, String> tags = tagsFor(ActionEventType.ERROR, "SampleEgressAction", REST_DATA_SOURCE_NAME, EGRESS_FLOW_NAME);
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
		UUID did = UUID.randomUUID();
		deltaFileRepo.save(postErrorDeltaFile(did));

		List<RetryResult> retryResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("resume"), did),
				"data." + DgsConstants.MUTATION.Resume,
				new TypeRef<>() {});

		assertEquals(2, retryResults.size());
		assertEquals(did, retryResults.getFirst().getDid());
		assertTrue(retryResults.getFirst().getSuccess());
		assertFalse(retryResults.get(1).getSuccess());

		verifyActionEventResults(postResumeDeltaFile(did),
				ActionContext.builder().flowName(EGRESS_FLOW_NAME).actionName(SAMPLE_EGRESS_ACTION).build());

		Mockito.verifyNoInteractions(metricService);
	}

	@Test
	void testResumeClearsAcknowledged() throws IOException {
		UUID did = UUID.randomUUID();
		DeltaFile postErrorDeltaFile = postErrorDeltaFile(did);
		postErrorDeltaFile.acknowledgeErrors(OffsetDateTime.now(), "some reason");
		deltaFileRepo.save(postErrorDeltaFile);

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("resume"), did),
				"data." + DgsConstants.MUTATION.Resume,
				new TypeRef<>() {});

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertNull(deltaFile.getFlows().get(2).lastAction().getErrorAcknowledged());
		assertNull(deltaFile.getFlows().get(2).lastAction().getErrorAcknowledgedReason());
		Mockito.verifyNoInteractions(metricService);
	}

	@Test
	void testAcknowledge() throws IOException {
		UUID did = UUID.randomUUID();
		deltaFileRepo.save(postErrorDeltaFile(did));

		List<AcknowledgeResult> acknowledgeResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("acknowledge"), did),
				"data." + DgsConstants.MUTATION.Acknowledge,
				new TypeRef<>() {});

		assertEquals(2, acknowledgeResults.size());
		assertEquals(did, acknowledgeResults.getFirst().getDid());
		assertTrue(acknowledgeResults.getFirst().getSuccess());
		assertFalse(acknowledgeResults.get(1).getSuccess());

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertNotNull(deltaFile.getFlows().get(2).lastAction().getErrorAcknowledged());
		assertEquals("apathy", deltaFile.getFlows().get(2).lastAction().getErrorAcknowledgedReason());
		Mockito.verifyNoInteractions(metricService);
	}

	@Test
	void testCancel() throws IOException {
		UUID did = UUID.randomUUID();
		DeltaFile deltaFile = postTransformUtf8DeltaFile(did);
		deltaFileRepo.save(deltaFile);

		List<CancelResult> cancelResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("cancel"), did),
				"data." + DgsConstants.MUTATION.Cancel,
				new TypeRef<>() {
				});

		assertEquals(2, cancelResults.size());
		assertEquals(did, cancelResults.getFirst().getDid());
		assertTrue(cancelResults.getFirst().getSuccess());
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
		assertEquals(did, alreadyCancelledResult.getFirst().getDid());
		assertFalse(alreadyCancelledResult.getFirst().getSuccess());
		assertTrue(alreadyCancelledResult.getFirst().getError().contains("no longer active"));
		assertFalse(alreadyCancelledResult.get(1).getSuccess());
		assertTrue(alreadyCancelledResult.get(1).getError().contains("not found"));
		Mockito.verifyNoInteractions(metricService);
	}

	@Captor
	private ArgumentCaptor<Metric> metricCaptor;

	@Test
	void testToEgressWithTestModeEgress() throws IOException {
		UUID did = UUID.randomUUID();
		DeltaFile midTransform = postTransformUtf8DeltaFile(did);
		DeltaFileFlow transformFlow = midTransform.getFlow(TRANSFORM_FLOW_NAME, 1);
		transformFlow.setTestMode(true);
		transformFlow.setTestModeReason(transformFlow.getName());
		deltaFileRepo.save(midTransform);

		deltaFilesService.handleActionEvent(actionEvent("transform", did));

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);

		assertEqualsIgnoringDates(
				postTransformDeltaFileInTestMode(did, OffsetDateTime.now()),
				deltaFile
		);
		MatcherAssert.assertThat(deltaFile.getFlows().get(1).getTestModeReason(), containsString(TRANSFORM_FLOW_NAME));
		Mockito.verify(actionEventQueue, never()).putActions(any(), anyBoolean());
		verifyCommonMetrics(ActionEventType.TRANSFORM, "SampleTransformAction", REST_DATA_SOURCE_NAME, null, "type");
	}

	@Test
	void testReplay() throws IOException {
		UUID did = UUID.randomUUID();
		deltaFileRepo.save(postEgressDeltaFile(did));

		List<RetryResult> results = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("replay"), did),
				"data." + DgsConstants.MUTATION.Replay,
				new TypeRef<>() {});

		assertEquals(1, results.size());
		assertTrue(results.getFirst().getSuccess());

		DeltaFile parent = deltaFilesService.getDeltaFile(did);
		assertEquals(1, parent.getChildDids().size());
		assertEquals(parent.getChildDids().getFirst(), parent.getReplayDid());
		assertEquals(results.getFirst().getDid(), parent.getReplayDid());
		assertNotNull(parent.getReplayed());

		DeltaFile expected = postIngressDeltaFile(did);
		expected.setDid(parent.getChildDids().getFirst());
		expected.setParentDids(List.of(did));
		Action action = expected.getFlows().getFirst().addAction("Replay", ActionType.INGRESS, COMPLETE, OffsetDateTime.now());
		Map<String, String> replayMetadata = Map.of("AuthorizedBy", "ABC", "anotherKey", "anotherValue");
		action.setContent(expected.getFlows().getFirst().getActions().getFirst().getContent());
		action.setMetadata(replayMetadata);
		action.setDeleteMetadataKeys(List.of("removeMe"));
		expected.getFlows().get(1).getInput().setMetadata(expected.getFlows().getFirst().getMetadata());
		verifyActionEventResults(expected, ActionContext.builder().flowName("sampleTransform").actionName("Utf8TransformAction").build());

		List<RetryResult> secondResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("replay"), did),
				"data." + DgsConstants.MUTATION.Replay,
				new TypeRef<>() {});

		assertEquals(1, secondResults.size());
		assertFalse(secondResults.getFirst().getSuccess());
		Mockito.verifyNoInteractions(metricService);
	}

	@Test
	void testReplayContentDeleted() throws IOException {
		UUID did = UUID.randomUUID();
		DeltaFile contentDeletedDeltaFile = postEgressDeltaFile(did);
		contentDeletedDeltaFile.setContentDeleted(OffsetDateTime.now());
		contentDeletedDeltaFile.setContentDeletedReason("aged off");
		deltaFileRepo.save(contentDeletedDeltaFile);

		List<RetryResult> results = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("replay"), did),
				"data." + DgsConstants.MUTATION.Replay,
				new TypeRef<>() {});

		assertEquals(1, results.size());
		assertFalse(results.getFirst().getSuccess());

		DeltaFile parent = deltaFilesService.getDeltaFile(did);
		assertTrue(parent.getChildDids().isEmpty());

		Mockito.verifyNoInteractions(metricService);
	}

	@Test
	void testSourceMetadataUnion() throws IOException {
		DeltaFile deltaFile1 = buildDeltaFile(UUID.randomUUID(), List.of(), Map.of("key", "val1"));
		DeltaFile deltaFile2 = buildDeltaFile(UUID.randomUUID(), List.of(), Map.of("key", "val2"));
		List<DeltaFile> deltaFiles = List.of(deltaFile1, deltaFile2);
		deltaFileRepo.saveAll(deltaFiles);

		List<UniqueKeyValues> metadataUnion = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("source"), deltaFile1.getDid(), deltaFile2.getDid()),
				"data." + DgsConstants.QUERY.SourceMetadataUnion,
				new TypeRef<>() {
				});

		assertEquals(1, metadataUnion.size());
		assertEquals("key", metadataUnion.getFirst().getKey());
		assertEquals(2, metadataUnion.getFirst().getValues().size());
		assertTrue(metadataUnion.getFirst().getValues().containsAll(List.of("val1", "val2")));
	}

	@Test
	void testFilterTransform() throws IOException {
		UUID did = UUID.randomUUID();
		verifyFiltered(postIngressDeltaFile(did), TRANSFORM_FLOW_NAME, 1, "Utf8TransformAction", 0);
	}

	@Test
	void testFilterEgress() throws IOException {
		UUID did = UUID.randomUUID();
		verifyFiltered(postTransformDeltaFile(did), EGRESS_FLOW_NAME, 2,"SampleEgressAction", 0);
	}

	@SuppressWarnings("SameParameterValue")
	private void verifyFiltered(DeltaFile deltaFile, String flow, int flowId, String filteredAction, int actionId) throws IOException {
		deltaFileRepo.save(deltaFile);

		deltaFilesService.handleActionEvent(filterActionEvent(deltaFile.getDid(), flow, flowId, filteredAction, actionId));

		DeltaFile actual = deltaFilesService.getDeltaFile(deltaFile.getDid());
		Action action = actual.getFlow(flow, flowId).getAction(filteredAction, actionId);
		assert action != null;

		assertEquals(ActionState.FILTERED, action.getState());
		assertEquals(DeltaFileStage.COMPLETE, actual.getStage());
		assertTrue(actual.getFiltered());
		assertEquals("you got filtered", action.getFilteredCause());
		assertEquals("here is why: blah", action.getFilteredContext());
		assertEquals("filter metadata", actual.getAnnotations().get("filterKey"));

		Mockito.verify(actionEventQueue, never()).putActions(any(), anyBoolean());
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

        assertInstanceOf(TransformActionConfiguration.class, configs.getFirst());

		TransformActionConfiguration transformActionConfiguration = (TransformActionConfiguration) configs.getFirst();
		assertEquals(name, transformActionConfiguration.getName());
	}

	@Test
	void testGetTransformFlowPlan() {
		clearForFlowTests();
		TransformFlowPlan transformFlowPlanA = new TransformFlowPlan("transformPlan", "description");
		TransformFlowPlan transformFlowPlanB = new TransformFlowPlan("b", "description");
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
	void testGetTimedIngressDataSource() {
		clearForFlowTests();
		DataSourcePlan dataSourcePlanA = new TimedDataSourcePlan("timedIngressPlan", FlowType.TIMED_DATA_SOURCE,
				"description", "topic", new TimedIngressActionConfiguration("timedIngress", "type"),  "*/5 * * * * *");
		DataSourcePlan dataSourcePlanB = new TimedDataSourcePlan("b", FlowType.TIMED_DATA_SOURCE, "description", "topic",
				new TimedIngressActionConfiguration("timedIngress", "type"), "*/5 * * * * *");
		dataSourcePlanRepo.saveAll(List.of(dataSourcePlanA, dataSourcePlanB));
		DataSourcePlan plan = FlowPlanDatafetcherTestHelper.getTimedIngressFlowPlan(dgsQueryExecutor);
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
		dataSourceRepo.save(buildTimedDataSource(FlowState.STOPPED));
		DataSource dataSource = FlowPlanDatafetcherTestHelper.validateTimedIngressFlow(dgsQueryExecutor);
		assertThat(dataSource.getFlowStatus()).isNotNull();
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

		DataSource dataSource = new TimedDataSource();
		dataSource.setName("timedIngress");
		dataSource.setSourcePlugin(pluginCoordinates);

		Plugin plugin = new Plugin();
		plugin.setPluginCoordinates(pluginCoordinates);
		pluginRepository.save(plugin);
		pluginVariableRepo.save(variables);
		transformFlowRepo.save(transformFlow);
		egressFlowRepo.save(egressFlow);
		dataSourceRepo.save(dataSource);
		refreshFlowCaches();

		List<Flows> flows = FlowPlanDatafetcherTestHelper.getFlows(dgsQueryExecutor);
		assertThat(flows).hasSize(1);
		Flows pluginFlows = flows.getFirst();
		assertThat(pluginFlows.getSourcePlugin().getArtifactId()).isEqualTo("test-actions");
		assertThat(pluginFlows.getTransformFlows().getFirst().getName()).isEqualTo("transform");
		assertThat(pluginFlows.getEgressFlows().getFirst().getName()).isEqualTo("egress");
		assertThat(pluginFlows.getDataSources().getFirst().getName()).isEqualTo("timedIngress");
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

		dataSourceRepo.save(buildTimedDataSource(FlowState.STOPPED));
		transformFlowRepo.save(buildTransformFlow(FlowState.STOPPED));
		egressFlowRepo.save(buildEgressFlow(FlowState.STOPPED));
		refreshFlowCaches();

		FlowNames flows = FlowPlanDatafetcherTestHelper.getFlowNames(dgsQueryExecutor);
		assertThat(flows.getDataSource()).hasSize(1).contains(TIMED_DATA_SOURCE_NAME);
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
		assertThat(flows.getTransform()).hasSize(1).matches(transformFlows -> TRANSFORM_FLOW_NAME.equals(transformFlows.getFirst().getName()));
		assertThat(flows.getEgress()).hasSize(1).matches(egressFlows -> EGRESS_FLOW_NAME.equals(egressFlows.getFirst().getName()));

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

		DataSource dataSource = new TimedDataSource();
		dataSource.setName(TIMED_DATA_SOURCE_NAME);

		EgressFlow egressFlow = new EgressFlow();
		egressFlow.setName(EGRESS_FLOW_NAME);

		transformFlowRepo.save(transformFlow);
		dataSourceRepo.save(dataSource);
		egressFlowRepo.save(egressFlow);
		refreshFlowCaches();

		SystemFlows flows = FlowPlanDatafetcherTestHelper.getAllFlows(dgsQueryExecutor);
		assertThat(flows.getTransform()).hasSize(1).matches(transformFlows -> TRANSFORM_FLOW_NAME.equals(transformFlows.getFirst().getName()));
		assertThat(flows.getDataSource()).hasSize(1).matches(timedIngressFlows -> TIMED_DATA_SOURCE_NAME.equals(timedIngressFlows.getFirst().getName()));
		assertThat(flows.getEgress()).hasSize(1).matches(egressFlows -> EGRESS_FLOW_NAME.equals(egressFlows.getFirst().getName()));
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
		dataSourceRepo.save(buildTimedDataSource(FlowState.STOPPED));
		refreshFlowCaches();

		List<ActionFamily> actionFamilies = FlowPlanDatafetcherTestHelper.getActionFamilies(dgsQueryExecutor);
		assertThat(actionFamilies).hasSize(4);

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
		DataSource dataSource = FlowPlanDatafetcherTestHelper.saveTimedIngressFlowPlan(dgsQueryExecutor);
		assertThat(dataSource).isNotNull();
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
		TimedDataSourcePlan dataSourcePlan = new TimedDataSourcePlan("flowPlan", FlowType.TIMED_DATA_SOURCE,
				null, null, null, null);
		dataSourcePlanRepo.save(dataSourcePlan);
		assertThatThrownBy(() -> FlowPlanDatafetcherTestHelper.removeTimedIngressFlowPlan(dgsQueryExecutor))
				.isInstanceOf(QueryException.class)
				.hasMessageContaining("Flow plan flowPlan is not a system-plugin flow plan and cannot be removed");

		dataSourcePlan.setSourcePlugin(systemPluginService.getSystemPluginCoordinates());
		dataSourcePlanRepo.save(dataSourcePlan);
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
		dataSourceRepo.save(buildTimedDataSource(FlowState.STOPPED));
		assertTrue(FlowPlanDatafetcherTestHelper.startTimedIngressFlow(dgsQueryExecutor));
	}

	@Test
	void testStopTimedIngressFlow() {
		clearForFlowTests();
		dataSourceRepo.save(buildTimedDataSource(FlowState.RUNNING));
		assertTrue(FlowPlanDatafetcherTestHelper.stopTimedIngressFlow(dgsQueryExecutor));
	}

	@Test
	void testSetMemoTimedIngressWhenStopped() {
		clearForFlowTests();
		dataSourceRepo.save(buildTimedDataSource(FlowState.STOPPED));
		assertFalse(FlowPlanDatafetcherTestHelper.setTimedDataSourceMemo(dgsQueryExecutor, null));
		assertTrue(FlowPlanDatafetcherTestHelper.setTimedDataSourceMemo(dgsQueryExecutor, "100"));
	}

	@Test
	void testSetMemoTimedIngressWhenRunning() {
		clearForFlowTests();
		dataSourceRepo.save(buildTimedDataSource(FlowState.RUNNING));
		assertFalse(FlowPlanDatafetcherTestHelper.setTimedDataSourceMemo(dgsQueryExecutor, "100"));
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
		assertTrue(results.getFirst().isSuccess());
		assertFalse(results.get(1).isSuccess());
		assertTrue(results.get(1).getErrors().contains("duplicate name or criteria"));
		assertTrue(results.get(2).getErrors().contains("duplicate name or criteria"));
		assertTrue(results.get(3).isSuccess());
		assertEquals(2, resumePolicyRepo.count());

		List<ResumePolicy> policies = ResumePolicyDatafetcherTestHelper.getAllResumePolicies(dgsQueryExecutor);
		assertEquals(2, policies.size());
		UUID idToUse;
		// Result are not ordered explicitly
		if (ResumePolicyDatafetcherTestHelper.isDefaultFlow(policies.getFirst())) {
			idToUse = policies.getFirst().getId();
			assertTrue(ResumePolicyDatafetcherTestHelper.matchesDefault(policies.getFirst()));
		} else {
			idToUse = policies.get(1).getId();
			assertTrue(ResumePolicyDatafetcherTestHelper.matchesDefault(policies.get(1)));
		}

		ResumePolicy policy = ResumePolicyDatafetcherTestHelper.getResumePolicy(dgsQueryExecutor, idToUse);
		assertTrue(ResumePolicyDatafetcherTestHelper.matchesDefault(
				policy));

		Result updateResult = ResumePolicyDatafetcherTestHelper.updateResumePolicy(dgsQueryExecutor, UUID.randomUUID());
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
		assertTrue(results.getFirst().isSuccess());

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
		UUID did1 = UUID.randomUUID();
		UUID did2 = UUID.randomUUID();
		UUID did3 = UUID.randomUUID();
		DeltaFile before1 = postTransformDeltaFile(did1);
		deltaFileRepo.save(before1);
		DeltaFile before2 = postTransformDeltaFile(did2);
		deltaFileRepo.save(before2);
		DeltaFile before3 = postTransformDeltaFile(did3);
		before3.setDataSource("flow3");
		deltaFileRepo.save(before3);

		deltaFilesService.handleActionEvent(actionEvent("error", did1));
		deltaFilesService.handleActionEvent(actionEvent("error", did2));
		deltaFilesService.handleActionEvent(actionEvent("error", did3));

		DeltaFile during1 = deltaFilesService.getDeltaFile(did1);
		DeltaFile during2 = deltaFilesService.getDeltaFile(did2);
		DeltaFile during3 = deltaFilesService.getDeltaFile(did3);

		assertNull(during1.getFlows().getLast().lastAction().getNextAutoResume());
		assertNull(during2.getFlows().getLast().lastAction().getNextAutoResume());
		assertNull(during3.getFlows().getLast().lastAction().getNextAutoResume());

		final String errorCause = "Authority XYZ not recognized";

		List<Result> loadA = ResumePolicyDatafetcherTestHelper.loadResumePolicy(dgsQueryExecutor,
				true, "policy1", during1.getDataSource(), null);
		List<Result> loadB = ResumePolicyDatafetcherTestHelper.loadResumePolicy(dgsQueryExecutor,
				false, "policy2", null, errorCause);

		assertTrue(loadA.getFirst().isSuccess());
		assertTrue(loadB.getFirst().isSuccess());

		Result applyResults = ResumePolicyDatafetcherTestHelper.applyResumePolicies(dgsQueryExecutor, List.of("policy1", "policy2"));
		assertTrue(applyResults.isSuccess());
		assertTrue(applyResults.getErrors().isEmpty());
		assertEquals(2, applyResults.getInfo().size());
		assertEquals("Applied policy1 policy to 2 DeltaFiles", applyResults.getInfo().getFirst());
		assertEquals("Applied policy2 policy to 1 DeltaFiles", applyResults.getInfo().get(1));

		DeltaFile final1 = deltaFilesService.getDeltaFile(did1);
		DeltaFile final2 = deltaFilesService.getDeltaFile(did2);
		DeltaFile final3 = deltaFilesService.getDeltaFile(did3);

		assertNotNull(final1.getFlows().getLast().lastAction().getNextAutoResume());
		assertNotNull(final2.getFlows().getLast().lastAction().getNextAutoResume());
		assertNotNull(final3.getFlows().getLast().lastAction().getNextAutoResume());

		assertEquals("policy1", final1.getFlows().getLast().lastAction().getNextAutoResumeReason());
		assertEquals("policy1", final2.getFlows().getLast().lastAction().getNextAutoResumeReason());
		assertEquals("policy2", final3.getFlows().getLast().lastAction().getNextAutoResumeReason());
	}

	@Test
	void testResumePolicyDryRun() {
		DeltaFile deltaFile = buildErrorDeltaFile(UUID.randomUUID(), "flow1", "errorCause", "context", MONGO_NOW);
		deltaFileRepo.save(deltaFile);

		DeltaFile deltaFile2 = buildErrorDeltaFile(UUID.randomUUID(), "flow2", "errorCause", "context", MONGO_NOW);
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
		DeltaFile expected = deltaFilesService.ingress(buildDataSource(DATA_SOURCE), INGRESS_INPUT, OffsetDateTime.now(), OffsetDateTime.now());

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
				new DeltaFileGraphQLQuery.Builder().did(expected.getDid()).build(), DELTA_FILE_PROJECTION_ROOT, SCALARS);

		DeltaFile actual = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQLQueryRequest.serialize(),
				"data." + DgsConstants.QUERY.DeltaFile,
				DeltaFile.class
		);

		assertEqualsIgnoringDates(expected, actual);
	}

	@Test
	void deltaFiles() {
		DeltaFile deltaFile = buildErrorDeltaFile(UUID.randomUUID(), "flow", "errorCause", "context", MONGO_NOW);
		deltaFile.setContentDeleted(MONGO_NOW);
		deltaFile.setContentDeletedReason("contentDeletedReason");
		Action erroredAction = deltaFile.getFlow("firstFlow", 1).getAction("ErrorAction", 0);
		erroredAction.setNextAutoResume(MONGO_NOW);
		erroredAction.setNextAutoResumeReason("nextAutoResumeReason");
		deltaFile.getFlows().forEach(flow -> flow.setFlowPlan(FlowPlanCoordinates.builder().plugin("plugin").pluginVersion("1").name(flow.getName()).build()));

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
		assertEqualsIgnoringDates(expected.getDeltaFiles().getFirst(), actual.getDeltaFiles().getFirst());
	}

	@Test
	void resume() {
		DeltaFile input = postErrorDeltaFile(UUID.randomUUID());
		DeltaFile second = postTransformDeltaFile(UUID.randomUUID());
		deltaFileRepo.saveAll(List.of(input, second));

		UUID badDid = UUID.randomUUID();
		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
				new ResumeGraphQLQuery.Builder()
						.dids(List.of(input.getDid(), second.getDid(), badDid))
						.build(),
				new ResumeProjectionRoot().did().success().error(),
				SCALARS
		);

		List<RetryResult> results = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQLQueryRequest.serialize(),
				"data." + DgsConstants.MUTATION.Resume,
				new TypeRef<>() {}
		);

		assertEquals(3, results.size());

		assertEquals(input.getDid(), results.getFirst().getDid());
		assertTrue(results.getFirst().getSuccess());
		assertNull(results.getFirst().getError());

		assertEquals(second.getDid(), results.get(1).getDid());
		assertFalse(results.get(1).getSuccess());
		assertEquals("DeltaFile with did " + second.getDid() + " had no errors", results.get(1).getError());

		assertEquals(badDid, results.get(2).getDid());
		assertFalse(results.get(2).getSuccess());
		assertEquals("DeltaFile with did %s not found".formatted(badDid), results.get(2).getError());

		DeltaFile afterResumeFile = deltaFilesService.getDeltaFile(input.getDid());
		assertEquals(2, afterResumeFile.getFlows().get(2).getActions().size());
		assertEquals(ActionState.RETRIED, afterResumeFile.getFlows().get(2).getActions().getFirst().getState());
		assertEquals(QUEUED, afterResumeFile.getFlows().get(2).getActions().get(1).getState());
		// StateMachine will queue the failed loadAction again leaving the DeltaFile in the IN_FLIGHT stage
		assertEquals(DeltaFileFlowState.IN_FLIGHT, afterResumeFile.getFlows().get(2).getState());
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

		validatePlugin1(plugins.getFirst());
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
		if (matched.getFirst().getDisplayName().equals("Test Plugin 1")) {
			assertEquals("Test Plugin 4", matched.get(1).getDisplayName());
		} else {
			assertEquals("Test Plugin 1", matched.get(1).getDisplayName());
			assertEquals("Test Plugin 4", matched.getFirst().getDisplayName());
		}

		matched = pluginRepository.findPluginsWithDependency(
				new PluginCoordinates("org.deltafi", "plugin-2", "1.0.0"));
		assertEquals(1, matched.size());
		assertEquals("Test Plugin 1", matched.getFirst().getDisplayName());

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
		Action ingress = Action.builder().name("ingress").modified(MONGO_NOW).state(ActionState.COMPLETE).build();
		Action hit = Action.builder().name("hit").modified(MONGO_NOW).state(ActionState.ERROR).build();
		hit.setNextAutoResume(MONGO_NOW.minusSeconds(1000));
		Action miss = Action.builder().name("miss").modified(MONGO_NOW).state(ActionState.ERROR).build();
		Action notSet = Action.builder().name("notSet").modified(MONGO_NOW).state(ActionState.ERROR).build();
		Action other = Action.builder().name("other").modified(MONGO_NOW).state(ActionState.COMPLETE).build();

		DeltaFile shouldResume = buildDeltaFile(UUID.randomUUID(), TRANSFORM_FLOW_NAME, DeltaFileStage.ERROR, MONGO_NOW, MONGO_NOW);
		shouldResume.getFlows().getFirst().setActions(Arrays.asList(ingress, other, hit));
		deltaFileRepo.save(shouldResume);

		DeltaFile shouldNotResume = buildDeltaFile(UUID.randomUUID(), TRANSFORM_FLOW_NAME, DeltaFileStage.ERROR, MONGO_NOW, MONGO_NOW);
		shouldNotResume.getFlows().getFirst().setActions(Arrays.asList(ingress, miss));
		deltaFileRepo.save(shouldNotResume);

		DeltaFile notResumable = buildDeltaFile(UUID.randomUUID(), TRANSFORM_FLOW_NAME, DeltaFileStage.ERROR, MONGO_NOW, MONGO_NOW);
		notResumable.getFlows().getFirst().setActions(Arrays.asList(ingress, notSet));
		deltaFileRepo.save(notResumable);

		DeltaFile cancelled = buildDeltaFile(UUID.randomUUID(), TRANSFORM_FLOW_NAME, DeltaFileStage.CANCELLED, MONGO_NOW, MONGO_NOW);
		cancelled.getFlows().getFirst().setActions(Arrays.asList(ingress, other, hit));
		deltaFileRepo.save(cancelled);

		DeltaFile contentDeleted = buildDeltaFile(UUID.randomUUID(), TRANSFORM_FLOW_NAME, DeltaFileStage.ERROR, MONGO_NOW, MONGO_NOW);
		contentDeleted.getFlows().getFirst().setActions(Arrays.asList(ingress, other, hit));
		contentDeleted.setContentDeleted(MONGO_NOW);
		deltaFileRepo.save(contentDeleted);

		DeltaFile shouldAlsoResume = buildDeltaFile(UUID.randomUUID(), TRANSFORM_FLOW_NAME, DeltaFileStage.ERROR, MONGO_NOW, MONGO_NOW);
		shouldAlsoResume.getFlows().getFirst().setActions(Arrays.asList(ingress, other, hit));
		deltaFileRepo.save(shouldAlsoResume);

		List<DeltaFile> hits = deltaFileRepo.findReadyForAutoResume(MONGO_NOW);
		assertEquals(3, hits.size());
		assertEquals(shouldResume.getDid(), hits.getFirst().getDid());
		assertEquals(contentDeleted.getDid(), hits.get(1).getDid());
		assertEquals(shouldAlsoResume.getDid(), hits.get(2).getDid());

		assertEquals(2, deltaFilesService.autoResume(MONGO_NOW));

		Mockito.verify(metricService).increment
				(new Metric(DeltaFiConstants.FILES_AUTO_RESUMED, 2)
						.addTag(DATA_SOURCE, TRANSFORM_FLOW_NAME));
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testUpdateForRequeue() {
		Action shouldRequeue = Action.builder().name("hit").modified(MONGO_NOW.minusSeconds(1000)).state(QUEUED).build();
		Action excludedRequeue = Action.builder().name("excluded").modified(MONGO_NOW.minusSeconds(1000)).state(QUEUED).build();
		Action shouldStay = Action.builder().name("miss").modified(MONGO_NOW.plusSeconds(1000)).state(QUEUED).build();

		DeltaFile oneHit = buildDeltaFile(UUID.randomUUID(), "flow1", DeltaFileStage.IN_FLIGHT, MONGO_NOW, MONGO_NOW.minusSeconds(1000));
		oneHit.getFlows().getFirst().setState(DeltaFileFlowState.IN_FLIGHT);
		oneHit.getFlows().getFirst().setActions(List.of(shouldRequeue));
		DeltaFileFlow flow2 = oneHit.addFlow("flow3", FlowType.EGRESS, oneHit.getFlows().getFirst(), MONGO_NOW.minusSeconds(1000));
		flow2.setActions(List.of(shouldStay));
		deltaFileRepo.save(oneHit);

		DeltaFile twoHits = buildDeltaFile(UUID.randomUUID(), "flow1", DeltaFileStage.IN_FLIGHT, MONGO_NOW, MONGO_NOW.minusSeconds(1000));
		twoHits.getFlows().getFirst().setState(DeltaFileFlowState.IN_FLIGHT);
		twoHits.setRequeueCount(5);
		twoHits.getFlows().getFirst().setActions(List.of(shouldRequeue));
		flow2 = twoHits.addFlow("flow2", FlowType.TRANSFORM, oneHit.getFlows().getFirst(), MONGO_NOW.minusSeconds(1000));
		flow2.setActions(List.of(shouldStay));
		DeltaFileFlow flow3 = twoHits.addFlow("flow3", FlowType.EGRESS, oneHit.getFlows().getFirst(), MONGO_NOW.minusSeconds(1000));
		flow3.setActions(List.of(shouldRequeue));
		deltaFileRepo.save(twoHits);

		DeltaFile miss = buildDeltaFile(UUID.randomUUID(), "flow1", DeltaFileStage.IN_FLIGHT, MONGO_NOW, MONGO_NOW.plusSeconds(1000));
		miss.getFlows().getFirst().setState(DeltaFileFlowState.IN_FLIGHT);
		miss.getFlows().getFirst().setActions(List.of(shouldStay));
		flow2 = oneHit.addFlow("flow2", FlowType.TRANSFORM, oneHit.getFlows().getFirst(), MONGO_NOW.minusSeconds(1000));
		flow2.setActions(List.of(excludedRequeue));
		flow3 = oneHit.addFlow("flow3", FlowType.EGRESS, oneHit.getFlows().getFirst(), MONGO_NOW.minusSeconds(1000));
		flow3.setActions(List.of(shouldStay));
		deltaFileRepo.save(miss);

		DeltaFile excludedByDid = buildDeltaFile(UUID.randomUUID(), "flow1", DeltaFileStage.IN_FLIGHT, MONGO_NOW, MONGO_NOW.minusSeconds(1000));
		excludedByDid.getFlows().getFirst().setState(DeltaFileFlowState.IN_FLIGHT);
		excludedByDid.getFlows().getFirst().setActions(List.of(shouldRequeue));
		deltaFileRepo.save(excludedByDid);

		DeltaFile wrongStage = buildDeltaFile(UUID.randomUUID(), "flow1", DeltaFileStage.CANCELLED, MONGO_NOW, MONGO_NOW.minusSeconds(1000));
		wrongStage.getFlows().getFirst().setState(DeltaFileFlowState.IN_FLIGHT);
		wrongStage.getFlows().getFirst().setActions(List.of(shouldRequeue));
		deltaFileRepo.save(wrongStage);

		List<DeltaFile> hits = deltaFileRepo.updateForRequeue(MONGO_NOW, Duration.ofSeconds(30), Set.of("excluded", "anotherAction"), Set.of(excludedByDid.getDid(), UUID.randomUUID()));

		assertEquals(2, hits.size());

		DeltaFile oneHitAfter = loadDeltaFile(oneHit.getDid());
		DeltaFile twoHitsAfter = loadDeltaFile(twoHits.getDid());
		DeltaFile missAfter = loadDeltaFile(miss.getDid());

		assertEquals(1, oneHitAfter.getRequeueCount());
		assertEquals(6, twoHitsAfter.getRequeueCount());
		assertEquals(miss, missAfter);
		assertNotEquals(oneHit.getFlows().getFirst().getActions().getFirst().getModified(), oneHitAfter.getFlows().getFirst().getActions().getFirst().getModified());
		assertEquals(oneHit.getFlows().get(1).getActions().getFirst().getModified(), oneHitAfter.getFlows().get(1).getActions().getFirst().getModified());
		assertNotEquals(twoHits.getFlows().getFirst().getActions().getFirst().getModified(), twoHitsAfter.getFlows().getFirst().getActions().getFirst().getModified());
		assertEquals(twoHits.getFlows().get(1).getActions().getFirst().getModified(), twoHitsAfter.getFlows().get(1).getActions().getFirst().getModified());
		assertNotEquals(twoHits.getFlows().get(2).getActions().getFirst().getModified(), twoHitsAfter.getFlows().get(2).getActions().getFirst().getModified());
	}

	@Test
	void batchedBulkDeleteByDidIn() {
		List<DeltaFile> deltaFiles = Stream.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()).map(Util::buildDeltaFile).toList();
		deltaFileRepo.saveAll(deltaFiles);

		assertEquals(3, deltaFileRepo.count());

		deltaFileRepo.batchedBulkDeleteByDidIn(Arrays.asList(deltaFiles.getFirst().getDid(), deltaFiles.getLast().getDid()));

		assertEquals(1, deltaFileRepo.count());
		assertEquals(deltaFiles.get(1).getDid(), deltaFileRepo.findAll().getFirst().getDid());
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testFindForDeleteCreatedBefore() {
		DeltaFile deltaFile1 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, OffsetDateTime.now().minusSeconds(1), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile2.acknowledgeErrors(OffsetDateTime.now(), "reason");
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.IN_FLIGHT, OffsetDateTime.now().plusSeconds(2), OffsetDateTime.now().plusSeconds(2));
		deltaFileRepo.save(deltaFile3);
		DeltaFile deltaFile4 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile2.acknowledgeErrors(OffsetDateTime.now(), "reason");
		deltaFile4.setContentDeleted(OffsetDateTime.now());
		deltaFileRepo.save(deltaFile4);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForTimedDelete(OffsetDateTime.now().plusSeconds(1), null, 0, null, false, 10);
		assertEquals(List.of(deltaFile1.getDid(), deltaFile2.getDid()), deltaFiles.stream().map(DeltaFile::getDid).toList());
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testFindForDeleteCreatedBeforeBatchLimit() {
		DeltaFile deltaFile1 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.IN_FLIGHT, OffsetDateTime.now().plusSeconds(2), OffsetDateTime.now().plusSeconds(2));
		deltaFileRepo.save(deltaFile3);
		DeltaFile deltaFile4 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile4.setContentDeleted(OffsetDateTime.now());
		deltaFileRepo.save(deltaFile4);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForTimedDelete(OffsetDateTime.now().plusSeconds(1), null, 0, null, false, 1);
		assertEquals(List.of(deltaFile1.getDid()), deltaFiles.stream().map(DeltaFile::getDid).toList());
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testFindForDeleteCreatedBeforeWithMetadata() {
		DeltaFile deltaFile1 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile1.setContentDeleted(OffsetDateTime.now());
		deltaFileRepo.save(deltaFile1);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForTimedDelete(OffsetDateTime.now().plusSeconds(1), null, 0, null, true, 10);
		assertEquals(List.of(deltaFile1.getDid()), deltaFiles.stream().map(DeltaFile::getDid).toList());
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testFindForDeleteCompletedBefore() {
		DeltaFile deltaFile1 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, OffsetDateTime.now().minusSeconds(1), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now().plusSeconds(2));
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile3.getFlows().getFirst().addAction("errorAction", ActionType.TRANSFORM, ActionState.ERROR, OffsetDateTime.now());
		deltaFile3.updateFlags();
		deltaFileRepo.save(deltaFile3);
		DeltaFile deltaFile4 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile4.getFlows().getFirst().addAction("errorAction", ActionType.TRANSFORM, ActionState.ERROR, OffsetDateTime.now());
		deltaFile4.acknowledgeErrors(OffsetDateTime.now(), null);
		deltaFile4.updateFlags();
		deltaFileRepo.save(deltaFile4);
		DeltaFile deltaFile5 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile5.getFlows().getFirst().setPendingAnnotations(Set.of("a"));
		deltaFile5.updateFlags();
		deltaFileRepo.save(deltaFile5);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForTimedDelete(null, OffsetDateTime.now().plusSeconds(1), 0, null, false, 10);
		assertEquals(List.of(deltaFile1.getDid(), deltaFile4.getDid()), deltaFiles.stream().map(DeltaFile::getDid).toList());
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testFindForDeleteWithFlow() {
		DeltaFile deltaFile1 = buildDeltaFile(UUID.randomUUID(), "a", DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile(UUID.randomUUID(), "b", DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile2);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForTimedDelete(OffsetDateTime.now().plusSeconds(1), null, 0, "a", false, 10);
		assertEquals(List.of(deltaFile1.getDid()), deltaFiles.stream().map(DeltaFile::getDid).toList());
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testFindForDelete_alreadyMarkedDeleted() {
		OffsetDateTime oneSecondAgo = OffsetDateTime.now().minusSeconds(1);

		DeltaFile deltaFile1 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, oneSecondAgo, oneSecondAgo);
		deltaFile1.setContentDeleted(oneSecondAgo);
		deltaFileRepo.save(deltaFile1);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForTimedDelete(OffsetDateTime.now(), null, 0, null, false, 10);
		assertTrue(deltaFiles.isEmpty());
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testFindForDeleteDiskSpace() {
		DeltaFile deltaFile1 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now().minusSeconds(5));
		deltaFile1.setTotalBytes(100L);
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, OffsetDateTime.now().plusSeconds(2), OffsetDateTime.now());
		deltaFile2.setTotalBytes(300L);
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now().plusSeconds(5));
		deltaFile3.setTotalBytes(500L);
		deltaFileRepo.save(deltaFile3);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForDiskSpaceDelete(250L, null, 100);
		assertEquals(List.of(deltaFile1.getDid(), deltaFile2.getDid()), deltaFiles.stream().map(DeltaFile::getDid).toList());
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testFindForDeleteDiskSpaceAll() {
		DeltaFile deltaFile1 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile1.setTotalBytes(100L);
		deltaFile1.updateFlags();
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, OffsetDateTime.now().plusSeconds(1), OffsetDateTime.now().plusSeconds(2));
		deltaFile2.setTotalBytes(300L);
		deltaFile2.updateFlags();
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, OffsetDateTime.now().plusSeconds(2), OffsetDateTime.now());
		deltaFile3.setTotalBytes(500L);
		deltaFile3.updateFlags();
		deltaFileRepo.save(deltaFile3);
		DeltaFile deltaFile4 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, OffsetDateTime.now().plusSeconds(3), OffsetDateTime.now());
		deltaFile4.setTotalBytes(500L);
		deltaFile4.setContentDeleted(OffsetDateTime.now());
		deltaFile4.updateFlags();
		deltaFileRepo.save(deltaFile4);
		DeltaFile deltaFile5 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, OffsetDateTime.now().plusSeconds(4), OffsetDateTime.now());
		deltaFile5.setTotalBytes(0L);
		deltaFile5.updateFlags();
		deltaFileRepo.save(deltaFile5);
		DeltaFile deltaFile6 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.IN_FLIGHT, OffsetDateTime.now().plusSeconds(5), OffsetDateTime.now());
		deltaFile6.setTotalBytes(50L);
		deltaFile6.updateFlags();
		deltaFileRepo.save(deltaFile6);
		DeltaFile deltaFile7 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, OffsetDateTime.now().plusSeconds(6), OffsetDateTime.now());
		deltaFile7.setTotalBytes(1000L);
		deltaFile7.getFlows().getFirst().setPendingAnnotations(Set.of("a"));
		deltaFile7.updateFlags();
		deltaFileRepo.save(deltaFile7);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForDiskSpaceDelete(2500L, null, 100);
		assertEquals(Stream.of(deltaFile1.getDid(), deltaFile2.getDid(), deltaFile3.getDid()).sorted().toList(), deltaFiles.stream().map(DeltaFile::getDid).sorted().toList());
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testFindForDeleteDiskSpaceBatchSizeLimited() {
		DeltaFile deltaFile1 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile1.setTotalBytes(100L);
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, OffsetDateTime.now().plusSeconds(2), OffsetDateTime.now().plusSeconds(1));
		deltaFile2.setTotalBytes(300L);
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now().plusSeconds(2));
		deltaFile3.setTotalBytes(500L);
		deltaFileRepo.save(deltaFile3);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForDiskSpaceDelete(2500L, null, 2);
		assertEquals(List.of(deltaFile1.getDid(), deltaFile2.getDid()), deltaFiles.stream().map(DeltaFile::getDid).toList());
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testFindForDeleteDiskSpaceBatchSizeFlow() {
		DeltaFile deltaFile1 = buildDeltaFile(UUID.randomUUID(), "a", DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile1.setTotalBytes(100L);
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile(UUID.randomUUID(), "a", DeltaFileStage.COMPLETE, OffsetDateTime.now().plusSeconds(1), OffsetDateTime.now().plusSeconds(2));
		deltaFile2.setTotalBytes(300L);
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = buildDeltaFile(UUID.randomUUID(), "b", DeltaFileStage.COMPLETE, OffsetDateTime.now().plusSeconds(2), OffsetDateTime.now());
		deltaFile3.setTotalBytes(500L);
		deltaFileRepo.save(deltaFile3);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForDiskSpaceDelete(2500L, "a", 100);
		assertEquals(List.of(deltaFile1.getDid(), deltaFile2.getDid()), deltaFiles.stream().map(DeltaFile::getDid).toList());
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testDeltaFiles_all() {
		DeltaFile deltaFile1 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, MONGO_NOW.minusSeconds(2), MONGO_NOW.minusSeconds(2));
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.plusSeconds(2));
		deltaFileRepo.save(deltaFile2);

		DeltaFiles deltaFiles = deltaFileRepo.deltaFiles(null, 50, new DeltaFilesFilter(), null, null);
		assertEquals(deltaFiles.getDeltaFiles(), List.of(deltaFile2, deltaFile1));
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testDeltaFiles_limit() {
		DeltaFile deltaFile1 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, MONGO_NOW.minusSeconds(2), MONGO_NOW.minusSeconds(2));
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.plusSeconds(2));
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
		DeltaFile deltaFile1 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, MONGO_NOW.minusSeconds(2), MONGO_NOW.plusSeconds(2));
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));
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
		DeltaFile deltaFile1 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, MONGO_NOW.minusSeconds(2), MONGO_NOW.plusSeconds(2));
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.ERROR, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));
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
		DeltaFile deltaFile1 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, MONGO_NOW.minusSeconds(2), MONGO_NOW.plusSeconds(2));
		deltaFile1.setIngressBytes(100L);
		deltaFile1.setTotalBytes(1000L);
		deltaFile1.addAnnotations(Map.of("a.1", "first", "common", "value"));
		deltaFile1.setContentDeleted(MONGO_NOW);
		deltaFile1.getFlows().getFirst().getInput().setMetadata(Map.of("key1", "value1", "key2", "value2"));
		deltaFile1.setName("filename1");
		deltaFile1.setDataSource("flow1");
		DeltaFileFlow flow1 = deltaFile1.addFlow("MyEgressFlow", FlowType.EGRESS, deltaFile1.getFlows().getFirst(), MONGO_NOW);
		flow1.setActions(List.of(Action.builder().name("action1")
				.state(ActionState.COMPLETE)
				.content(List.of(new Content("formattedFilename1", "mediaType")))
				.metadata(Map.of("formattedKey1", "formattedValue1", "formattedKey2", "formattedValue2"))
				.errorAcknowledged(MONGO_NOW)
				.build()));
		flow1.setTestModeReason("TestModeReason");
		flow1.setTestMode(true);
		deltaFile1.incrementRequeueCount();
		deltaFileRepo.save(deltaFile1);

		DeltaFile deltaFile2 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.ERROR, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));
		deltaFile2.setIngressBytes(200L);
		deltaFile2.setTotalBytes(2000L);
		deltaFile2.addAnnotations(Map.of("a.2", "first", "common", "value"));
		deltaFile2.setName("filename2");
		deltaFile2.setDataSource("flow2");
		DeltaFileFlow flow2 = deltaFile2.addFlow("MyEgressFlow", FlowType.EGRESS, deltaFile2.getFlows().getFirst(), MONGO_NOW);
		DeltaFileFlow flow2b = deltaFile2.addFlow("MyEgressFlow2", FlowType.EGRESS, deltaFile2.getFlows().getFirst(), MONGO_NOW);
		flow2.setActions(List.of(
				Action.builder().name("action1")
						.state(ActionState.ERROR)
						.errorCause("Cause")
						.build()));
		flow2b.setActions(List.of(Action.builder().name("action2")
						.state(ActionState.COMPLETE)
						.content(List.of(new Content("formattedFilename2", "mediaType")))
						.build()));
		deltaFile2.setEgressed(true);
		deltaFile2.setFiltered(true);
		deltaFileRepo.save(deltaFile2);

		DeltaFile deltaFile3 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(3), MONGO_NOW.minusSeconds(3));
		deltaFile3.setIngressBytes(300L);
		deltaFile3.setTotalBytes(3000L);
		deltaFile3.addAnnotations(Map.of("b.2", "first", "common", "value"));
		deltaFile3.setName("filename3");
		deltaFile3.setDataSource("flow3");
		DeltaFileFlow flow3 = deltaFile3.addFlow("MyTransformFlow", FlowType.TRANSFORM, deltaFile3.getFlows().getFirst(), MONGO_NOW);
		DeltaFileFlow flow3b = deltaFile3.addFlow("MyEgressFlow3", FlowType.EGRESS, deltaFile3.getFlows().getFirst(), MONGO_NOW);
		flow3.setActions(List.of(
				Action.builder()
						.name("action2")
						.state(ActionState.FILTERED)
						.filteredCause("Coffee")
						.filteredContext("and donuts")
						.build()));
		flow3b.setActions(List.of(Action.builder()
						.name("action2")
						.state(ActionState.COMPLETE)
						.content(List.of(new Content("formattedFilename3", "mediaType")))
						.build()));
		deltaFile3.setEgressed(true);
		deltaFile3.setFiltered(true);
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
		testFilter(DeltaFilesFilter.newBuilder().actions(Collections.emptyList()).build(), deltaFile1, deltaFile2, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().actions(List.of("action1")).build(), deltaFile1, deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().actions(List.of("action1", "action2")).build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().errorCause("^Cause$").build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().filtered(true).filteredCause("^Coffee$").build(), deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().filteredCause("off").build(), deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().filteredCause("nope").build());
		testFilter(DeltaFilesFilter.newBuilder().dids(Collections.emptyList()).build(), deltaFile1, deltaFile2, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().dids(Collections.singletonList(deltaFile1.getDid())).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().dids(List.of(deltaFile1.getDid(), deltaFile3.getDid())).build(), deltaFile1, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().dids(List.of(deltaFile1.getDid(), deltaFile2.getDid())).build(), deltaFile1, deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().dids(List.of(UUID.randomUUID(), UUID.randomUUID())).build());
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
		testFilter(DeltaFilesFilter.newBuilder().transformFlows(List.of("MyTransformFlow", "MyEgressFlow")).build(), deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().transformFlows(List.of("MyEgressFlow")).build());
		testFilter(DeltaFilesFilter.newBuilder().dataSources(List.of("flow1", "flow3")).build(), deltaFile1, deltaFile3);
	}

	@Test
	void testQueryByFilterMessage() {
		// Not filtered
		DeltaFile deltaFile1 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, MONGO_NOW, MONGO_NOW);
		deltaFile1.getFlows().getFirst().setActions(List.of(Action.builder().name("action1").build()));
		deltaFileRepo.save(deltaFile1);
		// Not filtered, with errorCause
		DeltaFile deltaFile2 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.ERROR, MONGO_NOW.plusSeconds(1), MONGO_NOW.plusSeconds(1));
		deltaFile2.getFlows().getFirst().setActions(List.of(Action.builder().name("action1").state(ActionState.ERROR).errorCause("Error reason 1").build()));
		deltaFileRepo.save(deltaFile2);
		// Filtered, reason 1
		DeltaFile deltaFile3 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.plusSeconds(2));
		deltaFile3.getFlows().getFirst().setActions(List.of(Action.builder().name("action1").state(ActionState.FILTERED).errorCause("Filtered reason 1").build()));
		deltaFile3.setFiltered(true);
		deltaFileRepo.save(deltaFile3);
		// Filtered, reason 2
		DeltaFile deltaFile4 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(3), MONGO_NOW.plusSeconds(3));
		deltaFile4.getFlows().getFirst().setActions(List.of(Action.builder().name("action1").state(ActionState.ERROR).filteredCause("Filtered reason 2").build()));
		deltaFile4.setFiltered(true);
		deltaFileRepo.save(deltaFile4);
		DeltaFile deltaFile5 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(3), MONGO_NOW.plusSeconds(3));
		deltaFile5.getFlows().getFirst().setActions(List.of(Action.builder().name("action1").state(ActionState.FILTERED).filteredCause("Filtered reason 2").build()));
		deltaFile5.setFiltered(true);
		deltaFileRepo.save(deltaFile5);

		testFilter(DeltaFilesFilter.newBuilder().errorCause("reason").build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().filteredCause("reason").build(), deltaFile5);
	}

	@Test
	void testQueryByCanReplay() {
		DeltaFile noContent = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, MONGO_NOW, MONGO_NOW);
		noContent.setContentDeleted(MONGO_NOW);
		noContent.setEgressed(true);
		DeltaFile hasReplayDate = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, MONGO_NOW, MONGO_NOW);
		hasReplayDate.setReplayed(MONGO_NOW);
		DeltaFile replayable = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, MONGO_NOW, MONGO_NOW);

		deltaFileRepo.saveAll(List.of(noContent, hasReplayDate, replayable));

		testFilter(DeltaFilesFilter.newBuilder().replayable(true).build(), replayable);
		testFilter(DeltaFilesFilter.newBuilder().replayable(false).build(), hasReplayDate, noContent);

		// make sure the content or replay criteria is properly nested within the outer and criteria
		testFilter(DeltaFilesFilter.newBuilder().replayable(false).egressed(true).build(), noContent);
	}

	@Test
	void testQueryByIsReplayed() {
		DeltaFile hasReplayDate = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, MONGO_NOW, MONGO_NOW);
		hasReplayDate.setReplayed(MONGO_NOW);
		DeltaFile noReplayDate = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, MONGO_NOW, MONGO_NOW);

		deltaFileRepo.saveAll(List.of(hasReplayDate, noReplayDate));

		testFilter(DeltaFilesFilter.newBuilder().replayed(true).build(), hasReplayDate);
		testFilter(DeltaFilesFilter.newBuilder().replayed(false).build(), noReplayDate);
	}

	@Test
	void testFilterByTerminalStage() {
		DeltaFile ingress = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.IN_FLIGHT, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));
		DeltaFile enrich = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.IN_FLIGHT, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));
		DeltaFile egress = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.IN_FLIGHT, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));
		DeltaFile complete = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));
		DeltaFile error = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.ERROR, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));
		error.acknowledgeErrors(MONGO_NOW, "acked");
		DeltaFile cancelled = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.CANCELLED, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));
		deltaFileRepo.saveAll(List.of(ingress, enrich, egress, complete, error, cancelled));
		testFilter(DeltaFilesFilter.newBuilder().terminalStage(true).build(), cancelled, error, complete);
		testFilter(DeltaFilesFilter.newBuilder().terminalStage(false).build(), egress, enrich, ingress);
		testFilter(DeltaFilesFilter.newBuilder().stage(DeltaFileStage.CANCELLED).terminalStage(false).build());
		testFilter(DeltaFilesFilter.newBuilder().stage(DeltaFileStage.IN_FLIGHT).terminalStage(true).build());
	}

	@Test
	void testFilterByPendingAnnotations() {
		DeltaFile pending = buildDeltaFile(UUID.randomUUID(), "a", DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));
		pending.getFlows().getFirst().setPendingAnnotations(Set.of("a"));
		DeltaFile notPending = buildDeltaFile(UUID.randomUUID(), "a", DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));

		deltaFileRepo.saveAll(List.of(pending, notPending));
		testFilter(DeltaFilesFilter.newBuilder().pendingAnnotations(true).build(), pending);
		testFilter(DeltaFilesFilter.newBuilder().pendingAnnotations(false).build(), notPending);
	}

	@Test
	void testFilterByName() {
		DeltaFile deltaFile = new DeltaFile();
		deltaFile.setName("filename");
		deltaFile.setSchemaVersion(CURRENT_SCHEMA_VERSION);

		DeltaFile deltaFile1 = new DeltaFile();
		deltaFile1.setName("file");
		deltaFile1.setSchemaVersion(CURRENT_SCHEMA_VERSION);

		deltaFileRepo.saveAll(List.of(deltaFile1, deltaFile));

		testFilter(nameFilter("iLe", true, true));
		testFilter(nameFilter("iLe", true, false), deltaFile, deltaFile1);
		testFilter(nameFilter("ile", true, true), deltaFile, deltaFile1);
		testFilter(nameFilter("ilen", true, true), deltaFile);
		testFilter(nameFilter("^FILE", true, false), deltaFile, deltaFile1);
		testFilter(nameFilter("FILE", false, true));
		testFilter(nameFilter("FILE", false, false), deltaFile1);
		testFilter(nameFilter("file", false, true), deltaFile1);
	}

	private DeltaFilesFilter nameFilter(String filename, boolean regex, boolean caseSensitive) {
		NameFilter nameFilter = NameFilter.newBuilder().name(filename).regex(regex).caseSensitive(caseSensitive).build();
		return DeltaFilesFilter.newBuilder().nameFilter(nameFilter).build();
	}

	private void testFilter(DeltaFilesFilter filter, DeltaFile... expected) {
		DeltaFiles deltaFiles = deltaFileRepo.deltaFiles(null, 50, filter, null, null);
		assertEquals(new ArrayList<>(Arrays.asList(expected)), deltaFiles.getDeltaFiles());
	}

	@Test
	void testAnnotationKeys() {
		assertThat(deltaFilesService.annotationKeys()).isEmpty();

		DeltaFile nullKeys = new DeltaFile();
		nullKeys.setAnnotationKeys(null);
		deltaFileRepo.save(nullKeys);

		assertThat(deltaFilesService.annotationKeys()).isEmpty();

		DeltaFile emptyKeys = new DeltaFile();
		emptyKeys.setAnnotationKeys(Set.of());
		deltaFileRepo.save(emptyKeys);

		assertThat(deltaFilesService.annotationKeys()).isEmpty();

		DeltaFile withKeys = new DeltaFile();
		withKeys.setAnnotationKeys(Set.of("a", "b"));

		DeltaFile otherDomain = new DeltaFile();
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
		DeltaFile deltaFile1 = buildDeltaFile(UUID.randomUUID(), "flow1", DeltaFileStage.COMPLETE, MONGO_NOW.minusSeconds(2), MONGO_NOW.minusSeconds(2));
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile(UUID.randomUUID(), "flow2", DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.plusSeconds(2));
		deltaFileRepo.save(deltaFile2);

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
				DeltaFilesGraphQLQuery.newRequest()
						.filter(new DeltaFilesFilter())
						.limit(50)
						.offset(null)
						.orderBy(null)
						.build(),
				new DeltaFilesProjectionRoot().count().totalCount().offset().deltaFiles().did()
						.flows().publishTopics().name().type().parent().state().getParent().created().modified()
						.actions().name().id().type().parent().state().parent().created().modified().attempt());

		DeltaFiles deltaFiles = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQLQueryRequest.serialize(),
				"data." + DgsConstants.QUERY.DeltaFiles,
				new TypeRef<>() {}
		);

		assertEquals(2, deltaFiles.getCount());
		assertEquals(2, deltaFiles.getTotalCount());
		assertEquals(0, deltaFiles.getOffset());
		assertEquals(deltaFile2.getDid(), deltaFiles.getDeltaFiles().getFirst().getDid());
		assertEquals(deltaFile2.getFlows().getFirst(), deltaFiles.getDeltaFiles().getFirst().getFlows().getFirst());
		assertEquals(deltaFile1.getDid(), deltaFiles.getDeltaFiles().get(1).getDid());
		assertEquals(deltaFile1.getFlows().getFirst().getName(), deltaFiles.getDeltaFiles().get(1).getFlows().getFirst().getName());
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

		assertThat(updatedVars)
				.containsEntry("notSet", notSet)
				.containsEntry("notSetAndMasked", notSetAndMasked)
				.containsEntry("setValueAndMasked", setValueAndMasked);

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
	void launchIntegrationTest() throws IOException {
		testResultRepo.deleteAll();
		TestResult testResult = IntegrationDataFetcherTestHelper.launchIntegrationTest(
				dgsQueryExecutor,
				Resource.read("/integration/config-binary.yaml"));
		List<String> errors = List.of(
				"Could not validate config",
				"Plugin not found: org.deltafi:deltafi-core-actions:*",
				"Plugin not found: org.deltafi.testjig:deltafi-testjig:*",
				"Flow does not exist (dataSource): unarchive-passthrough-rest-data-source",
				"Flow does not exist (transformation): unarchive-passthrough-transform",
				"Flow does not exist (transformation): passthrough-transform",
				"Flow does not exist (egress): passthrough-egress"
		);
		assertEquals(TestStatus.INVALID, testResult.getStatus());
		assertEquals(errors, testResult.getErrors());
	}

	@Test
	void integrationTestCrud() {
		testResultRepo.deleteAll();
		OffsetDateTime start = OffsetDateTime.of(2024, 1, 31, 12, 0, 30, 0, ZoneOffset.UTC);
		OffsetDateTime stop = OffsetDateTime.of(2024, 1, 31, 12, 1, 30, 0, ZoneOffset.UTC);

		TestResult testResult1 = new TestResult("1", TestStatus.SUCCESSFUL, start, stop, Collections.emptyList());
		TestResult testResult2 = new TestResult("2", TestStatus.FAILED, start, stop, List.of("errors"));
		testResultRepo.saveAll(List.of(testResult1, testResult2));

		List<TestResult> listResult = IntegrationDataFetcherTestHelper.getAllIntegrationTests(dgsQueryExecutor);
		assertEquals(2, listResult.size());
		assertTrue(listResult.containsAll(List.of(testResult1, testResult2)));

		TestResult get1Result = IntegrationDataFetcherTestHelper.getIntegrationTest(dgsQueryExecutor, "1");
		assertEquals(testResult1, get1Result);

		assertFalse(IntegrationDataFetcherTestHelper.removeIntegrationTest(dgsQueryExecutor, "3"));
		assertTrue(IntegrationDataFetcherTestHelper.removeIntegrationTest(dgsQueryExecutor, "1"));

		List<TestResult> anotherListResult = IntegrationDataFetcherTestHelper.getAllIntegrationTests(dgsQueryExecutor);
		assertEquals(1, anotherListResult.size());
		assertTrue(anotherListResult.contains(testResult2));
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

		DeltaFile deltaFile1 = buildDeltaFile(UUID.randomUUID(), "flow1", DeltaFileStage.ERROR, now, plusTwo);
		deltaFileRepo.save(deltaFile1);

		DeltaFile deltaFile2 = buildDeltaFile(UUID.randomUUID(), "flow1", DeltaFileStage.COMPLETE, now, now);
		deltaFileRepo.save(deltaFile2);

		DeltaFile deltaFile3 = buildDeltaFile(UUID.randomUUID(), "flow2", DeltaFileStage.ERROR, now, minusTwo);
		deltaFileRepo.save(deltaFile3);

		DeltaFile deltaFile4 = buildDeltaFile(UUID.randomUUID(), "flow1", DeltaFileStage.ERROR, now, now);
		deltaFileRepo.save(deltaFile4);

		DeltaFile deltaFile5 = buildDeltaFile(UUID.randomUUID(), "flow3", DeltaFileStage.ERROR, now, now);
		deltaFileRepo.save(deltaFile5);

		SummaryByFlow firstPage = deltaFilesService.getErrorSummaryByFlow(
				0, 2, null, null);

		assertEquals(2, firstPage.count());
		assertEquals(0, firstPage.offset());
		assertEquals(3, firstPage.totalCount());
		assertEquals(2, firstPage.countPerFlow().size());

		assertEquals("flow1", firstPage.countPerFlow().getFirst().getFlow());
		assertEquals("flow2", firstPage.countPerFlow().get(1).getFlow());

		assertEquals(2, firstPage.countPerFlow().getFirst().getCount());
		assertEquals(1, firstPage.countPerFlow().get(1).getCount());

		assertTrue(firstPage.countPerFlow().getFirst().getDids().containsAll(List.of(deltaFile1.getDid(), deltaFile4.getDid())));
		assertTrue(firstPage.countPerFlow().get(1).getDids().contains(deltaFile3.getDid()));

		SummaryByFlow secondPage = deltaFilesService.getErrorSummaryByFlow(
				2, 2, null, null);

		assertEquals(1, secondPage.count());
		assertEquals(2, secondPage.offset());
		assertEquals(3, secondPage.totalCount());
		assertEquals(1, secondPage.countPerFlow().size());

		assertEquals("flow3", secondPage.countPerFlow().getFirst().getFlow());
		assertEquals(1, secondPage.countPerFlow().getFirst().getCount());
		assertTrue(secondPage.countPerFlow().getFirst().getDids().contains(deltaFile5.getDid()));

		DeltaFile deltaFile6 = buildDeltaFile(UUID.randomUUID(), "flow3", DeltaFileStage.ERROR, now, minusTwo);
		deltaFileRepo.save(deltaFile6);

		DeltaFile deltaFile7 = buildDeltaFile(UUID.randomUUID(), "flow3", DeltaFileStage.ERROR, now, minusTwo);
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

		assertEquals(2, filterByTime.countPerFlow().getFirst().getCount());
		assertEquals(1, filterByTime.countPerFlow().get(1).getCount());

		assertEquals("flow3", filterByTime.countPerFlow().getFirst().getFlow());
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
		assertEquals(2, filterByFlow.countPerFlow().getFirst().getCount());
		assertEquals("flow3", filterByFlow.countPerFlow().getFirst().getFlow());

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

		DeltaFile deltaFile1 = buildDeltaFile(UUID.randomUUID(), "flow1", DeltaFileStage.ERROR, now, null);
		deltaFileRepo.save(deltaFile1);

		DeltaFile deltaFile2 = buildDeltaFile(UUID.randomUUID(), "flow1", DeltaFileStage.COMPLETE, now, now);
		deltaFileRepo.save(deltaFile2);

		DeltaFile deltaFile3 = buildDeltaFile(UUID.randomUUID(), "flow2", DeltaFileStage.ERROR, now, null);
		deltaFileRepo.save(deltaFile3);

		DeltaFile deltaFile4 = buildDeltaFile(UUID.randomUUID(), "flow1", DeltaFileStage.ERROR, now, now);
		deltaFileRepo.save(deltaFile4);

		DeltaFile deltaFile5 = buildDeltaFile(UUID.randomUUID(), "flow3", DeltaFileStage.ERROR, now, null);
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
		assertEquals(1, resultsAck.countPerFlow().getFirst().getCount());
		assertEquals("f3", resultsAck.countPerFlow().getFirst().getFlow());
		assertTrue(resultsAck.countPerFlow().getFirst().getDids().contains(DIDS.get(5)));

		ErrorSummaryFilter filterNoAck = ErrorSummaryFilter.builder()
				.errorAcknowledged(false)
				.flow("f3")
				.build();

		SummaryByFlow resultsNoAck = deltaFilesService.getErrorSummaryByFlow(
				0, 99, filterNoAck, null);

		assertEquals(1, resultsNoAck.count());
		assertEquals(1, resultsNoAck.countPerFlow().size());
		assertEquals(2, resultsNoAck.countPerFlow().getFirst().getCount());
		assertEquals("f3", resultsNoAck.countPerFlow().getFirst().getFlow());
		assertTrue(resultsNoAck.countPerFlow().getFirst().getDids().containsAll(List.of(DIDS.get(6), DIDS.get(7))));

		ErrorSummaryFilter filterFlowOnly = ErrorSummaryFilter.builder()
				.flow("f3")
				.build();

		SummaryByFlow resultsForFlow = deltaFilesService.getErrorSummaryByFlow(
				0, 99, filterFlowOnly, null);

		assertEquals(1, resultsForFlow.count());
		assertEquals(1, resultsForFlow.countPerFlow().size());
		assertEquals(3, resultsForFlow.countPerFlow().getFirst().getCount());
		assertEquals("f3", resultsForFlow.countPerFlow().getFirst().getFlow());
		assertTrue(resultsForFlow.countPerFlow().getFirst().getDids().containsAll(List.of(DIDS.get(5), DIDS.get(6), DIDS.get(7))));
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
		List<UUID> dids = loadFilteredDeltaFiles(plusTwo);

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
		List<UUID> expectedDids = List.of(dids.getFirst());
		CountPerFlow message0 = actual.countPerFlow().getFirst();
		assertThat(message0.getFlow()).isEqualTo(REST_DATA_SOURCE_NAME);
		assertThat(message0.getDids()).isEqualTo(expectedDids);
	}

	@Test
	void testGetFilteredSummaryByMessageDatafetcher() {
		OffsetDateTime plusTwo = OffsetDateTime.now().plusMinutes(2);
		List<UUID> dids = loadFilteredDeltaFiles(plusTwo);

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
		List<UUID> expectedDids = List.of(dids.getFirst());
		CountPerMessage message0 = actual.countPerMessage().getFirst();
		CountPerMessage message1 = actual.countPerMessage().get(1);
		assertThat(message0.getDids()).isEqualTo(expectedDids);
		assertThat(message0.getMessage()).isEqualTo("filtered two");
		assertThat(message1.getDids()).isEqualTo(expectedDids);
		assertThat(message1.getMessage()).isEqualTo("filtered one");
	}

	private List<UUID> loadFilteredDeltaFiles(OffsetDateTime plusTwo) {
		deltaFileRepo.deleteAll();

		DeltaFile deltaFile = postTransformDeltaFile(UUID.randomUUID());
		deltaFile.setFiltered(true);
		deltaFile.getFlows().getFirst().getActions().add(filteredAction("filtered one"));
		deltaFile.getFlows().getFirst().getActions().add(filteredAction("filtered two"));

		DeltaFile tooNew = postTransformDeltaFile(UUID.randomUUID());
		tooNew.setFiltered(true);
		tooNew.setDataSource("other");
		tooNew.setModified(plusTwo);
		tooNew.getFlows().getFirst().getActions().add(filteredAction("another message"));

		DeltaFile notMarkedFiltered = postTransformDeltaFile(UUID.randomUUID());
		notMarkedFiltered.setFiltered(null);
		notMarkedFiltered.setDataSource("other");
		notMarkedFiltered.setModified(plusTwo);
		notMarkedFiltered.getFlows().getFirst().getActions().add(filteredAction("another message"));

		deltaFileRepo.saveAll(List.of(deltaFile, tooNew, notMarkedFiltered));

		return List.of(deltaFile.getDid(), tooNew.getDid(), notMarkedFiltered.getDid());
	}

	private Action filteredAction(String message) {
		Action action = new Action();
		action.setName("someAction");
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

		matchesCounterPerMessage(fullSummary, 0, "causeA", "f1", List.of(DIDS.get(3), DIDS.get(4)));
		matchesCounterPerMessage(fullSummary, 1, "causeX", "f1", List.of(DIDS.get(1)));
		matchesCounterPerMessage(fullSummary, 2, "causeX", "f2", List.of(DIDS.get(0), DIDS.get(2)));
		matchesCounterPerMessage(fullSummary, 3, "causeY", "f2", List.of(DIDS.get(2)));
		matchesCounterPerMessage(fullSummary, 4, "causeZ", "f1", List.of(DIDS.get(8)));
		matchesCounterPerMessage(fullSummary, 5, "causeZ", "f2", List.of(DIDS.get(9)));
		matchesCounterPerMessage(fullSummary, 6, "causeZ", "f3", List.of(DIDS.get(5), DIDS.get(6), DIDS.get(7)));
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

		matchesCounterPerMessage(orderByFlow, 0, "causeA", "f1", List.of(DIDS.get(3), DIDS.get(4)));
		matchesCounterPerMessage(orderByFlow, 1, "causeX", "f1", List.of(DIDS.get(1)));
		matchesCounterPerMessage(orderByFlow, 2, "causeZ", "f1", List.of(DIDS.get(8)));
		matchesCounterPerMessage(orderByFlow, 3, "causeX", "f2", List.of(DIDS.getFirst(), DIDS.get(2)));
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
		matchesCounterPerMessage(resultsBefore, 0, "causeX", "f1", List.of(DIDS.get(1)));
		matchesCounterPerMessage(resultsBefore, 1, "causeX", "f2", List.of(DIDS.get(0), DIDS.get(2)));
		matchesCounterPerMessage(resultsBefore, 2, "causeY", "f2", List.of(DIDS.get(2)));
		matchesCounterPerMessage(resultsBefore, 3, "causeZ", "f1", List.of(DIDS.get(8)));
		matchesCounterPerMessage(resultsBefore, 4, "causeZ", "f2", List.of(DIDS.get(9)));
		matchesCounterPerMessage(resultsBefore, 5, "causeZ", "f3", List.of(DIDS.get(5), DIDS.get(6), DIDS.get(7)));

		ErrorSummaryFilter filterAfter = ErrorSummaryFilter.builder()
				.modifiedAfter(plusOne).build();

		SummaryByFlowAndMessage resultAfter = deltaFilesService.getErrorSummaryByMessage(
				0, 99, filterAfter, null);

		assertEquals(0, resultAfter.offset());
		assertEquals(1, resultAfter.count());
		assertEquals(1, resultAfter.totalCount());
		assertEquals(1, resultAfter.countPerMessage().size());
		matchesCounterPerMessage(resultAfter, 0, "causeA", "f1", List.of(DIDS.get(3), DIDS.get(4)));
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
		matchesCounterPerMessage(firstPage, 0, "causeX", "f2", List.of(DIDS.get(0), DIDS.get(2)));
		matchesCounterPerMessage(firstPage, 1, "causeZ", "f2", List.of(DIDS.get(9)));

		SummaryByFlowAndMessage pageTwo = deltaFilesService.getErrorSummaryByMessage(
				2, 2, filter, order);

		assertEquals(2, pageTwo.offset());
		assertEquals(1, pageTwo.count());
		assertEquals(3, pageTwo.totalCount());
		assertEquals(1, pageTwo.countPerMessage().size());
		matchesCounterPerMessage(pageTwo, 0, "causeY", "f2", List.of(DIDS.get(2)));

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

	private static final List<UUID> DIDS = Stream.generate(UUID::randomUUID)
			.limit(12)
			.toList();

	private void loadDeltaFilesWithActionErrors(OffsetDateTime now, OffsetDateTime later) {
		// causeX, f1: 1, f2: 2
		// _AND_ causeY, f2: 1
		deltaFileRepo.save(Util.buildErrorDeltaFile(
				DIDS.getFirst(), "f2", "causeX", "x", now, now, null));
		deltaFileRepo.save(Util.buildErrorDeltaFile(
				DIDS.get(1), "f1", "causeX", "x", now));
		deltaFileRepo.save(Util.buildErrorDeltaFile(
				DIDS.get(2), "f2", "causeX", "x", now, now, "causeY"));

		// causeA, f1: 2
		deltaFileRepo.save(Util.buildErrorDeltaFile(
				DIDS.get(3), "f1", "causeA", "x", now, later, null));
		deltaFileRepo.save(Util.buildErrorDeltaFile(
				DIDS.get(4), "f1", "causeA", "x", now, later, null));

		// causeZ, f2: 1, f3: 3. f1: 1 (which is not the last action)
		DeltaFile deltaFileWithAck = Util.buildErrorDeltaFile(
				DIDS.get(5), "f3", "causeZ", "x", now);
		deltaFileWithAck.acknowledgeErrors(now, null);
		deltaFileRepo.save(deltaFileWithAck);
		deltaFileRepo.save(Util.buildErrorDeltaFile(
				DIDS.get(6), "f3", "causeZ", "x", now));
		deltaFileRepo.save(Util.buildErrorDeltaFile(
				DIDS.get(7), "f3", "causeZ", "x", now));
		deltaFileRepo.save(Util.buildErrorDeltaFile(
				DIDS.get(8), "f1", "causeZ", "x", now, now, null));
		deltaFileRepo.save(Util.buildErrorDeltaFile(
				DIDS.get(9), "f2", "causeZ", "x", now, now, null));

		// these have no errors
		deltaFileRepo.save(buildDeltaFile(
				DIDS.get(10), "f1", DeltaFileStage.COMPLETE, now, now));
		deltaFileRepo.save(buildDeltaFile(
				DIDS.get(11), "f4", DeltaFileStage.COMPLETE, now, now));
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

		assertThat(result.isSuccess()).isTrue();
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
	void testUpdatePendingAnnotationsForFlows() {
		String flow = "flowThatChanges";
		Set<String> expectedAnnotations = Set.of("f");
		DeltaFile completeAfterChange = buildDeltaFile(UUID.randomUUID());
		completeAfterChange.getFlows().getFirst().setPendingAnnotations(expectedAnnotations);
		completeAfterChange.addAnnotations(Map.of("a", "value")); // this already has the expected annotation, the flow state should go to complete
		setupPendingAnnotations(completeAfterChange, flow, expectedAnnotations);

		DeltaFile waitingForA = buildDeltaFile(UUID.randomUUID());
		setupPendingAnnotations(waitingForA, flow, expectedAnnotations); // this does not have the expected annotation, should have a flow state of PENDING_ANNOTATIONS

		DeltaFile differentFlow = buildDeltaFile(UUID.randomUUID());
		setupPendingAnnotations(differentFlow, "otherFlow", Set.of("f2")); // should not be impacted, different flow

		deltaFileRepo.saveAll(List.of(completeAfterChange, waitingForA, differentFlow));
		deltaFilesService.updatePendingAnnotationsForFlows(flow, Set.of("a"));

		Util.assertEqualsIgnoringDates(waitingForA, deltaFilesService.getDeltaFile(waitingForA.getDid()));
		Util.assertEqualsIgnoringDates(differentFlow, deltaFilesService.getDeltaFile(differentFlow.getDid()));

		DeltaFile updated = deltaFilesService.getDeltaFile(completeAfterChange.getDid());
		assertThat(updated.pendingAnnotationFlows()).isEmpty();
		assertThat(updated.getFlows().getFirst().getState()).isEqualTo(DeltaFileFlowState.COMPLETE);
	}

	private void setupPendingAnnotations(DeltaFile deltaFile, String flowName, Set<String> pendingAnnotations) {
		DeltaFileFlow deltaFileFlow = deltaFile.getFlows().getFirst();
		deltaFileFlow.setState(DeltaFileFlowState.PENDING_ANNOTATIONS);
		deltaFileFlow.setName(flowName);
		deltaFileFlow.setType(FlowType.EGRESS);
		deltaFileFlow.setPendingAnnotations(pendingAnnotations);
	}

	private DeltaFile loadDeltaFile(UUID did) {
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
			assertThat(actionInput.getActionContext().getFlowName()).isEqualTo(forActions[i].getFlowName());
			assertThat(actionInput.getActionContext().getActionName()).isEqualTo(forActions[i].getActionName());
			assertEquals(forQueueHelper(expected, actionInput.getActionContext()), actionInput.getDeltaFileMessages().getFirst());
		}
	}

	void clearForFlowTests() {
		transformFlowRepo.deleteAll();
		transformFlowPlanRepo.deleteAll();
		transformFlowService.refreshCache();
		egressFlowRepo.deleteAll();
		egressFlowPlanRepo.deleteAll();
		egressFlowService.refreshCache();
		dataSourceRepo.deleteAll();
		dataSourcePlanRepo.deleteAll();
		dataSourceService.refreshCache();
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
		headers.add("Flow", TRANSFORM_FLOW_NAME);
		headers.add("Metadata", METADATA);
		headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM);
		headers.add(USER_HEADER, USERNAME);
		headers.add(DeltaFiConstants.PERMISSIONS_HEADER, DeltaFiConstants.ADMIN_PERMISSION);
		HttpEntity<byte[]> request = new HttpEntity<>(body, headers);

		return restTemplate.postForEntity("/deltafile/ingress", request, String.class);
	}

	@Test
	@SneakyThrows
	void testIngressFromAction() {
		UUID taskedDid = UUID.randomUUID();
		UUID did = UUID.randomUUID();

		dataSourceService.setLastRun(TIMED_DATA_SOURCE_NAME, OffsetDateTime.now(), taskedDid);
		deltaFilesService.handleActionEvent(actionEvent("ingress", taskedDid, did));

		verifyActionEventResults(ingressedFromAction(did, TIMED_DATA_SOURCE_NAME),
				ActionContext.builder().flowName("sampleTransform").actionName("Utf8TransformAction").build());

		Map<String, String> tags = tagsFor(ActionEventType.INGRESS, "SampleTimedIngressAction", TIMED_DATA_SOURCE_NAME, null);
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.FILES_IN, 1).addTags(tags));
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.BYTES_IN, 36).addTags(tags));

		extendTagsForAction(tags, "type");
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.ACTION_EXECUTION_TIME_MS, 1).addTags(tags));

		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	@SneakyThrows
	void testIngressErrorFromAction() {
		UUID taskedDid = UUID.randomUUID();
		UUID did = UUID.randomUUID();

		dataSourceService.setLastRun(TIMED_DATA_SOURCE_ERROR_NAME, OffsetDateTime.now(), taskedDid);
		deltaFilesService.handleActionEvent(actionEvent("ingressError", taskedDid, did));

		DeltaFile actual = deltaFilesService.getDeltaFile(did);
		DeltaFile expected = ingressedFromActionWithError(did);
		assertEqualsIgnoringDates(expected, actual);

		Map<String, String> tags = tagsFor(ActionEventType.INGRESS, "SampleTimedIngressErrorAction", TIMED_DATA_SOURCE_ERROR_NAME, null);
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
		UUID did1 = UUID.randomUUID();
		Content content1 = new Content(FILENAME, MEDIA_TYPE, new Segment(UUID.randomUUID(), 0, CONTENT_DATA.length(), did1));
		UUID did2 = UUID.randomUUID();
		Content content2 = new Content(FILENAME, MEDIA_TYPE, new Segment(UUID.randomUUID(), 0, CONTENT_DATA.length(), did2));
		List<IngressResult> ingressResults = List.of(
				new IngressResult(TRANSFORM_FLOW_NAME, did1, content1),
				new IngressResult(TRANSFORM_FLOW_NAME, did2, content2));

		Mockito.when(ingressService.ingress(eq(TRANSFORM_FLOW_NAME), eq(FILENAME), eq(MEDIA_TYPE), eq(USERNAME), eq(METADATA), any(), any()))
				.thenReturn(ingressResults);

		ResponseEntity<String> response = ingress(FILENAME, CONTENT_DATA.getBytes());
		assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());
		assertEquals(String.join(",", did1.toString(), did2.toString()), response.getBody());
	}

	@Test
	@SneakyThrows
	void testIngress_missingFilename() {
		Mockito.when(ingressService.ingress(eq(TRANSFORM_FLOW_NAME), isNull(), eq(MEDIA_TYPE), eq(USERNAME), eq(METADATA), any(), any()))
				.thenThrow(new IngressMetadataException(""));

		ResponseEntity<String> response = ingress(null, CONTENT_DATA.getBytes());
		assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatusCode().value());
	}

	@Test
	@SneakyThrows
	void testIngress_disabled() {
		Mockito.when(ingressService.ingress(eq(TRANSFORM_FLOW_NAME), eq(FILENAME), eq(MEDIA_TYPE), eq(USERNAME), eq(METADATA), any(), any()))
				.thenThrow(new IngressUnavailableException(""));

		ResponseEntity<String> response = ingress(FILENAME, CONTENT_DATA.getBytes());
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), response.getStatusCode().value());
	}

	@Test
	@SneakyThrows
	void testIngress_storageLimit() {
		Mockito.when(ingressService.ingress(eq(TRANSFORM_FLOW_NAME), eq(FILENAME), eq(MEDIA_TYPE), eq(USERNAME), eq(METADATA), any(), any()))
				.thenThrow(new IngressStorageException(""));

		ResponseEntity<String> response = ingress(FILENAME, CONTENT_DATA.getBytes());
		assertEquals(HttpStatus.INSUFFICIENT_STORAGE.value(), response.getStatusCode().value());
	}

	@Test
	@SneakyThrows
	void testIngress_internalServerError() {
		Mockito.when(ingressService.ingress(eq(TRANSFORM_FLOW_NAME), eq(FILENAME), eq(MEDIA_TYPE), eq(USERNAME), eq(METADATA), any(), any()))
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
		DeltaFile deltaFile1 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, MONGO_NOW.minusSeconds(2), MONGO_NOW.minusSeconds(2));
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.plusSeconds(2));
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = buildDeltaFile(UUID.randomUUID(), null, DeltaFileStage.COMPLETE, MONGO_NOW.plusSeconds(2), MONGO_NOW.plusSeconds(2));
		deltaFileRepo.save(deltaFile3);

		List<UUID> dids = new ArrayList<>();
		dids.add(deltaFile1.getDid());
		dids.add(deltaFile3.getDid());
		for (int i = 0; i < 3000; i++) {
			dids.add(UUID.randomUUID());
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
					.did(UUID.randomUUID())
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
					.did(UUID.randomUUID())
					.created(OffsetDateTime.now())
					.totalBytes(1)
					.stage(DeltaFileStage.ERROR)
					.flows(List.of(DeltaFileFlow.builder().actions(List.of(
						Action.builder().state(ERROR).build())).build()))
					.build();
		error.updateFlags();

		DeltaFile complete = DeltaFile.builder()
				.did(UUID.randomUUID())
				.created(OffsetDateTime.now())
				.totalBytes(2)
				.stage(DeltaFileStage.COMPLETE)
				.flows(List.of(DeltaFileFlow.builder().actions(List.of(
						Action.builder().state(COMPLETE).build())).build()))
				.build();
		complete.updateFlags();

		DeltaFile errorAcked = DeltaFile.builder()
				.did(UUID.randomUUID())
				.created(OffsetDateTime.now())
				.totalBytes(4)
				.stage(DeltaFileStage.ERROR)
				.flows(List.of(DeltaFileFlow.builder().actions(List.of(
						Action.builder().state(ERROR).errorAcknowledged(OffsetDateTime.now()).build())).build()))
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
				.did(UUID.randomUUID())
				.created(OffsetDateTime.now())
				.totalBytes(1)
				.stage(DeltaFileStage.COMPLETE)
				.schemaVersion(CURRENT_SCHEMA_VERSION)
				.flows(List.of())
				.build();
		complete.updateFlags();

		DeltaFile contentDeleted = DeltaFile.builder()
				.did(UUID.randomUUID())
				.created(OffsetDateTime.now())
				.totalBytes(2)
				.stage(DeltaFileStage.COMPLETE)
				.contentDeleted(OffsetDateTime.now())
				.schemaVersion(CURRENT_SCHEMA_VERSION)
				.flows(List.of())
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

		DeltaFile deltaFile1 = Util.emptyDeltaFile(UUID.randomUUID(), "flow", List.of());
		deltaFile1.setTotalBytes(1L);
		deltaFile1.setReferencedBytes(2L);
		deltaFile1.setStage(DeltaFileStage.IN_FLIGHT);
		deltaFile1.setInFlight(true);

		DeltaFile deltaFile2 = Util.emptyDeltaFile(UUID.randomUUID(), "flow", List.of());
		deltaFile2.setTotalBytes(2L);
		deltaFile2.setReferencedBytes(4L);
		deltaFile2.setContentDeleted(OffsetDateTime.now());
		deltaFile2.setStage(DeltaFileStage.IN_FLIGHT);
		deltaFile2.setInFlight(true);

		DeltaFile deltaFile3 = Util.emptyDeltaFile(UUID.randomUUID(), "flow", List.of());
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
	void testTransformUtf8() throws IOException {
		UUID did = UUID.randomUUID();
		deltaFileRepo.save(postIngressDeltaFile(did));

		deltaFilesService.handleActionEvent(actionEvent("transformUtf8", did));

		verifyActionEventResults(postTransformUtf8DeltaFile(did),
				ActionContext.builder().flowName("sampleTransform").actionName("SampleTransformAction").build());

		verifyCommonMetrics(ActionEventType.TRANSFORM, "Utf8TransformAction", REST_DATA_SOURCE_NAME, null, "type");
	}

	@Test
	void testTransform() throws IOException {
		UUID did = UUID.randomUUID();
		deltaFileRepo.save(postTransformUtf8DeltaFile(did));

		deltaFilesService.handleActionEvent(actionEvent("transform", did));

		verifyActionEventResults(postTransformDeltaFile(did),
				ActionContext.builder().flowName(EGRESS_FLOW_NAME).actionName(SAMPLE_EGRESS_ACTION).build());

		verifyCommonMetrics(ActionEventType.TRANSFORM, "SampleTransformAction", REST_DATA_SOURCE_NAME, null, "type");
	}

	@Test
	void testResumeNoSubscribers() throws IOException {
		UUID did = UUID.randomUUID();
		deltaFileRepo.save(postTransformUtf8NoSubscriberDeltaFile(did));

		List<RetryResult> retryResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("resumeNoSubscribers"), did),
				"data." + DgsConstants.MUTATION.Resume,
				new TypeRef<>() {});

		assertEquals(1, retryResults.size());
		assertEquals(did, retryResults.getFirst().getDid());
		assertTrue(retryResults.getFirst().getSuccess());

		DeltaFile expected = postResumeNoSubscribersDeltaFile(did);
		verifyActionEventResults(expected, ActionContext.builder().flowName(EGRESS_FLOW_NAME).actionName(SAMPLE_EGRESS_ACTION).build());
	}

	@Test
	void testEgress() throws IOException {
		UUID did = UUID.randomUUID();
		deltaFileRepo.save(postTransformDeltaFile(did));

		deltaFilesService.handleActionEvent(actionEvent("egress", did));

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertEqualsIgnoringDates(postEgressDeltaFile(did), deltaFile);

		Mockito.verify(actionEventQueue, never()).putActions(any(), anyBoolean());
		Map<String, String> tags = tagsFor(ActionEventType.EGRESS, "SampleEgressAction", REST_DATA_SOURCE_NAME, EGRESS_FLOW_NAME);
		Map<String, String> classTags = tagsFor(ActionEventType.EGRESS, "SampleEgressAction", REST_DATA_SOURCE_NAME, EGRESS_FLOW_NAME);
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
	void testSplit() throws IOException {
		UUID did = UUID.randomUUID();
		DeltaFile postUtf8Transform = postTransformUtf8DeltaFile(did);
		deltaFileRepo.save(postUtf8Transform);

		deltaFilesService.handleActionEvent(actionEvent("split", did));

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertEquals(DeltaFileStage.COMPLETE, deltaFile.getStage());
		assertEquals(2, deltaFile.getChildDids().size());
		assertEquals(ActionState.SPLIT, deltaFile.getFlows().getLast().lastAction().getState());

		List<DeltaFile> children = deltaFilesService.deltaFiles(0, 50, DeltaFilesFilter.newBuilder().dids(deltaFile.getChildDids()).build(), DeltaFileOrder.newBuilder().field("created").direction(DeltaFileDirection.ASC).build()).getDeltaFiles();
		assertEquals(2, children.size());
		assertEquals(List.of("child1", "child2"), children.stream().map(DeltaFile::getName).sorted().toList());

		DeltaFile child1 = children.stream().filter(d -> d.getName().equals("child1")).findFirst().orElseThrow();
		assertEquals(DeltaFileStage.IN_FLIGHT, child1.getStage());
		assertNull(child1.getFlows().getFirst().getTestModeReason());
		assertEquals(Collections.singletonList(deltaFile.getDid()), child1.getParentDids());
		assertEquals(0, child1.getFlows().getFirst().lastCompleteAction().orElseThrow().getContent().getFirst().getSegments().getFirst().getOffset());

		DeltaFile child2 = children.stream().filter(d -> d.getName().equals("child2")).findFirst().orElseThrow();
		assertEquals(DeltaFileStage.IN_FLIGHT, child2.getStage());
		assertNull(child2.getFlows().getFirst().getTestModeReason());
		assertEquals(Collections.singletonList(deltaFile.getDid()), child2.getParentDids());
		assertEquals(100, child2.getFlows().getFirst().lastCompleteAction().orElseThrow().getContent().getFirst().getSegments().getFirst().getOffset());

		Mockito.verify(actionEventQueue).putActions(actionInputListCaptor.capture(), anyBoolean());
		assertEquals(2, actionInputListCaptor.getValue().size());
		assertEquals(child1.getDid(), actionInputListCaptor.getValue().getFirst().getActionContext().getDid());
		assertEquals(child2.getDid(), actionInputListCaptor.getValue().get(1).getActionContext().getDid());

		verifyCommonMetrics(ActionEventType.TRANSFORM, "SampleTransformAction", REST_DATA_SOURCE_NAME, null, "type");
	}

	@Test
	void testCountUnacknowledgedErrors() {
		List<DeltaFile> deltaFiles = new ArrayList<>();
		for (int i = 0; i < 50005; i++) {
			DeltaFile deltaFile = Util.buildDeltaFile(UUID.randomUUID(), List.of());
			deltaFile.getFlow("myFlow", 0).getAction("IngressAction", 0).setState(ERROR);
			deltaFile.setStage(DeltaFileStage.ERROR);

			if (i >= 50001) {
				deltaFile.acknowledgeErrors(OffsetDateTime.now(), "acked");
			}
			deltaFiles.add(deltaFile);
		}
		deltaFileRepo.saveAll(deltaFiles);

		assertEquals(50005, deltaFileRepo.count());
		assertEquals(50001, deltaFilesService.countUnacknowledgedErrors());
	}

	@Test
	void queuesCollectingTransformActionOnMaxNum() {
		TransformFlow transformFlow = collectingTransformFlow("coll-max-num", new CollectConfiguration(Duration.parse("PT1H"), null, 2, null));
		RestDataSource restDataSource = buildDataSource(COLLECT_TOPIC);
		transformFlowRepo.insert(transformFlow);
		transformFlowService.refreshCache();
		dataSourceRepo.insert(restDataSource);
		dataSourceService.refreshCache();

		IngressEventItem ingress1 = new IngressEventItem(UUID.randomUUID(), FILENAME, restDataSource.getName(), null,
				Collections.emptyList());
		deltaFilesService.ingress(restDataSource, ingress1, OffsetDateTime.now(), OffsetDateTime.now());

		IngressEventItem ingress2 = new IngressEventItem(UUID.randomUUID(), "file-2", restDataSource.getName(), null,
				Collections.emptyList());
		deltaFilesService.ingress(restDataSource, ingress2, OffsetDateTime.now(), OffsetDateTime.now());

		Mockito.verify(actionEventQueue).putActions(actionInputListCaptor.capture(), Mockito.anyBoolean());
		verifyActionInputs(actionInputListCaptor.getValue(), ingress1.getDid(), ingress2.getDid(), transformFlow.getName());
	}

	private void verifyActionInputs(List<ActionInput> actionInputs, UUID did1, UUID did2, String actionFlow) {
		assertThat(actionInputs).hasSize(1);

		DeltaFile parent1 = deltaFileRepo.findById(did1).orElseThrow();
		Action action = parent1.getFlow(actionFlow, 1).actionNamed(DeltaFiCoreApplicationTests.COLLECTING_TRANSFORM_ACTION).orElseThrow();
		assertEquals(ActionState.COLLECTING, action.getState());

		DeltaFile parent2 = deltaFileRepo.findById(did2).orElseThrow();
		action = parent2.getFlow(actionFlow, 1).actionNamed(DeltaFiCoreApplicationTests.COLLECTING_TRANSFORM_ACTION).orElseThrow();
		assertEquals(ActionState.COLLECTING, action.getState());

		ActionInput actionInput = actionInputs.getFirst();
		assertEquals(2, actionInput.getDeltaFileMessages().size());
		assertEquals(forQueueHelper(parent1.getFlows().getLast()), actionInput.getDeltaFileMessages().getFirst());
		assertEquals(forQueueHelper(parent2.getFlows().getLast()), actionInput.getDeltaFileMessages().get(1));
	}

	private DeltaFileMessage forQueueHelper(DeltaFile deltaFile, ActionContext actionContext) {
		DeltaFileFlow deltaFileFlow = deltaFile.getFlow(actionContext.getFlowName(), actionContext.getFlowId());
		return forQueueHelper(deltaFileFlow);
	}

	private DeltaFileMessage forQueueHelper(DeltaFileFlow deltaFileFlow) {
		return new DeltaFileMessage(deltaFileFlow.getMetadata(), deltaFileFlow.lastContent());
	}

	@Test
	void queuesCollectingTransformActionOnTimeout() {
		TransformFlow transformFlow = collectingTransformFlow("coll-on-timeout", new CollectConfiguration(Duration.parse("PT3S"), null, 5, null));
		RestDataSource restDataSource = buildDataSource(COLLECT_TOPIC);
		transformFlowRepo.insert(transformFlow);
		transformFlowService.refreshCache();
		dataSourceRepo.insert(restDataSource);
		dataSourceService.refreshCache();
		String dataSourceName = restDataSource.getName();

		IngressEventItem ingress1 = new IngressEventItem(UUID.randomUUID(), FILENAME, dataSourceName, null,
				Collections.emptyList());
		deltaFilesService.ingress(restDataSource, ingress1, OffsetDateTime.now(), OffsetDateTime.now());

		IngressEventItem ingress2 = new IngressEventItem(UUID.randomUUID(), "file-2", dataSourceName, null,
				Collections.emptyList());
		deltaFilesService.ingress(restDataSource, ingress2, OffsetDateTime.now(), OffsetDateTime.now());

		Mockito.verify(actionEventQueue, Mockito.timeout(5000))
				.putActions(actionInputListCaptor.capture(), Mockito.anyBoolean());

		verifyActionInputs(actionInputListCaptor.getValue(), ingress1.getDid(), ingress2.getDid(), transformFlow.getName());
	}

	@Test
	void queuesCollectingTransformActionOnMaxNumGrouping() {
		TransformFlow transformFlow = collectingTransformFlow("col-max-num-grouping", new CollectConfiguration(Duration.parse("PT1H"), null, 2, "a"));
		RestDataSource restDataSource = buildDataSource(COLLECT_TOPIC);
		transformFlow.getFlowStatus().setState(FlowState.RUNNING);
		String dataSourceName = restDataSource.getName();
		String transformFlowName = transformFlow.getName();

		transformFlowRepo.insert(transformFlow);
		transformFlowService.refreshCache();
		dataSourceRepo.insert(restDataSource);
		dataSourceService.refreshCache();

		IngressEventItem ingress1 = new IngressEventItem(UUID.randomUUID(),
				FILENAME, dataSourceName, Map.of("a", "1"), Collections.emptyList());
		deltaFilesService.ingress(restDataSource, ingress1, OffsetDateTime.now(), OffsetDateTime.now());

		IngressEventItem ingress2 = new IngressEventItem(UUID.randomUUID(),
				"file-2", dataSourceName, Map.of("a", "2"), Collections.emptyList());
		deltaFilesService.ingress(restDataSource, ingress2, OffsetDateTime.now(), OffsetDateTime.now());

		IngressEventItem ingress3 = new IngressEventItem(UUID.randomUUID(),
				"file-3", dataSourceName, Map.of("a", "2"), Collections.emptyList());
		deltaFilesService.ingress(restDataSource, ingress3, OffsetDateTime.now(), OffsetDateTime.now());

		IngressEventItem ingress4 = new IngressEventItem(UUID.randomUUID(),
				"file-4", dataSourceName, Map.of("a", "1"), Collections.emptyList());
		deltaFilesService.ingress(restDataSource, ingress4, OffsetDateTime.now(), OffsetDateTime.now());

		Mockito.verify(actionEventQueue, Mockito.times(2))
				.putActions(actionInputListCaptor.capture(), Mockito.anyBoolean());
		List<List<ActionInput>> actionInputLists = actionInputListCaptor.getAllValues();
		verifyActionInputs(actionInputLists.getFirst(), ingress2.getDid(), ingress3.getDid(), transformFlowName);
		verifyActionInputs(actionInputLists.get(1), ingress1.getDid(), ingress4.getDid(), transformFlowName);
	}

	@Test
	void failsCollectingTransformActionOnMinNum() {
		TransformFlow transformFlow = collectingTransformFlow("col-fail-min-num", new CollectConfiguration(Duration.parse("PT3S"), 3, 5, null));
		String transformFlowName = transformFlow.getName();
		RestDataSource restDataSource = buildDataSource(COLLECT_TOPIC);
		transformFlowRepo.insert(transformFlow);
		transformFlowService.refreshCache();
		dataSourceRepo.insert(restDataSource);
		dataSourceService.refreshCache();
		String dataSourceName = restDataSource.getName();

		IngressEventItem ingress1 = new IngressEventItem(UUID.randomUUID(), FILENAME, dataSourceName, null,
				Collections.emptyList());
		deltaFilesService.ingress(restDataSource, ingress1, OffsetDateTime.now(), OffsetDateTime.now());

		IngressEventItem ingress2 = new IngressEventItem(UUID.randomUUID(), "file-2", dataSourceName, null,
				Collections.emptyList());
		deltaFilesService.ingress(restDataSource, ingress2, OffsetDateTime.now(), OffsetDateTime.now());

		await().atMost(5, TimeUnit.SECONDS).until(() -> hasErroredCollectingAction(ingress1.getDid(), transformFlowName));
		await().atMost(5, TimeUnit.SECONDS).until(() -> hasErroredCollectingAction(ingress2.getDid(), transformFlowName));
	}

	private boolean hasErroredCollectingAction(UUID did, String actionFlow) {
		DeltaFile deltaFile = deltaFileRepo.findById(did).orElseThrow();
		Action action = deltaFile.getFlow(actionFlow, 1).actionNamed(COLLECTING_TRANSFORM_ACTION).orElseThrow();
		return action.getState() == ActionState.ERROR;
	}

	@Test
	void testResumeAggregate() throws IOException {
		TransformFlow transformFlow = collectingTransformFlow("col-resume", new CollectConfiguration(Duration.parse("PT1H"), null, 2, null));
		transformFlowRepo.insert(transformFlow);
		transformFlowService.refreshCache();

		UUID did = UUID.randomUUID();

		DeltaFile parent1 = Util.emptyDeltaFile(UUID.randomUUID(), transformFlow.getName());
		parent1.getFlows().getFirst().setCollectId(did);
		deltaFileRepo.save(parent1);
		DeltaFile parent2 = Util.emptyDeltaFile(UUID.randomUUID(), transformFlow.getName());
		parent2.getFlows().getFirst().setCollectId(did);
		deltaFileRepo.save(parent2);

		List<UUID> parentDids = List.of(parent1.getDid(), parent2.getDid());
		DeltaFile aggregate = Util.emptyDeltaFile(did, transformFlow.getName());
		aggregate.setCollectId(did);
		aggregate.setParentDids(parentDids);
		aggregate.setStage(DeltaFileStage.ERROR);

		DeltaFileFlow aggregateFlow = aggregate.getFlows().getFirst();
		aggregateFlow.setType(FlowType.TRANSFORM);
		aggregateFlow.queueNewAction(COLLECTING_TRANSFORM_ACTION, ActionType.TRANSFORM, false, OffsetDateTime.now(clock));
		aggregateFlow.actionNamed(COLLECTING_TRANSFORM_ACTION).orElseThrow().error(START_TIME, STOP_TIME, OffsetDateTime.now(clock), "collect action failed", "message");
		aggregateFlow.setActionConfigurations(transformFlow.allActionConfigurations());
		deltaFileRepo.save(aggregate);

		List<RetryResult> retryResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("resumeAggregate"), did),
				"data." + DgsConstants.MUTATION.Resume,
				new TypeRef<>() {});

		assertEquals(1, retryResults.size());
		assertEquals(did, retryResults.getFirst().getDid());
		assertTrue(retryResults.getFirst().getSuccess());

		Mockito.verify(actionEventQueue).putActions(actionInputListCaptor.capture(), Mockito.anyBoolean());
		List<ActionInput> actionInputs = actionInputListCaptor.getValue();
		assertThat(actionInputs).hasSize(1);

		ActionInput actionInput = actionInputs.getFirst();
		assertEquals(parentDids, actionInput.getActionContext().getCollectedDids());
		assertEquals(forQueueHelper(parent1.getFlows().getFirst()), actionInput.getDeltaFileMessages().getFirst());
		assertEquals(forQueueHelper(parent2.getFlows().getFirst()), actionInput.getDeltaFileMessages().get(1));
	}

	private TransformFlow collectingTransformFlow(String name, CollectConfiguration configuration) {
		TransformFlow transformFlow = new TransformFlow();
		transformFlow.setName(name);
		TransformActionConfiguration transformAction = new TransformActionConfiguration(COLLECTING_TRANSFORM_ACTION,
				"org.deltafi.action.SomeCollectingTransformAction");
		transformAction.setCollect(configuration);
		transformFlow.getTransformActions().add(transformAction);
		transformFlow.getFlowStatus().setState(FlowState.RUNNING);
		transformFlow.setSubscribe(Set.of(new Rule(COLLECT_TOPIC)));
		return transformFlow;
	}
}
