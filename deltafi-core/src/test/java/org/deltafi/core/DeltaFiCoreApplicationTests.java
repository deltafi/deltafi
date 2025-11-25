/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import io.minio.MinioClient;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.content.*;
import org.deltafi.common.lookup.LookupTable;
import org.deltafi.common.queue.valkey.ValkeyKeyedBlockingQueue;
import org.deltafi.common.resource.Resource;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.test.time.TestClock;
import org.deltafi.common.types.*;
import org.deltafi.common.types.integration.*;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.configuration.AuthProperties;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.configuration.PropertyGroup;
import org.deltafi.core.configuration.ui.Link;
import org.deltafi.core.configuration.ui.Link.LinkType;
import org.deltafi.core.datafetchers.*;
import org.deltafi.core.delete.*;
import org.deltafi.core.exceptions.*;
import org.deltafi.core.generated.DgsConstants;
import org.deltafi.core.generated.client.*;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.integration.*;
import org.deltafi.core.lookup.LookupTableService;
import org.deltafi.core.lookup.LookupTableServiceException;
import org.deltafi.core.metrics.MetricService;
import org.deltafi.core.plugin.deployer.DeployerService;
import org.deltafi.core.plugin.deployer.credential.CredentialProvider;
import org.deltafi.core.repo.*;
import org.deltafi.core.rest.AuthRest;
import org.deltafi.core.services.*;
import org.deltafi.core.services.analytics.AnalyticEventService;
import org.deltafi.core.snapshot.SystemSnapshotTestHelper;
import org.deltafi.core.types.*;
import org.deltafi.core.types.Flow;
import org.deltafi.core.types.integration.IntegrationTestEntity;
import org.deltafi.core.types.integration.TestResult;
import org.deltafi.core.types.snapshot.*;
import org.deltafi.core.types.snapshot.SystemSnapshot;
import org.deltafi.core.util.*;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.deltafi.common.constant.DeltaFiConstants.*;
import static org.deltafi.common.types.ActionState.*;
import static org.deltafi.core.datafetchers.DeletePolicyDatafetcherTestHelper.*;
import static org.deltafi.core.datafetchers.DeltaFilesDatafetcherTestHelper.*;
import static org.deltafi.core.datafetchers.FlowPlanDatafetcherTestHelper.PLUGIN_COORDINATES;
import static org.deltafi.core.datafetchers.PluginDataFetcherTestHelper.*;
import static org.deltafi.core.metrics.MetricsUtil.extendTagsForAction;
import static org.deltafi.core.metrics.MetricsUtil.tagsFor;
import static org.deltafi.core.services.DeletePolicyService.TTL_SYSTEM_POLICY;
import static org.deltafi.core.services.PluginService.SYSTEM_PLUGIN_ARTIFACT_ID;
import static org.deltafi.core.util.Constants.*;
import static org.deltafi.core.util.FlowBuilders.*;
import static org.deltafi.core.util.FullFlowExemplarService.*;
import static org.deltafi.core.util.UtilService.*;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Sql(statements = "TRUNCATE TABLE annotations, delta_file_flows, delta_files, flows, plugins, properties, resume_policies, analytics, event_annotations, flow_definitions CASCADE",
		executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class DeltaFiCoreApplicationTests {
	@Container
	public static final PostgreSQLContainer<?> POSTGRES_CONTAINER = new PostgreSQLContainer<>(
			DockerImageName.parse("deltafi/timescaledb:2.19.0-pg16").asCompatibleSubstituteFor("postgres"));

	@Container
	public static final PostgreSQLContainer<?> POSTGRES_LOOKUP_CONTAINER = new PostgreSQLContainer<>(
			DockerImageName.parse("postgres:16.9-alpine3.22").asCompatibleSubstituteFor("postgres"));

	@Container
	public static final GenericContainer<?> VALKEY_CONTAINER = new GenericContainer<>("valkey/valkey:9.0.0-alpine")
			.withExposedPorts(6379);
	public static final String SAMPLE_EGRESS_ACTION = "SampleEgressAction";
	public static final String JOINING_TRANSFORM_ACTION = "JoiningTransformAction";
	public static final String JOIN_TOPIC = "join-topic";
	public static final String SYSTEM_NAME = "systemName";
	public static final TestClock TEST_CLOCK = new TestClock();

	private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
			.registerModule(new JavaTimeModule());

    @DynamicPropertySource
	static void setProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES_CONTAINER::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES_CONTAINER::getUsername);
		registry.add("spring.datasource.password", POSTGRES_CONTAINER::getPassword);
		registry.add("valkey.url", () -> "redis://" + VALKEY_CONTAINER.getHost() + ":" + VALKEY_CONTAINER.getMappedPort(6379));
		registry.add("schedule.actionEvents", () -> false);
		registry.add("schedule.maintenance", () -> false);
		registry.add("schedule.flowSync", () -> false);
		registry.add("schedule.diskSpace", () -> false);
		registry.add("schedule.errorCount", () -> false);
		registry.add("schedule.propertySync", () -> false);
		registry.add("cold.queue.refresh.duration", () -> "PT1M");
		registry.add("lookup.enabled", () -> true);
		registry.add("lookup.datasource.url", POSTGRES_LOOKUP_CONTAINER::getJdbcUrl);
		registry.add("lookup.datasource.username", POSTGRES_LOOKUP_CONTAINER::getUsername);
		registry.add("lookup.datasource.password", POSTGRES_LOOKUP_CONTAINER::getPassword);
	}

	@Autowired
	private ScheduledJoinService scheduledJoinService;

	@Autowired
	private DeltaFileCacheService deltaFileCacheService;

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
	AnnotationRepo annotationRepo;

	@Autowired
	EventAnnotationsRepo eventAnnotationsRepo;

	@MockitoBean
	ContentStorageService contentStorageService;

	@Autowired
	DeltaFileFlowRepo deltaFileFlowRepo;

	@Autowired
	DeltaFileRepo deltaFileRepo;

	@Autowired
	DeletePolicyRepo deletePolicyRepo;

	@Autowired
	PluginRepository pluginRepository;

	@Autowired
	TimedDataSourceService timedDataSourceService;

	@Autowired
	TransformFlowService transformFlowService;

	@Autowired
	TransformFlowRepo transformFlowRepo;

	@Autowired
	RestDataSourceRepo restDataSourceRepo;

	@Autowired
	TimedDataSourceRepo timedDataSourceRepo;

	@Autowired
	OnErrorDataSourceRepo onErrorDataSourceRepo;

	@Autowired
	DataSinkRepo dataSinkRepo;

	@Autowired
	DataSinkService dataSinkService;

	@Autowired
	TestResultRepo testResultRepo;

	@Autowired
	IntegrationTestRepo integrationTestRepo;

	@Autowired
	PluginVariableRepo pluginVariableRepo;

	@Autowired
	PluginVariableService pluginVariableService;

	@Autowired
	PluginService pluginService;

	@Autowired
	ResumePolicyRepo resumePolicyRepo;

	@Autowired
	DeltaFiPropertiesRepo deltaFiPropertiesRepo;

	@Autowired
	SystemSnapshotRepo systemSnapshotRepo;

	@Autowired
	UiLinkService uiLinkService;

	@Autowired
	UiLinkRepo uiLinkRepo;

	@Autowired
	DeltaFiUserRepo userRepo;

	@Autowired
	RoleRepo roleRepo;

	@Autowired
	DeltaFiUserService userService;

	@Autowired
	RoleService roleService;

    @Autowired
    private UnifiedFlowService unifiedFlowService;

	@MockitoBean
	SystemService systemService;

	@Captor
	ArgumentCaptor<List<WrappedActionInput>> actionInputListCaptor;

	@Autowired
	TestRestTemplate restTemplate;

	@MockitoBean
	MetricService metricService;

	@MockitoBean
	CoreEventQueue coreEventQueue;

	@MockitoBean
	DeployerService deployerService;

	@Autowired
	QueueManagementService queueManagementService;

    @Autowired
    EventRepo eventRepo;

    @Autowired
    Clock clock;

	@Autowired
	FlowCacheService flowCacheService;

	@Autowired
	List<FlowService<?, ?, ?, ?>> flowServices;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	FlowDefinitionService flowDefinitionService;

	@Autowired
	FullFlowExemplarService fullFlowExemplarService;

	@Autowired
	UtilService utilService;

	@MockitoBean
	@SuppressWarnings("unused")
	PlatformService platformService;

	@MockitoBean
	@SuppressWarnings("unused")
	StorageConfigurationService storageConfigurationService;

	@MockitoBean
	@SuppressWarnings("unused")
	ValkeyKeyedBlockingQueue valkeyKeyedBlockingQueue;

	@MockitoBean
	@SuppressWarnings("unused")
	ServerSentService serverSentService;

	@MockitoBean
	@SuppressWarnings("unused")
	CredentialProvider credentialProvider;

	@MockitoBean
	@SuppressWarnings("unused")
	AnalyticEventService analyticEventService;

	private final OffsetDateTime NOW = OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC));

	abstract static class DeltaFileMixin {
		@SuppressWarnings("unused")
		@JsonDeserialize(contentAs = DeltaFileFlow.class)
		abstract Set<DeltaFileFlow> getFlows();
	}

	private JsonMapper flowMapper() {
		return JsonMapper.builder()
				.addModule(new JavaTimeModule())
				.addModule(new SimpleModule()
						.addDeserializer(DeltaFileFlow.class, new DeltaFileFlowDeserializer(flowDefinitionService)))
				.addMixIn(DeltaFile.class, DeltaFileMixin.class)
				.build();
	}

	@TestConfiguration
	public static class Config {
		@Bean
		public StorageConfigurationService storageConfigurationService(MinioClient minioClient, DeltaFiPropertiesService deltaFiPropertiesService, StorageProperties storageProperties) {
			return new StorageConfigurationService(minioClient, deltaFiPropertiesService, storageProperties);
		}

		@Bean
		public FlowDefinitionService flowDefinitionService(FlowDefinitionRepo flowDefinitionRepo) {
			return new FlowDefinitionServiceImpl(flowDefinitionRepo);
		}

		@Bean
		public Clock clock() {
			return Clock.tickMillis(ZoneOffset.UTC);
		}

		@Bean
		public AuthRest authRest(CoreAuditLogger coreAuditLogger) {
			// manually create bean to mock wiring in the domain
			return new AuthRest("local.deltafi.org", new AuthProperties("disabled"), coreAuditLogger);
		}

		@Bean
		public ScheduledJoinService scheduledJoinService(DeltaFiPropertiesService deltaFiPropertiesService, JoinEntryService joinEntryService, DeltaFilesService deltaFilesService) {
			return new ScheduledJoinService(TEST_CLOCK, deltaFiPropertiesService, joinEntryService, deltaFilesService);
		}

		@Bean
		public RoleService roleService(RoleRepo roleRepo, PermissionsService permissionsService) {
			return new RoleService(roleRepo, permissionsService, true);
		}
	}

	@BeforeEach
	void setup() {
		deltaFiPropertiesService.getDeltaFiProperties().setIngressEnabled(true);
		deltaFiPropertiesService.upsertProperties();
		resumePolicyService.refreshCache();
		loadConfig();

		Mockito.clearInvocations(coreEventQueue);

		// Set the security context for the tests that use DgsQueryExecutor
		SecurityContextHolder.clearContext();
		SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
		Authentication authentication = new PreAuthenticatedAuthenticationToken("name", "pass", List.of(new SimpleGrantedAuthority(DeltaFiConstants.ADMIN_PERMISSION)));
		securityContext.setAuthentication(authentication);
		SecurityContextHolder.setContext(securityContext);

		Mockito.when(systemService.isContentStorageDepleted()).thenReturn(false);

		flowDefinitionService.clearCache();
	}

	void refreshFlowCaches() {
		flowCacheService.refreshCache();
		for (FlowService<?, ?, ?, ?> flowService : flowServices) {
			flowService.onRefreshCache();
		}
	}

	void loadConfig() {
		loadTransformConfig();
		loadEgressConfig();
		loadRestDataSources();
		loadTimedDataSources();
		loadOnErrorDataSources();
		refreshFlowCaches();
	}

	static final TransformFlow SAMPLE_TRANSFORM_FLOW;
	static {
		TransformFlow sampleTransformFlow = buildRunningTransformFlow(TRANSFORM_FLOW_NAME, TRANSFORM_ACTIONS, false);
		sampleTransformFlow.setSubscribe(Set.of(new Rule(TRANSFORM_TOPIC)));
		sampleTransformFlow.setPublish(publishRules(EGRESS_TOPIC));
		SAMPLE_TRANSFORM_FLOW = sampleTransformFlow;
	}
	static final TransformFlow RETRY_FLOW = buildRunningTransformFlow("theTransformFlow", null, false);
	static final TransformFlow CHILD_FLOW = buildRunningTransformFlow("transformChildFlow", List.of(TRANSFORM2), false);

	void loadTransformConfig() {
		transformFlowRepo.batchInsert(List.of(SAMPLE_TRANSFORM_FLOW, RETRY_FLOW, CHILD_FLOW));
	}

	static final DataSink SAMPLE_EGRESS_FLOW;
	static final DataSink ERROR_EGRESS_FLOW;
	static {
		DataSink sampleDataSink = buildRunningDataSink(DATA_SINK_FLOW_NAME, EGRESS, false);
		sampleDataSink.setSubscribe(Set.of(new Rule(EGRESS_TOPIC)));
		SAMPLE_EGRESS_FLOW = sampleDataSink;

		ActionConfiguration errorEgress = new ActionConfiguration("ErrorEgressAction", ActionType.EGRESS, "type");
		ERROR_EGRESS_FLOW = buildRunningDataSink("error", errorEgress, false);
	}

	void loadEgressConfig() {
		dataSinkRepo.batchInsert(List.of(SAMPLE_EGRESS_FLOW, ERROR_EGRESS_FLOW));
	}

	static final RestDataSource REST_DATA_SOURCE = buildRestDataSource(FlowState.RUNNING);

	void loadRestDataSources() {
		restDataSourceRepo.batchInsert(List.of(REST_DATA_SOURCE));
	}

	static final TimedDataSource TIMED_DATA_SOURCE = buildTimedDataSource(FlowState.RUNNING);
	static final TimedDataSource TIMED_DATA_SOURCE_WITH_ANNOT = buildTimedDataSourceWithAnnotationConfig(FlowState.RUNNING);
	static final TimedDataSource TIMED_DATA_SOURCE_ERROR = buildTimedDataSourceError(FlowState.RUNNING);

	void loadTimedDataSources() {
		timedDataSourceRepo.batchInsert(List.of(TIMED_DATA_SOURCE, TIMED_DATA_SOURCE_WITH_ANNOT, TIMED_DATA_SOURCE_ERROR));
	}

	static final OnErrorDataSource ON_ERROR_DATA_SOURCE = buildOnErrorDataSource(FlowState.RUNNING);

	void loadOnErrorDataSources() {
		onErrorDataSourceRepo.batchInsert(List.of(ON_ERROR_DATA_SOURCE));
	}

	@Test
	void contextLoads() {
		assertTrue(true);
		assertFalse(transformFlowService.getAll().isEmpty());
	}

	@Test
	void testReplaceAllDeletePolicies() {
		Result result = replaceAllDeletePolicies(dgsQueryExecutor);
		assertTrue(result.isSuccess());
		assertTrue(result.getErrors().isEmpty());
		assertEquals(2, deletePolicyRepo.count());
	}

	@Test
	void testRemoveDeletePolicy() {
		replaceAllDeletePolicies(dgsQueryExecutor);
		assertEquals(2, deletePolicyRepo.count());
		UUID id = getIdByPolicyName(AFTER_COMPLETE_POLICY);
		assertTrue(removeDeletePolicy(dgsQueryExecutor, id));
		assertEquals(1, deletePolicyRepo.count());
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
	void testUpdateTimedDeletePolicy() {
		loadOneDeletePolicy(dgsQueryExecutor);
		assertEquals(1, deletePolicyRepo.count());
		UUID idToUpdate = getIdByPolicyName(AFTER_COMPLETE_POLICY);

		Result validationError = updateTimedDeletePolicy(dgsQueryExecutor,
				TimedDeletePolicy.builder()
						.id(idToUpdate)
						.name("blah")
						.afterComplete("ABC")
						.enabled(false)
						.deleteMetadata(false)
						.build());
		checkUpdateResult(validationError, "Unable to parse duration for afterComplete", idToUpdate, AFTER_COMPLETE_POLICY, true);

		UUID wrongUUID = UUID.randomUUID();
		Result notFoundError = updateTimedDeletePolicy(dgsQueryExecutor,
				TimedDeletePolicy.builder()
						.id(wrongUUID)
						.name("blah")
						.afterComplete("PT1H")
						.enabled(true)
						.deleteMetadata(false)
						.build());
		checkUpdateResult(notFoundError, "policy not found", idToUpdate, AFTER_COMPLETE_POLICY, true);

		Result goodUpdate = updateTimedDeletePolicy(dgsQueryExecutor,
				TimedDeletePolicy.builder()
						.id(idToUpdate)
						.name("newTypesAndName")
						.afterComplete("PT1H")
						.enabled(false)
						.deleteMetadata(false)
						.build());
		checkUpdateResult(goodUpdate, null, idToUpdate, "newTypesAndName", false);
	}

	private void checkUpdateResult(Result result, String error, UUID id, String name, boolean enabled) {
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

		assertInstanceOf(TimedDeletePolicy.class, policyList.getFirst());
	}

	@Test
	void testGetDeletePolicies() {
		replaceAllDeletePolicies(dgsQueryExecutor);
		List<DeletePolicy> policyList = getDeletePolicies(dgsQueryExecutor);
		assertEquals(2, policyList.size());

		boolean foundAfterCompletePolicy = false;
		boolean foundOfflinePolicy = false;
		Set<UUID> ids = new HashSet<>();

		for (DeletePolicy policy : policyList) {
			ids.add(policy.getId());
			if (policy instanceof TimedDeletePolicy timedPolicy) {
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
		assertEquals(2, ids.size());
	}

	@Test
	void testDeleteRunnerPoliciesScheduled() {
		replaceAllDeletePolicies(dgsQueryExecutor);
		assertThat(deletePolicyRepo.count()).isEqualTo(2);
		List<DeletePolicyWorker> policiesScheduled = deleteRunner.refreshPolicies();
		assertThat(policiesScheduled).hasSize(2); // only 2 of 3 are enabled + the TTL policy
		List<String> names = policiesScheduled.stream().map(DeletePolicyWorker::getName).toList();
		assertThat(names).containsExactly(AFTER_COMPLETE_POLICY, TTL_SYSTEM_POLICY);
	}

	@Test
	void deletePoliciesDontRaise() {
		deleteRunner.runDeletes();
	}

	@SuppressWarnings("SameParameterValue")
	private void verifyCommonMetrics(ActionEventType actionEventType,
									 String actionName,
									 String dataSource,
									 String flowName,
									 String dataSink,
									 String className) {
		Map<String, String> tags = tagsFor(actionEventType, actionName, dataSource, dataSink);

		Map<String, String> flowTags = Map.of(
				"dataSource", dataSource,
				"flowName", flowName);

		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.FILES_OUT, 1).addTags(flowTags));
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.BYTES_OUT, 500).addTags(flowTags));

		extendTagsForAction(tags, className);
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.ACTION_EXECUTION_TIME_MS, 1).addTags(tags));
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.ACTION_EXECUTION, 1).addTags(tags));
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testResumeVersion0() throws IOException {
		UUID did = UUID.randomUUID();
		DeltaFile insertVersion0 = fullFlowExemplarService.postTransformHadErrorDeltaFile(did);
		insertVersion0.setVersion(0);
		deltaFileRepo.insertOne(insertVersion0);

		List<RetryResult> resumeResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("resumeTransform"), did),
				"data." + DgsConstants.MUTATION.Resume,
				new TypeRef<>() {});

		assertEquals(1, resumeResults.size());
		assertEquals(did, resumeResults.getFirst().getDid());
		assertTrue(resumeResults.getFirst().getSuccess());
	}

	@Test
	void testResumeTransform() throws IOException {
		UUID did = UUID.randomUUID();
		deltaFileRepo.save(fullFlowExemplarService.postTransformHadErrorDeltaFile(did));

		List<RetryResult> retryResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("resumeTransform"), did),
				"data." + DgsConstants.MUTATION.Resume,
				new TypeRef<>() {});

		assertEquals(1, retryResults.size());
		assertEquals(did, retryResults.getFirst().getDid());
		assertTrue(retryResults.getFirst().getSuccess());

		DeltaFile expected = fullFlowExemplarService.postResumeTransformDeltaFile(did);
		verifyActionEventResults(expected, ActionContext.builder().flowName(TRANSFORM_FLOW_NAME).actionName("SampleTransformAction").build());

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		deltaFile.lastFlow().lastAction().error(OffsetDateTime.now(), OffsetDateTime.now(), OffsetDateTime.now(), "cause", "context");
		deltaFile.lastFlow().setState(DeltaFileFlowState.ERROR);
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
		DeltaFile contentDeletedDeltaFile = fullFlowExemplarService.postTransformHadErrorDeltaFile(did);
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
		DeltaFile postTransformUtf8 = fullFlowExemplarService.postTransformUtf8DeltaFile(did);
		deltaFileRepo.save(postTransformUtf8);
		// the action config has type set to 'type' which would be the action queue name
		queueManagementService.getColdQueues().add("type");

		deltaFilesService.handleActionEvent(actionEvent("transform", did));
		queueManagementService.getColdQueues().remove("type"); // reset for other tests

		DeltaFile postTransformColdQueued = fullFlowExemplarService.postTransformDeltaFile(did);
		postTransformColdQueued.lastFlow().lastAction().setState(ActionState.COLD_QUEUED);
		verifyActionEventResults(postTransformColdQueued,
				ActionContext.builder().flowName(DATA_SINK_FLOW_NAME).actionName(SAMPLE_EGRESS_ACTION).build());

		verifyCommonMetrics(ActionEventType.TRANSFORM, "SampleTransformAction", REST_DATA_SOURCE_NAME, TRANSFORM_FLOW_NAME, null, "type");

		queueManagementService.getColdQueues().remove(SAMPLE_EGRESS_ACTION);
	}

	@Test
	void testColdRequeue() {
		UUID did = UUID.randomUUID();
		DeltaFile postTransform = fullFlowExemplarService.postTransformDeltaFile(did);
		postTransform.lastFlow().lastAction().setActionClass("org.plugin.SlowAction");
		DeltaFileFlow dataSink = postTransform.lastFlow();
		dataSink.lastAction().setState(ActionState.COLD_QUEUED);
		dataSink.updateState();
		deltaFileRepo.save(postTransform);

		queueManagementService.coldToWarm();

		verifyActionEventResults(fullFlowExemplarService.postTransformDeltaFile(did), ActionContext.builder().flowName(DATA_SINK_FLOW_NAME).actionName(SAMPLE_EGRESS_ACTION).build());
	}

	@Test
	void testTransformDidNotFound() throws IOException {
		UUID did = UUID.randomUUID();
		deltaFileRepo.save(fullFlowExemplarService.postTransformUtf8DeltaFile(did));
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
		deltaFileRepo.save(fullFlowExemplarService.postTransformUtf8DeltaFile(did));

		deltaFilesService.handleActionEvent(actionEvent("transformUnicode", did));

		verifyActionEventResults(fullFlowExemplarService.postTransformDeltaFileWithUnicodeAnnotation(did), ActionContext.builder().flowName(DATA_SINK_FLOW_NAME).actionName(SAMPLE_EGRESS_ACTION).build());
		verifyCommonMetrics(ActionEventType.TRANSFORM, "SampleTransformAction", REST_DATA_SOURCE_NAME, TRANSFORM_FLOW_NAME, null, "type");
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
		deltaFileRepo.save(fullFlowExemplarService.postTransformUtf8DeltaFile(did));

		deltaFilesService.handleActionEvent(actionEvent("transformWrongElement", did));

		DeltaFile afterMutation = deltaFilesService.getDeltaFile(did);
		assertEqualsIgnoringDates(fullFlowExemplarService.postTransformInvalidDeltaFile(did), afterMutation);
	}

	void runErrorWithAutoResume(Integer autoResumeDelay, String mode) throws IOException {
		UUID did = UUID.randomUUID();
		String policyName = null;
		DeltaFile original = fullFlowExemplarService.postTransformDeltaFile(did);
		deltaFileRepo.save(original);
		boolean withAnnotation = "ANNOTATIONS".equals(mode);
		boolean withMessages = "MESSAGES".equals(mode);

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
		} else if (withMessages) {
			deltaFilesService.handleActionEvent(actionEvent("errorWithMessages", did));
		} else {
			deltaFilesService.handleActionEvent(actionEvent("error", did, original.lastFlow().getId()));
		}

		DeltaFile actual = deltaFilesService.getDeltaFile(did);
		DeltaFile expected = fullFlowExemplarService.postErrorDeltaFile(did, policyName, autoResumeDelay);
		if (withAnnotation) {
			expected.addAnnotations(Map.of("errorKey", "error metadata"));
		}
		if (withMessages) {
			OffsetDateTime testTime = OffsetDateTime.of(2024, 10, 31, 12, 30, 00, 0, ZoneOffset.UTC);
			expected.setMessages(List.of(
					new LogMessage(LogSeverity.WARNING, testTime, "SampleEgressAction", "We had an issue, but it didn't stop us"),
					new LogMessage(LogSeverity.ERROR, testTime, "SampleEgressAction", "Action failed")));
			expected.setWarnings(true);
		}
		assertEqualsIgnoringDates(expected, actual);

		Map<String, String> tags = tagsFor(ActionEventType.ERROR, "SampleEgressAction", REST_DATA_SOURCE_NAME, DATA_SINK_FLOW_NAME);
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.FILES_ERRORED, 1).addTags(tags));

		extendTagsForAction(tags, "type");
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.ACTION_EXECUTION_TIME_MS, 1).addTags(tags));
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.ACTION_EXECUTION, 1).addTags(tags));
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testError() throws IOException {
		runErrorWithAutoResume(null, null);
	}

	@Test
	void testErrorWithAnnotation() throws IOException {
			runErrorWithAutoResume(null, "ANNOTATIONS");
	}

	@Test
	void testErrorWithMessages() throws IOException {
		runErrorWithAutoResume(null, "MESSAGES");
	}

	@Test
	void testAutoResume() throws IOException {
		runErrorWithAutoResume(100, null);
	}

	@Test
	@SneakyThrows
	void testOnErrorDataSourceTriggering() {
		clearForFlowTests();

		// Set up a specific OnError data source that should catch errors from the egress action
		OnErrorDataSource onErrorDataSource = new OnErrorDataSource();
		onErrorDataSource.setName("test-error-catcher");
		onErrorDataSource.getFlowStatus().setState(FlowState.RUNNING);
		onErrorDataSource.setTestMode(false);
		onErrorDataSource.setTopic("error-events");
		onErrorDataSource.setSourcePlugin(PLUGIN_COORDINATES);
		onErrorDataSource.setErrorMessageRegex(".*");
		onErrorDataSource.setSourceFilters(List.of(
			new org.deltafi.common.types.ErrorSourceFilter(null, null, "SampleEgressAction", null),
			new org.deltafi.common.types.ErrorSourceFilter(org.deltafi.common.types.FlowType.DATA_SINK, DATA_SINK_FLOW_NAME, null, null)
		));
		onErrorDataSourceRepo.save(onErrorDataSource);

		// Set up the normal flow configuration needed for the error test
		loadConfig();
		refreshFlowCaches();

		// Create a DeltaFile and process it normally until it hits the egress stage
		UUID did = UUID.randomUUID();
		DeltaFile original = fullFlowExemplarService.postTransformDeltaFile(did);
		deltaFileRepo.save(original);

		// Count DeltaFiles before error occurs
		long initialCount = deltaFileRepo.count();

		// Trigger an error in the egress action - this should trigger our OnError data source
		deltaFilesService.handleActionEvent(actionEvent("error", did, original.lastFlow().getId()));
		deltaFileCacheService.flush();

		// Verify the original DeltaFile is in error state
		DeltaFile erroredFile = deltaFilesService.getDeltaFile(did);
		assertEquals(DeltaFileFlowState.ERROR, erroredFile.lastFlow().getState());
		assertNotNull(erroredFile.lastFlow().lastAction().getErrorCause());
		assertEquals("Authority XYZ not recognized", erroredFile.lastFlow().lastAction().getErrorCause());

		// Verify that a new DeltaFile was created by the OnError data source
		long finalCount = deltaFileRepo.count();
		assertTrue(finalCount > initialCount, "OnError data source should have created a new DeltaFile, but count went from " + initialCount + " to " + finalCount);

		DeltaFile updatedOriginal = deltaFileRepo.findById(original.getDid()).orElse(null);
		assertNotNull(updatedOriginal);

		// Find the newly created error event DeltaFile by checking for child DID
		assertNotNull(updatedOriginal.getChildDids());
		assertFalse(updatedOriginal.getChildDids().isEmpty());
		UUID errorEventDid = updatedOriginal.getChildDids().getFirst();
		DeltaFile errorEventFile = deltaFileRepo.findById(errorEventDid).orElse(null);

		assertNotNull(errorEventFile);
		assertEquals(List.of(original.getDid()), errorEventFile.getParentDids());
		assertNotNull(original.getChildDids());
		assertEquals(List.of(errorEventFile.getDid()), updatedOriginal.getChildDids());

		assertNotNull(errorEventFile, "Should have created an error event DeltaFile with DID: " + errorEventDid);
		assertEquals(errorEventDid, errorEventFile.getDid(), "DeltaFile DID should match the DID used in ContentStorageService.save");
		assertEquals("test-error-catcher", errorEventFile.getDataSource());
		assertEquals("test-error-catcher", errorEventFile.lastFlow().getName());
		assertEquals(List.of("error-events"), errorEventFile.lastFlow().getPublishTopics());

		// Verify metadata contains error context
		Map<String, String> metadata = errorEventFile.lastFlow().getMetadata();
		assertNotNull(metadata);
		assertEquals(DATA_SINK_FLOW_NAME, metadata.get("onError.sourceFlowName"));
		assertEquals("DATA_SINK", metadata.get("onError.sourceFlowType"));
		assertEquals("SampleEgressAction", metadata.get("onError.sourceActionName"));
		assertEquals("EGRESS", metadata.get("onError.sourceActionType"));
		assertEquals("Authority XYZ not recognized", metadata.get("onError.errorCause"));
		assertEquals(did.toString(), metadata.get("onError.sourceDid"));
		assertEquals(original.getName(), metadata.get("onError.sourceName"));
		assertEquals(original.getDataSource(), metadata.get("onError.sourceDataSource"));
		assertNotNull(metadata.get("onError.eventTimestamp"));

		// Verify the error event DeltaFile has the correct depth (original depth + 1)
		assertEquals(original.lastFlow().getDepth() + 1, errorEventFile.lastFlow().getDepth(),
			"Error event DeltaFile should have depth = original depth + 1");

		// Verify content contains both original and errored content with proper tags
		List<Content> errorEventContent = errorEventFile.lastFlow().firstAction().getContent();
		assertNotNull(errorEventContent);
		assertFalse(errorEventContent.isEmpty());

		// Check for original content (tagged with "original")
		List<Content> originalTaggedContent = errorEventContent.stream()
			.filter(content -> content.getTags().contains("original"))
			.toList();
		assertFalse(originalTaggedContent.isEmpty(), "Should have content tagged with 'original'");

		// Check for errored content (tagged with "errored")
		List<Content> erroredTaggedContent = errorEventContent.stream()
			.filter(content -> content.getTags().contains("errored"))
			.toList();
		assertFalse(erroredTaggedContent.isEmpty(), "Should have content tagged with 'errored'");
	}

	@Test
	@SneakyThrows
	void testOnErrorDataSourceTriggeringThenResume() {
		clearForFlowTests();

		// Set up OnError data source that catches errors from the egress action
		OnErrorDataSource onErrorDataSource = new OnErrorDataSource();
		onErrorDataSource.setName("test-error-catcher");
		onErrorDataSource.getFlowStatus().setState(FlowState.RUNNING);
		onErrorDataSource.setTestMode(false);
		onErrorDataSource.setTopic("error-events");
		onErrorDataSource.setSourcePlugin(PLUGIN_COORDINATES);
		onErrorDataSource.setErrorMessageRegex(".*");
		onErrorDataSource.setSourceFilters(List.of(
			new org.deltafi.common.types.ErrorSourceFilter(null, null, "SampleEgressAction", null),
			new org.deltafi.common.types.ErrorSourceFilter(org.deltafi.common.types.FlowType.DATA_SINK, DATA_SINK_FLOW_NAME, null, null)
		));
		onErrorDataSourceRepo.save(onErrorDataSource);

		// Set up the normal flow configuration
		loadConfig();
		refreshFlowCaches();

		// Create a DeltaFile and process it normally until it hits the egress stage
		UUID did = UUID.randomUUID();
		DeltaFile original = fullFlowExemplarService.postTransformDeltaFile(did);
		deltaFileRepo.save(original);

		// Count DeltaFiles before error occurs
		long initialCount = deltaFileRepo.count();

		// Trigger an error in the egress action - this should trigger our OnError data source
		deltaFilesService.handleActionEvent(actionEvent("error", did, original.lastFlow().getId()));
		deltaFileCacheService.flush();

		// Verify the original DeltaFile is in error state
		DeltaFile erroredFile = deltaFilesService.getDeltaFile(did);
		assertEquals(DeltaFileFlowState.ERROR, erroredFile.lastFlow().getState());
		assertNotNull(erroredFile.lastFlow().lastAction().getErrorCause());
		assertEquals("Authority XYZ not recognized", erroredFile.lastFlow().lastAction().getErrorCause());

		// Verify that a new DeltaFile was created by the OnError data source
		long finalCount = deltaFileRepo.count();
		assertTrue(finalCount > initialCount, "OnError data source should have created a new DeltaFile");

		DeltaFile updatedOriginal = deltaFileRepo.findById(original.getDid()).orElse(null);
		assertNotNull(updatedOriginal);

		// Find the newly created error event DeltaFile by checking for child DID
		assertNotNull(updatedOriginal.getChildDids());
		assertFalse(updatedOriginal.getChildDids().isEmpty());
		UUID errorEventDid = updatedOriginal.getChildDids().getFirst();
		DeltaFile errorEventFile = deltaFileRepo.findById(errorEventDid).orElse(null);

		assertNotNull(errorEventFile, "Should have created an error event DeltaFile");
		assertEquals("test-error-catcher", errorEventFile.getDataSource());

		// Now attempt to resume the original DeltaFile
		List<RetryResult> retryResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("resume"), did),
				"data." + DgsConstants.MUTATION.Resume,
				new TypeRef<>() {});

		// Verify resume was successful
		assertNotNull(retryResults, "Resume should return results");
		assertFalse(retryResults.isEmpty(), "Resume should return at least one result");

		RetryResult firstResult = retryResults.getFirst();
		assertEquals(did, firstResult.getDid(), "Resume result should be for the correct DID");
		assertTrue(firstResult.getSuccess(),
			"Resume should succeed. Error: " + (firstResult.getError() != null ? firstResult.getError() : "none"));

		// Verify the original DeltaFile is now resuming/resumed correctly
		DeltaFile resumedFile = deltaFilesService.getDeltaFile(did);
		assertNotNull(resumedFile);

		// The resumed file should be in IN_FLIGHT state (not stuck in ERROR)
		assertEquals(DeltaFileFlowState.IN_FLIGHT, resumedFile.lastFlow().getState(),
			"After resume, the DeltaFile should be IN_FLIGHT. Current state: " + resumedFile.lastFlow().getState());

		// Verify the stage is also IN_FLIGHT
		assertEquals(DeltaFileStage.IN_FLIGHT, resumedFile.getStage(),
			"After resume, the DeltaFile stage should be IN_FLIGHT. Current stage: " + resumedFile.getStage());

		// Verify the error action was marked as RETRIED
		Action errorAction = resumedFile.lastFlow().getActions().stream()
			.filter(a -> "SampleEgressAction".equals(a.getName()) && a.getState() == ActionState.RETRIED)
			.findFirst()
			.orElse(null);
		assertNotNull(errorAction, "The errored action should be marked as RETRIED");

		// Verify a new action attempt was queued
		Action retryAction = resumedFile.lastFlow().getActions().stream()
			.filter(a -> "SampleEgressAction".equals(a.getName()) && a.getState() == ActionState.QUEUED)
			.findFirst()
			.orElse(null);
		assertNotNull(retryAction, "A new retry action should be QUEUED");
		assertEquals(2, retryAction.getAttempt(), "Retry action should be on attempt 2");

		// Verify the OnError child DeltaFile still exists and wasn't affected by the resume
		DeltaFile stillExistingErrorEventFile = deltaFileRepo.findById(errorEventDid).orElse(null);
		assertNotNull(stillExistingErrorEventFile, "OnError DeltaFile should still exist after resume");

		// Verify the childDids relationship is maintained
		assertEquals(List.of(errorEventDid), resumedFile.getChildDids(),
			"The original DeltaFile should still have the OnError child DID");

		// Verify action was queued for execution
		Mockito.verify(coreEventQueue).putActions(actionInputListCaptor.capture(), anyBoolean());
		List<WrappedActionInput> actionInputs = actionInputListCaptor.getValue();
		assertFalse(actionInputs.isEmpty(), "Resume should have queued an action");
		assertEquals(SAMPLE_EGRESS_ACTION, actionInputs.getFirst().getActionContext().getActionName());
		assertEquals(DATA_SINK_FLOW_NAME, actionInputs.getFirst().getActionContext().getFlowName());
	}

	@Test
	@SneakyThrows
	void testOnErrorDataSourceTriggeringTwice() {
		clearForFlowTests();

		// Set up OnError data source that catches errors from the egress action
		OnErrorDataSource onErrorDataSource = new OnErrorDataSource();
		onErrorDataSource.setName("test-error-catcher");
		onErrorDataSource.getFlowStatus().setState(FlowState.RUNNING);
		onErrorDataSource.setTestMode(false);
		onErrorDataSource.setTopic("error-events");
		onErrorDataSource.setSourcePlugin(PLUGIN_COORDINATES);
		onErrorDataSource.setErrorMessageRegex(".*");
		onErrorDataSource.setSourceFilters(List.of(
			new org.deltafi.common.types.ErrorSourceFilter(null, null, "SampleEgressAction", null),
			new org.deltafi.common.types.ErrorSourceFilter(org.deltafi.common.types.FlowType.DATA_SINK, DATA_SINK_FLOW_NAME, null, null)
		));
		onErrorDataSourceRepo.save(onErrorDataSource);

		// Set up the normal flow configuration
		loadConfig();
		refreshFlowCaches();

		// Create a DeltaFile and process it normally until it hits the egress stage
		UUID did = UUID.randomUUID();
		DeltaFile original = fullFlowExemplarService.postTransformDeltaFile(did);
		deltaFileRepo.save(original);

		// Count DeltaFiles before first error occurs
		long initialCount = deltaFileRepo.count();

		// Trigger first error in the egress action
		deltaFilesService.handleActionEvent(actionEvent("error", did, original.lastFlow().getId()));
		deltaFileCacheService.flush();

		// Verify the original DeltaFile is in error state after first error
		DeltaFile erroredFile = deltaFilesService.getDeltaFile(did);
		assertEquals(DeltaFileFlowState.ERROR, erroredFile.lastFlow().getState());
		assertEquals("Authority XYZ not recognized", erroredFile.lastFlow().lastAction().getErrorCause());

		// Verify first OnError DeltaFile was created
		long afterFirstErrorCount = deltaFileRepo.count();
		assertTrue(afterFirstErrorCount > initialCount, "First OnError data source should have created a new DeltaFile");

		DeltaFile afterFirstError = deltaFileRepo.findById(did).orElse(null);
		assertNotNull(afterFirstError);
		assertNotNull(afterFirstError.getChildDids());
		assertEquals(1, afterFirstError.getChildDids().size(), "Should have one child DID after first error");
		UUID firstErrorEventDid = afterFirstError.getChildDids().getFirst();

		DeltaFile firstErrorEventFile = deltaFileRepo.findById(firstErrorEventDid).orElse(null);
		assertNotNull(firstErrorEventFile, "First error event DeltaFile should exist");
		assertEquals("test-error-catcher", firstErrorEventFile.getDataSource());
		assertEquals(List.of(did), firstErrorEventFile.getParentDids());

		// Now resume the original DeltaFile
		List<RetryResult> retryResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("resume"), did),
				"data." + DgsConstants.MUTATION.Resume,
				new TypeRef<>() {});

		assertTrue(retryResults.getFirst().getSuccess(), "Resume should succeed");

		// Verify the DeltaFile is now IN_FLIGHT after resume
		DeltaFile resumedFile = deltaFilesService.getDeltaFile(did);
		assertEquals(DeltaFileFlowState.IN_FLIGHT, resumedFile.lastFlow().getState());
		assertEquals(1, resumedFile.getChildDids().size(), "Should still have one child DID after resume");

		// Now trigger a SECOND error (the resume failed)
		deltaFilesService.handleActionEvent(actionEvent("error", did, resumedFile.lastFlow().getId()));
		deltaFileCacheService.flush();

		// Verify the original DeltaFile is back in error state after second error
		DeltaFile erroredAgain = deltaFilesService.getDeltaFile(did);
		assertEquals(DeltaFileFlowState.ERROR, erroredAgain.lastFlow().getState());
		assertEquals("Authority XYZ not recognized", erroredAgain.lastFlow().lastAction().getErrorCause());

		// Verify second OnError DeltaFile was created
		long afterSecondErrorCount = deltaFileRepo.count();
		assertTrue(afterSecondErrorCount > afterFirstErrorCount, "Second OnError data source should have created another new DeltaFile");

		// Verify we now have TWO child DIDs
		assertNotNull(erroredAgain.getChildDids());
		assertEquals(2, erroredAgain.getChildDids().size(),
			"Should have TWO child DIDs after second error. Found: " + erroredAgain.getChildDids());

		// Verify both error event DeltaFiles exist
		UUID secondErrorEventDid = erroredAgain.getChildDids().get(1);
		assertNotEquals(firstErrorEventDid, secondErrorEventDid, "Second error event DID should be different from first");

		DeltaFile secondErrorEventFile = deltaFileRepo.findById(secondErrorEventDid).orElse(null);
		assertNotNull(secondErrorEventFile, "Second error event DeltaFile should exist");
		assertEquals("test-error-catcher", secondErrorEventFile.getDataSource());
		assertEquals(List.of(did), secondErrorEventFile.getParentDids());

		// Verify both error events have proper metadata
		Map<String, String> firstMetadata = firstErrorEventFile.lastFlow().getMetadata();
		Map<String, String> secondMetadata = secondErrorEventFile.lastFlow().getMetadata();

		assertEquals(did.toString(), firstMetadata.get("onError.sourceDid"));
		assertEquals(did.toString(), secondMetadata.get("onError.sourceDid"));
		assertEquals("Authority XYZ not recognized", firstMetadata.get("onError.errorCause"));
		assertEquals("Authority XYZ not recognized", secondMetadata.get("onError.errorCause"));

		// Verify the order: first error event should be at index 0, second at index 1
		assertEquals(firstErrorEventDid, erroredAgain.getChildDids().get(0));
		assertEquals(secondErrorEventDid, erroredAgain.getChildDids().get(1));
	}

	@Test
	void testResume() throws IOException {
		UUID did = UUID.randomUUID();
		deltaFileRepo.save(fullFlowExemplarService.postErrorDeltaFile(did));

		List<RetryResult> retryResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("resume"), did),
				"data." + DgsConstants.MUTATION.Resume,
				new TypeRef<>() {});

		assertEquals(2, retryResults.size());
		assertEquals(did, retryResults.getFirst().getDid());
		assertTrue(retryResults.getFirst().getSuccess());
		assertFalse(retryResults.get(1).getSuccess());

		verifyActionEventResults(fullFlowExemplarService.postResumeDeltaFile(did),
				ActionContext.builder().flowName(DATA_SINK_FLOW_NAME).actionName(SAMPLE_EGRESS_ACTION).build());

		Mockito.verifyNoInteractions(metricService);
	}

	@Test
	void testResumeClearsAcknowledged() throws IOException {
		UUID did = UUID.randomUUID();
		DeltaFile postErrorDeltaFile = fullFlowExemplarService.postErrorDeltaFile(did);
		postErrorDeltaFile.acknowledgeErrors(OffsetDateTime.now(), "some reason");
		deltaFileRepo.save(postErrorDeltaFile);

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("resume"), did),
				"data." + DgsConstants.MUTATION.Resume,
				new TypeRef<>() {});

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertNull(deltaFile.lastFlow().getErrorAcknowledged());
		assertNull(deltaFile.lastFlow().getErrorAcknowledgedReason());
		Mockito.verifyNoInteractions(metricService);
	}

	@Test
	void testAcknowledge() throws IOException {
		UUID did = UUID.randomUUID();
		deltaFileRepo.save(fullFlowExemplarService.postErrorDeltaFile(did));

		List<AcknowledgeResult> acknowledgeResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("acknowledge"), did),
				"data." + DgsConstants.MUTATION.Acknowledge,
				new TypeRef<>() {});

		assertEquals(2, acknowledgeResults.size());
		assertEquals(did, acknowledgeResults.getFirst().getDid());
		assertTrue(acknowledgeResults.getFirst().getSuccess());
		assertFalse(acknowledgeResults.get(1).getSuccess());

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertNotNull(deltaFile.lastFlow().getErrorAcknowledged());
		assertEquals("apathy", deltaFile.lastFlow().getErrorAcknowledgedReason());

		// Verify once its been acknowledged, it fails on another acknowledge attempt
		acknowledgeResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("acknowledge"), did),
				"data." + DgsConstants.MUTATION.Acknowledge,
				new TypeRef<>() {});

		assertEquals(2, acknowledgeResults.size());
		assertEquals(did, acknowledgeResults.getFirst().getDid());
		assertFalse(acknowledgeResults.getFirst().getSuccess());
		assertTrue(acknowledgeResults.getFirst().getError().contains("can no longer be acknowledged"));

		Mockito.verifyNoInteractions(metricService);
	}

	@Test
	void testAcknowledgeMatching() throws IOException {
		UUID did = UUID.randomUUID();
		deltaFileRepo.save(fullFlowExemplarService.postErrorDeltaFile(did));

		List<AcknowledgeResult> acknowledgeResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("acknowledgeMatching"), did),
				"data." + DgsConstants.MUTATION.AcknowledgeMatching,
				new TypeRef<>() {});

		assertEquals(1, acknowledgeResults.size());
		assertEquals(did, acknowledgeResults.getFirst().getDid());
		assertTrue(acknowledgeResults.getFirst().getSuccess());

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertNotNull(deltaFile.lastFlow().getErrorAcknowledged());
		assertEquals("apathy", deltaFile.lastFlow().getErrorAcknowledgedReason());
		Mockito.verifyNoInteractions(metricService);
	}

	@Test
	void testCancel() throws IOException {
		UUID did = UUID.randomUUID();
		DeltaFile deltaFile = fullFlowExemplarService.postTransformUtf8DeltaFile(did);
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
		assertEqualsIgnoringDates(fullFlowExemplarService.postCancelDeltaFile(did), afterMutation);

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

	@Test
	void testToEgressWithTestModeEgress() throws IOException {
		UUID did = UUID.randomUUID();
		DeltaFile midTransform = fullFlowExemplarService.postTransformUtf8DeltaFile(did);
		DeltaFileFlow transformFlow = midTransform.getFlow(TRANSFORM_FLOW_NAME);
		transformFlow.setTestMode(true);
		transformFlow.setTestModeReason(transformFlow.getName());
		deltaFileRepo.save(midTransform);

		deltaFilesService.handleActionEvent(actionEvent("transform", did));

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);

		assertEqualsIgnoringDates(
				fullFlowExemplarService.postTransformDeltaFileInTestMode(did, OffsetDateTime.now()),
				deltaFile
		);
		MatcherAssert.assertThat(deltaFile.lastFlow().getTestModeReason(), containsString(TRANSFORM_FLOW_NAME));
		Mockito.verify(coreEventQueue, never()).putActions(any(), anyBoolean());
		verifyCommonMetrics(ActionEventType.TRANSFORM, "SampleTransformAction", REST_DATA_SOURCE_NAME, TRANSFORM_FLOW_NAME, null, "type");
	}

	@Test
	void testReplay() throws IOException {
		UUID did = UUID.randomUUID();
		deltaFileRepo.save(fullFlowExemplarService.postEgressDeltaFile(did));

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

		DeltaFile expected = fullFlowExemplarService.postIngressDeltaFile(did);
		expected.setDid(parent.getChildDids().getFirst());
		expected.setParentDids(List.of(did));
		Action action = expected.firstFlow().addAction("Replay", null, ActionType.INGRESS, COMPLETE, NOW);
		Map<String, String> replayMetadata = Map.of("AuthorizedBy", "ABC", "anotherKey", "anotherValue");
		action.setContent(expected.firstFlow().firstAction().getContent());
		action.setMetadata(replayMetadata);
		action.setDeleteMetadataKeys(List.of("removeMe"));
		verifyActionEventResults(expected, ActionContext.builder().flowName("sampleTransform").actionName("Utf8TransformAction").build());

		List<RetryResult> secondResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("replay"), did),
				"data." + DgsConstants.MUTATION.Replay,
				new TypeRef<>() {});

		assertEquals(1, secondResults.size());
		assertFalse(secondResults.getFirst().getSuccess());
		verifyIngressMetrics(REST_DATA_SOURCE_NAME, 500);
		verifySubscribeMetrics(REST_DATA_SOURCE_NAME, TRANSFORM_FLOW_NAME, 500);
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testReplayContentDeleted() throws IOException {
		UUID did = UUID.randomUUID();
		DeltaFile contentDeletedDeltaFile = fullFlowExemplarService.postEgressDeltaFile(did);
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
	void testReplayPaused() throws IOException {
		UUID did = UUID.randomUUID();
		deltaFileRepo.save(fullFlowExemplarService.postEgressDeltaFile(did));

		timedDataSourceService.pauseFlow(TIMED_DATA_SOURCE_NAME);

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

		DeltaFile child = deltaFilesService.getDeltaFile(parent.getChildDids().getFirst());
		assertEquals(1, child.getFlows().size());
		assertEquals(DeltaFileFlowState.PAUSED, child.firstFlow().getState());
		assertTrue(child.getPaused());

		Mockito.verifyNoInteractions(coreEventQueue);
	}

	@Test
	void testReplayTestMode() throws IOException {
		UUID did = UUID.randomUUID();
		DeltaFile tempParent = fullFlowExemplarService.postEgressDeltaFile(did);
		tempParent.firstFlow().setTestMode(true);
		tempParent.firstFlow().setTestModeReason(TIMED_DATA_SOURCE_NAME);
		deltaFileRepo.save(tempParent);

		timedDataSourceService.getFlowOrThrow(TIMED_DATA_SOURCE_NAME).setTestMode(true);

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

		DeltaFile child = deltaFilesService.getDeltaFile(parent.getChildDids().getFirst());
		assertEquals(2, child.getFlows().size());
		assertTrue(child.firstFlow().isTestMode());
		assertEquals(TIMED_DATA_SOURCE_NAME, child.firstFlow().getTestModeReason());
		assertTrue(child.lastFlow().isTestMode());
		assertEquals(TIMED_DATA_SOURCE_NAME, child.lastFlow().getTestModeReason());
	}

	@Test
	void testReplayTestModeToRegularMode() throws IOException {
		UUID did = UUID.randomUUID();
		DeltaFile tempParent = fullFlowExemplarService.postEgressDeltaFile(did);
		tempParent.firstFlow().setTestMode(true);
		tempParent.firstFlow().setTestModeReason(TIMED_DATA_SOURCE_NAME);
		deltaFileRepo.save(tempParent);

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

		DeltaFile child = deltaFilesService.getDeltaFile(parent.getChildDids().getFirst());
		assertEquals(2, child.getFlows().size());
		assertFalse(child.firstFlow().isTestMode());
		assertNull(child.firstFlow().getTestModeReason());
		assertFalse(child.lastFlow().isTestMode());
		assertNull(child.lastFlow().getTestModeReason());
	}

	@Test
	void testReplaySplitTestModeSticks() throws IOException {
		UUID did = UUID.randomUUID();
		DeltaFile tempParent = fullFlowExemplarService.postEgressDeltaFile(did);
		tempParent.setFlows(Set.of(tempParent.getFlow(TRANSFORM_FLOW_NAME)));
		tempParent.firstFlow().setTestMode(true);
		tempParent.firstFlow().setTestModeReason("parentReason");
		deltaFileRepo.save(tempParent);

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

		DeltaFile child = deltaFilesService.getDeltaFile(parent.getChildDids().getFirst());
		assertEquals(2, child.getFlows().size());
		assertTrue(child.firstFlow().isTestMode());
		assertEquals("parentReason", child.firstFlow().getTestModeReason());
		assertTrue(child.lastFlow().isTestMode());
		assertEquals("parentReason", child.lastFlow().getTestModeReason());
	}

	@Test
	void testSourceMetadataUnion() throws IOException {
		DeltaFile deltaFile1 = utilService.buildDeltaFile(UUID.randomUUID(), List.of(), Map.of("key", "val1"));
		DeltaFile deltaFile2 = utilService.buildDeltaFile(UUID.randomUUID(), List.of(), Map.of("key", "val2"));
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
		verifyFiltered(fullFlowExemplarService.postIngressDeltaFile(did), TRANSFORM_FLOW_NAME, UUID_1, "Utf8TransformAction");
	}

	@Test
	void testFilterEgress() throws IOException {
		UUID did = UUID.randomUUID();
		verifyFiltered(fullFlowExemplarService.postTransformDeltaFile(did), DATA_SINK_FLOW_NAME, UUID_2,"SampleEgressAction");
	}

	@SuppressWarnings("SameParameterValue")
	private void verifyFiltered(DeltaFile deltaFile, String flow, UUID flowId, String filteredAction) throws IOException {
		deltaFileRepo.save(deltaFile);

		deltaFilesService.handleActionEvent(filterActionEvent(deltaFile.getDid(), flow, flowId, filteredAction));

		DeltaFile actual = deltaFilesService.getDeltaFile(deltaFile.getDid());
		Action action = actual.getFlow(flowId).getAction(filteredAction);
		assert action != null;

		assertEquals(ActionState.FILTERED, action.getState());
		assertEquals(DeltaFileStage.COMPLETE, actual.getStage());
		assertTrue(actual.getFiltered());
		assertEquals("you got filtered", action.getFilteredCause());
		assertEquals("here is why: blah", action.getFilteredContext());
		assertEquals("filter metadata", actual.getAnnotations().stream().filter(a -> a.getKey().equals("filterKey")).findFirst().orElse(new Annotation()).getValue());

		Mockito.verify(coreEventQueue, never()).putActions(any(), anyBoolean());
	}

	void savePlugin(List<FlowPlan> flowPlans) {
		savePlugin(flowPlans, PLUGIN_COORDINATES);
	}

	void savePlugin(List<FlowPlan> flowPlans, PluginCoordinates pluginCoordinates) {
		PluginEntity pluginEntity = new PluginEntity();
		pluginEntity.setPluginCoordinates(pluginCoordinates);
		pluginEntity.setFlowPlans(flowPlans);
		pluginRepository.save(pluginEntity);
	}

	@Test
	void testGetTransformFlowPlan() {
		clearForFlowTests();
		TransformFlowPlan transformFlowPlanA = new TransformFlowPlan("transformPlan", FlowType.TRANSFORM, "description");
		TransformFlowPlan transformFlowPlanB = new TransformFlowPlan("b", FlowType.TRANSFORM, "description");
		savePlugin(List.of(transformFlowPlanA, transformFlowPlanB));
		TransformFlowPlan plan = FlowPlanDatafetcherTestHelper.getTransformFlowPlan(dgsQueryExecutor);
		assertThat(plan.getName()).isEqualTo("transformPlan");
	}

	@Test
	void testGetDataSinkPlan() {
		clearForFlowTests();
		DataSinkPlan DataSinkPlanA = new DataSinkPlan("dataSinkPlan", FlowType.DATA_SINK, "description", new ActionConfiguration("egress", ActionType.EGRESS, "type"));
		DataSinkPlan DataSinkPlanB = new DataSinkPlan("b", FlowType.DATA_SINK, "description", new ActionConfiguration("egress", ActionType.EGRESS, "type"));
		savePlugin(List.of(DataSinkPlanA, DataSinkPlanB));
		DataSinkPlan plan = FlowPlanDatafetcherTestHelper.getDataSinkPlan(dgsQueryExecutor);
		assertThat(plan.getName()).isEqualTo("dataSinkPlan");
	}

	@Test
	void testGetTimedIngressDataSource() {
		clearForFlowTests();
		TimedDataSourcePlan dataSourcePlanA = new TimedDataSourcePlan("timedIngressPlan", FlowType.TIMED_DATA_SOURCE, "description", "topic",
				new ActionConfiguration("timedIngress", ActionType.TIMED_INGRESS, "type"),  "*/5 * * * * *");
		dataSourcePlanA.setMetadata(Map.of(
				"metaKeyX", "planX",
				"metaKeyY", "planY",
				"metaZ", "planZ"));
		dataSourcePlanA.setAnnotationConfig(new AnnotationConfig(
				Map.of("annotation1", "plan", "annotation2", "plan"),
				List.of("metaKey.*"), "meta"));

		TimedDataSourcePlan dataSourcePlanB = new TimedDataSourcePlan("b", FlowType.TIMED_DATA_SOURCE, "description", "topic",
				new ActionConfiguration("timedIngress", ActionType.TIMED_INGRESS, "type"), "*/5 * * * * *");

		savePlugin(List.of(dataSourcePlanA, dataSourcePlanB));
		DataSourcePlan plan = FlowPlanDatafetcherTestHelper.getTimedIngressFlowPlan(dgsQueryExecutor);
		assertThat(plan.getName()).isEqualTo("timedIngressPlan");
		assertThat(plan.getMetadata().size()).isEqualTo(3);
		assertThat(plan.getAnnotationConfig().getAnnotations().size()).isEqualTo(2);
	}

	@Test
	void testValidateTransformFlow() {
		clearForFlowTests();
		transformFlowRepo.save(buildTransformFlow(FlowState.STOPPED));
		refreshFlowCaches();
		TransformFlow transformFlow = FlowPlanDatafetcherTestHelper.validateTransformFlow(dgsQueryExecutor);
		assertThat(transformFlow.getFlowStatus()).isNotNull();
	}

	@Test
	void testValidateTimedIngressFlow() {
		clearForFlowTests();
		timedDataSourceRepo.save(buildTimedDataSource(FlowState.STOPPED));
		refreshFlowCaches();
		DataSource dataSource = FlowPlanDatafetcherTestHelper.validateTimedIngressFlow(dgsQueryExecutor);
		assertThat(dataSource.getFlowStatus()).isNotNull();
	}

	@Test
	void testValidateDataSink() {
		clearForFlowTests();
		dataSinkRepo.save(buildDataSink(FlowState.STOPPED));
		refreshFlowCaches();
		DataSink dataSink = FlowPlanDatafetcherTestHelper.validateDataSink(dgsQueryExecutor);
		assertThat(dataSink.getFlowStatus()).isNotNull();
	}

	@Test
	void testGetFlows() {
		clearForFlowTests();

		PluginCoordinates pluginCoordinates = PluginCoordinates.builder().artifactId("test-actions").groupId("org.deltafi").version("1.0").build();
		Variable variable = Variable.builder().name("var").description("description").defaultValue("value").required(false).build();
		PluginVariables variables = new PluginVariables();
		variables.setSourcePlugin(pluginCoordinates);
		variables.setVariables(List.of(variable));

		TransformFlow transformFlow = new TransformFlow();
		transformFlow.setName("transform");
		transformFlow.setSourcePlugin(pluginCoordinates);

		DataSink dataSink = new DataSink();
		dataSink.setName("dataSink");
		dataSink.setSourcePlugin(pluginCoordinates);

		RestDataSource restDataSource = new RestDataSource();
		restDataSource.setName("restDataSource");
		restDataSource.setSourcePlugin(pluginCoordinates);

		TimedDataSource timedDataSource = new TimedDataSource();
		timedDataSource.setName("timedDataSource");
		timedDataSource.setSourcePlugin(pluginCoordinates);

		OnErrorDataSource onErrorDataSource = new OnErrorDataSource();
		onErrorDataSource.setName("onErrorDataSource");
		onErrorDataSource.setSourcePlugin(pluginCoordinates);

		PluginEntity plugin = new PluginEntity();
		plugin.setPluginCoordinates(pluginCoordinates);
		pluginRepository.save(plugin);
		pluginVariableRepo.save(variables);
		transformFlowRepo.save(transformFlow);
		dataSinkRepo.save(dataSink);
		restDataSourceRepo.save(restDataSource);
		timedDataSourceRepo.save(timedDataSource);
		onErrorDataSourceRepo.save(onErrorDataSource);
		refreshFlowCaches();

		List<Flows> flows = FlowPlanDatafetcherTestHelper.getFlows(dgsQueryExecutor);
		assertThat(flows).hasSize(2);
		Flows systemFlows = flows.getFirst();
		assertThat(systemFlows.getSourcePlugin().getArtifactId()).isEqualTo("system-plugin");
		assertThat(systemFlows.getTransformFlows()).isEmpty();
		assertThat(systemFlows.getDataSinks()).isEmpty();
		assertThat(systemFlows.getRestDataSources()).isEmpty();
		assertThat(systemFlows.getTimedDataSources()).isEmpty();
		assertThat(systemFlows.getOnErrorDataSources()).isEmpty();
		Flows pluginFlows = flows.getLast();
		assertThat(pluginFlows.getSourcePlugin().getArtifactId()).isEqualTo("test-actions");
		assertThat(pluginFlows.getTransformFlows().getFirst().getName()).isEqualTo("transform");
		assertThat(pluginFlows.getDataSinks().getFirst().getName()).isEqualTo("dataSink");
		assertThat(pluginFlows.getRestDataSources().getFirst().getName()).isEqualTo("restDataSource");
		assertThat(pluginFlows.getTimedDataSources().getFirst().getName()).isEqualTo("timedDataSource");
		assertThat(pluginFlows.getOnErrorDataSources().getFirst().getName()).isEqualTo("onErrorDataSource");
	}

	@Test
	void testGetFlowsByState() {
		clearForFlowTests();
		TransformFlow stoppedFlow = buildTransformFlow(FlowState.STOPPED);
		stoppedFlow.setName("stopped");

		TransformFlow pausedFlow = buildTransformFlow(FlowState.PAUSED);
		pausedFlow.setName("paused");

		TransformFlow runningFlow = buildTransformFlow(FlowState.RUNNING);
		runningFlow.setName("running");

		transformFlowRepo.saveAll(List.of(stoppedFlow, pausedFlow, runningFlow));
		refreshFlowCaches();

		assertThat(transformFlowService.getFlowNamesByState(null)).hasSize(3).contains("stopped", "paused", "running");
		assertThat(transformFlowService.getFlowNamesByState(FlowState.STOPPED)).hasSize(1).contains("stopped");
		assertThat(transformFlowService.getFlowNamesByState(FlowState.PAUSED)).hasSize(1).contains("paused");
		assertThat(transformFlowService.getFlowNamesByState(FlowState.RUNNING)).hasSize(1).contains("running");
	}

	@Test
	void testGetFlowNamesQuery() {
		clearForFlowTests();

		timedDataSourceRepo.save(buildTimedDataSource(FlowState.STOPPED));
		onErrorDataSourceRepo.save(buildOnErrorDataSource(FlowState.STOPPED));
		transformFlowRepo.save(buildTransformFlow(FlowState.STOPPED));
		dataSinkRepo.save(buildDataSink(FlowState.STOPPED));
		refreshFlowCaches();

		FlowNames flows = FlowPlanDatafetcherTestHelper.getFlowNames(dgsQueryExecutor);
		assertThat(flows.getTimedDataSource()).hasSize(1).contains(TIMED_DATA_SOURCE_NAME);
		assertThat(flows.getOnErrorDataSource()).hasSize(1).contains(ON_ERROR_DATA_SOURCE_NAME);
		assertThat(flows.getTransform()).hasSize(1).contains(TRANSFORM_FLOW_NAME);
		assertThat(flows.getDataSink()).hasSize(1).contains(DATA_SINK_FLOW_NAME);
	}

	@Test
	void getRunningFlows() {
		clearForFlowTests();

		transformFlowRepo.save(buildTransformFlow(FlowState.STOPPED));
		refreshFlowCaches();
		assertTrue(FlowPlanDatafetcherTestHelper.startTransformFlow(dgsQueryExecutor));

		dataSinkRepo.save(buildDataSink(FlowState.STOPPED));
		refreshFlowCaches();
		assertTrue(FlowPlanDatafetcherTestHelper.startDataSink(dgsQueryExecutor));

		SystemFlows flows = FlowPlanDatafetcherTestHelper.getRunningFlows(dgsQueryExecutor);
		assertThat(flows.getTransform()).hasSize(1).matches(transformFlows -> TRANSFORM_FLOW_NAME.equals(transformFlows.getFirst().getName()));
		assertThat(flows.getDataSink()).hasSize(1).matches(dataSinks -> DATA_SINK_FLOW_NAME.equals(dataSinks.getFirst().getName()));

		assertTrue(FlowPlanDatafetcherTestHelper.stopDataSink(dgsQueryExecutor));
		SystemFlows updatedFlows = FlowPlanDatafetcherTestHelper.getRunningFlows(dgsQueryExecutor);
		assertThat(updatedFlows.getTransform()).hasSize(1);
		assertThat(updatedFlows.getDataSink()).isEmpty();
	}

	@Test
	void getAllFlowPlans() {
		clearForFlowTests();

		TransformFlowPlan transformFlow = new TransformFlowPlan(TRANSFORM_FLOW_NAME, FlowType.TRANSFORM, "desc");
		TimedDataSourcePlan timedDataSource = new TimedDataSourcePlan(TIMED_DATA_SOURCE_NAME, FlowType.TIMED_DATA_SOURCE, "desc", "topic", new ActionConfiguration("timed", ActionType.TIMED_INGRESS, "type"), "1234");
		RestDataSourcePlan restDataSource = new RestDataSourcePlan(REST_DATA_SOURCE_NAME, FlowType.REST_DATA_SOURCE, "desc", "topic");
		OnErrorDataSourcePlan onErrorDataSource = new OnErrorDataSourcePlan(ON_ERROR_DATA_SOURCE_NAME, FlowType.ON_ERROR_DATA_SOURCE, "desc", null, null, "topic", null, null, null, null, null, null, null);
		DataSinkPlan dataSink = new DataSinkPlan(DATA_SINK_FLOW_NAME, FlowType.DATA_SINK, "desc", new ActionConfiguration("egress", ActionType.EGRESS, "type2"));

		savePlugin(List.of(transformFlow, timedDataSource, restDataSource, onErrorDataSource, dataSink));

		SystemFlowPlans flowPlans = FlowPlanDatafetcherTestHelper.getAllFlowPlans(dgsQueryExecutor);
		assertThat(flowPlans.getTransformPlans()).hasSize(1).matches(transformFlows -> TRANSFORM_FLOW_NAME.equals(transformFlows.getFirst().getName()));
		assertThat(flowPlans.getTimedDataSources()).hasSize(1).matches(timedIngressFlows -> TIMED_DATA_SOURCE_NAME.equals(timedIngressFlows.getFirst().getName()));
		assertThat(flowPlans.getRestDataSources()).hasSize(1).matches(restIngressFlows -> REST_DATA_SOURCE_NAME.equals(restIngressFlows.getFirst().getName()));
		assertThat(flowPlans.getOnErrorDataSources()).hasSize(1).matches(onErrorDataSources -> ON_ERROR_DATA_SOURCE_NAME.equals(onErrorDataSources.getFirst().getName()));
		assertThat(flowPlans.getDataSinkPlans()).hasSize(1).matches(dataSinks -> DATA_SINK_FLOW_NAME.equals(dataSinks.getFirst().getName()));
	}

	@Test
	void getAllFlows() {
		clearForFlowTests();

		TransformFlow transformFlow = new TransformFlow();
		transformFlow.setName(TRANSFORM_FLOW_NAME);

		TimedDataSource timedDataSource = new TimedDataSource();
		timedDataSource.setName(TIMED_DATA_SOURCE_NAME);

		RestDataSource restDataSource = new RestDataSource();
		restDataSource.setName(REST_DATA_SOURCE_NAME);

		OnErrorDataSource onErrorDataSource = new OnErrorDataSource();
		onErrorDataSource.setName(ON_ERROR_DATA_SOURCE_NAME);

		DataSink dataSink = new DataSink();
		dataSink.setName(DATA_SINK_FLOW_NAME);

		transformFlowRepo.save(transformFlow);
		timedDataSourceRepo.save(timedDataSource);
		restDataSourceRepo.save(restDataSource);
		onErrorDataSourceRepo.save(onErrorDataSource);
		dataSinkRepo.save(dataSink);
		refreshFlowCaches();

		SystemFlows flows = FlowPlanDatafetcherTestHelper.getAllFlows(dgsQueryExecutor);
		assertThat(flows.getTransform()).hasSize(1).matches(transformFlows -> TRANSFORM_FLOW_NAME.equals(transformFlows.getFirst().getName()));
		assertThat(flows.getTimedDataSource()).hasSize(1).matches(timedIngressFlows -> TIMED_DATA_SOURCE_NAME.equals(timedIngressFlows.getFirst().getName()));
		assertThat(flows.getRestDataSource()).hasSize(1).matches(restIngressFlows -> REST_DATA_SOURCE_NAME.equals(restIngressFlows.getFirst().getName()));
		assertThat(flows.getOnErrorDataSource()).hasSize(1).matches(onErrorDataSources -> ON_ERROR_DATA_SOURCE_NAME.equals(onErrorDataSources.getFirst().getName()));
		assertThat(flows.getDataSink()).hasSize(1).matches(dataSinks -> DATA_SINK_FLOW_NAME.equals(dataSinks.getFirst().getName()));
	}

	void setupTopicTest() {
		clearForFlowTests();

		TransformFlow transformFlow = new TransformFlow();
		transformFlow.setName(TRANSFORM_FLOW_NAME);
		transformFlow.setSubscribe(Set.of(new Rule("restTopic", "restCondition"), new Rule("timedTopic", null)));
		transformFlow.setPublish(PublishRules.builder()
				.rules(List.of(new Rule("transformPublish1", "transformPublishCondition"),
						new Rule("transformPublish2", null)))
				.defaultRule(DefaultRule.builder().defaultBehavior(DefaultBehavior.PUBLISH).topic("default").build())
				.build());

		TimedDataSource timedDataSource = new TimedDataSource();
		timedDataSource.setName(TIMED_DATA_SOURCE_NAME);
		timedDataSource.setTopic("timedTopic");

		RestDataSource restDataSource = new RestDataSource();
		restDataSource.setName(REST_DATA_SOURCE_NAME);
		restDataSource.setTopic("restTopic");
		restDataSource.getFlowStatus().setState(FlowState.RUNNING);

		DataSink dataSink = new DataSink();
		dataSink.setName(DATA_SINK_FLOW_NAME);
		dataSink.setSubscribe(Set.of(new Rule("transformPublish1", null)));

		transformFlowRepo.save(transformFlow);
		timedDataSourceRepo.save(timedDataSource);
		restDataSourceRepo.save(restDataSource);
		dataSinkRepo.save(dataSink);
	}

	private static final Topic REST_TOPIC = Topic.newBuilder().name("restTopic")
			.publishers(List.of(TopicParticipant.newBuilder().name(REST_DATA_SOURCE_NAME).type(FlowType.REST_DATA_SOURCE).state(FlowState.RUNNING).condition(null).build()))
			.subscribers(List.of(TopicParticipant.newBuilder().name(TRANSFORM_FLOW_NAME).type(FlowType.TRANSFORM).state(FlowState.STOPPED).condition("restCondition").build()))
			.build();
	private static final Topic TIMED_TOPIC = Topic.newBuilder().name("timedTopic")
			.publishers(List.of(TopicParticipant.newBuilder().name(TIMED_DATA_SOURCE_NAME).type(FlowType.TIMED_DATA_SOURCE).state(FlowState.STOPPED).condition(null).build()))
			.subscribers(List.of(TopicParticipant.newBuilder().name(TRANSFORM_FLOW_NAME).type(FlowType.TRANSFORM).state(FlowState.STOPPED).condition(null).build()))
			.build();
	private static final Topic DEFAULT_TOPIC = Topic.newBuilder().name("default")
			.publishers(List.of(TopicParticipant.newBuilder().name(TRANSFORM_FLOW_NAME).type(FlowType.TRANSFORM).state(FlowState.STOPPED).condition(null).build()))
			.subscribers(List.of())
			.build();
	private static final Topic TRANSFORM_1_TOPIC = Topic.newBuilder().name("transformPublish1")
			.publishers(List.of(TopicParticipant.newBuilder().name(TRANSFORM_FLOW_NAME).type(FlowType.TRANSFORM).state(FlowState.STOPPED).condition("transformPublishCondition").build()))
			.subscribers(List.of(TopicParticipant.newBuilder().name(DATA_SINK_FLOW_NAME).type(FlowType.DATA_SINK).state(FlowState.STOPPED).condition(null).build()))
			.build();
	private static final Topic TRANSFORM_2_TOPIC = Topic.newBuilder().name("transformPublish2")
			.publishers(List.of(TopicParticipant.newBuilder().name(TRANSFORM_FLOW_NAME).type(FlowType.TRANSFORM).state(FlowState.STOPPED).condition(null).build()))
			.subscribers(List.of())
			.build();

	@Test
	void getAllTopics() {
		setupTopicTest();

		List<Topic> actual = FlowPlanDatafetcherTestHelper.getAllTopics(dgsQueryExecutor);
		assertThat(actual.size()).isEqualTo(5);

		assertThat(actual).contains(REST_TOPIC, TIMED_TOPIC, DEFAULT_TOPIC, TRANSFORM_1_TOPIC, TRANSFORM_2_TOPIC);
	}

	@Test
	void getTopics() {
		setupTopicTest();

		List<Topic> actual = FlowPlanDatafetcherTestHelper.getTopics(dgsQueryExecutor, List.of(REST_TOPIC.getName(), TIMED_TOPIC.getName()));
		assertThat(actual.size()).isEqualTo(2);
		assertThat(actual).contains(REST_TOPIC, TIMED_TOPIC);

		assertThatThrownBy(() -> FlowPlanDatafetcherTestHelper.getTopics(dgsQueryExecutor, List.of(REST_TOPIC.getName(), TIMED_TOPIC.getName(), "x")));
	}

	@Test
	void getTopic() {
		setupTopicTest();

		Topic actual = FlowPlanDatafetcherTestHelper.getTopic(dgsQueryExecutor, REST_TOPIC.getName());
		assertThat(actual).isEqualTo(REST_TOPIC);

		assertThatThrownBy(() -> FlowPlanDatafetcherTestHelper.getTopic(dgsQueryExecutor, "x"));
	}

	@Test
	void getTransformFlow() {
		clearForFlowTests();
		TransformFlow transformFlow = new TransformFlow();
		transformFlow.setName(TRANSFORM_FLOW_NAME);
		transformFlowRepo.save(transformFlow);
		refreshFlowCaches();

		TransformFlow foundFlow = FlowPlanDatafetcherTestHelper.getTransformFlow(dgsQueryExecutor);
		assertThat(foundFlow).isNotNull();
		assertThat(foundFlow.getName()).isEqualTo(TRANSFORM_FLOW_NAME);
	}

	@Test
	void getDataSink() {
		clearForFlowTests();
		DataSink dataSink = new DataSink();
		dataSink.setName(DATA_SINK_FLOW_NAME);
		dataSinkRepo.save(dataSink);
		refreshFlowCaches();
		DataSink foundFlow = FlowPlanDatafetcherTestHelper.getDataSink(dgsQueryExecutor);
		assertThat(foundFlow).isNotNull();
		assertThat(foundFlow.getName()).isEqualTo(DATA_SINK_FLOW_NAME);
	}

	@Test
	void findFlowByNameAndType() {
		clearForFlowTests();
		String name = "dataSource-name";
		DataSink dataSink = new DataSink();
		dataSink.setName(name);
		dataSinkRepo.save(dataSink);

		TransformFlow transformFlow = new TransformFlow();
		transformFlow.setName(name);
		transformFlowRepo.save(transformFlow);
		transformFlowRepo.save(transformFlow);

		RestDataSource restDataSource = new RestDataSource();
		restDataSource.setName(name);
		restDataSourceRepo.save(restDataSource);

		TimedDataSource timedDataSource = new TimedDataSource();
		timedDataSource.setName("timed" + name);
		timedDataSourceRepo.save(timedDataSource);

		assertThat(transformFlowRepo.findByNameAndType(name, FlowType.TRANSFORM, TransformFlow.class)).isNotEmpty();
		assertThat(dataSinkRepo.findByNameAndType(name, FlowType.DATA_SINK, DataSink.class)).isNotEmpty();
		assertThat(restDataSourceRepo.findByNameAndType(name, FlowType.REST_DATA_SOURCE, RestDataSource.class)).isNotEmpty();
		assertThat(timedDataSourceRepo.findByNameAndType(name, FlowType.TIMED_DATA_SOURCE, TimedDataSource.class)).isEmpty();
		assertThat(timedDataSourceRepo.findByNameAndType(timedDataSource.getName(), FlowType.TIMED_DATA_SOURCE, TimedDataSource.class)).isNotEmpty();
	}

	@Test
	void getActionNamesByFamily() {
		clearForFlowTests();

		transformFlowRepo.save(buildTransformFlow(FlowState.STOPPED));
		dataSinkRepo.save(buildDataSink(FlowState.STOPPED));
		timedDataSourceRepo.save(buildTimedDataSource(FlowState.STOPPED));
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
		List<PropertySet> propertySets = PropertiesDatafetcherTestHelper.getPropertySets(dgsQueryExecutor, null);
		assertThat(propertySets).hasSize(1);

        propertySets = PropertiesDatafetcherTestHelper.getPropertySets(dgsQueryExecutor, false);
        assertThat(propertySets).hasSize(1);

        propertySets = PropertiesDatafetcherTestHelper.getPropertySets(dgsQueryExecutor, true);
        assertThat(propertySets).hasSize(PropertyGroup.values().length);
    }

	@Test
	void testGetDeltaFiProperties() {
		DeltaFiProperties deltaFiProperties = PropertiesDatafetcherTestHelper.getDeltaFiProperties(dgsQueryExecutor);
		assertThat(deltaFiProperties).isEqualTo(new DeltaFiProperties());
	}

	@Test
	void testUpdateAndRemovePropertyOverrides() {
		deltaFiPropertiesService.upsertProperties();
		assertThat(PropertiesDatafetcherTestHelper.updateProperties(dgsQueryExecutor)).isTrue();
		assertThat(PropertiesDatafetcherTestHelper.removePropertyOverrides(dgsQueryExecutor)).isTrue();
	}

	@Test
	void testLinkMutations() {
		uiLinkRepo.deleteAll();
		Link link = PropertiesDatafetcherTestHelper.saveLink(dgsQueryExecutor);
		assertThat(link.getName()).isEqualTo("some link");
		assertThat(link.getDescription()).isEqualTo("some place described");
		assertThat(link.getUrl()).isEqualTo("www.some.place");
		assertThat(link.getLinkType()).isEqualTo(LinkType.EXTERNAL);
		assertThat(PropertiesDatafetcherTestHelper.removeLink(dgsQueryExecutor, link.getId())).isTrue();
		assertThat(uiLinkRepo.findAll()).isEmpty();
	}

	@Test
	void testSaveTransformFlowPlan() {
		clearForFlowTests();
		TransformFlow transformFlow = FlowPlanDatafetcherTestHelper.saveTransformFlowPlan(dgsQueryExecutor);
		assertThat(transformFlow).isNotNull();
	}

	@Test
	void testSaveDataSinkPlan() {
		clearForFlowTests();
		DataSink dataSink = FlowPlanDatafetcherTestHelper.saveDataSinkPlan(dgsQueryExecutor);
		assertThat(dataSink).isNotNull();
	}

	@Test
	void testSaveTimedDataSourcePlan() {
		clearForFlowTests();
		DataSource dataSource = FlowPlanDatafetcherTestHelper.saveTimedDataSourcePlan(dgsQueryExecutor);
		assertThat(dataSource).isNotNull();
	}

	@Test
	void testExportImportCustomSystemFlowPlans() throws IOException {
		clearForFlowTests();
		SystemFlowPlansInput input = OBJECT_MAPPER.readValue(Resource.read("/system-flow-plans.json"), SystemFlowPlansInput.class);
		assertTrue(FlowPlanDatafetcherTestHelper.saveSystemFlowPlans(dgsQueryExecutor, input));

		SystemFlowPlans systemFlowPlans = FlowPlanDatafetcherTestHelper.getAllSystemFlowPlans(dgsQueryExecutor);

		assertEquals(input.getDataSinkPlans().getFirst().getName(), systemFlowPlans.getDataSinkPlans().getFirst().getName());
		assertEquals(input.getRestDataSources().getFirst().getName(), systemFlowPlans.getRestDataSources().getFirst().getName());
		assertEquals(input.getTimedDataSources().getFirst().getName(), systemFlowPlans.getTimedDataSources().getFirst().getName());
		assertEquals(input.getTransformPlans().getFirst().getName(), systemFlowPlans.getTransformPlans().getFirst().getName());
	}

	@Test
	void testRemoveTransformFlowPlan() {
		clearForFlowTests();
		TransformFlowPlan transformFlowPlan = new TransformFlowPlan("flowPlan", FlowType.TRANSFORM, "desc");
		transformFlowPlan.setSourcePlugin(PLUGIN_COORDINATES);
		savePlugin(List.of(transformFlowPlan), PLUGIN_COORDINATES);
		assertFalse(FlowPlanDatafetcherTestHelper.removeTransformFlowPlan(dgsQueryExecutor));

		clearForFlowTests();
		transformFlowPlan.setSourcePlugin(pluginService.getSystemPluginCoordinates());
		savePlugin(List.of(transformFlowPlan), pluginService.getSystemPluginCoordinates());
		assertTrue(FlowPlanDatafetcherTestHelper.removeTransformFlowPlan(dgsQueryExecutor));
	}

	@Test
	void testRemoveDataSinkPlan() {
		clearForFlowTests();
		DataSinkPlan DataSinkPlan = new DataSinkPlan("flowPlan", FlowType.DATA_SINK, null, null);
		DataSinkPlan.setSourcePlugin(PLUGIN_COORDINATES);
		savePlugin(List.of(DataSinkPlan), PLUGIN_COORDINATES);
		assertFalse(FlowPlanDatafetcherTestHelper.removeDataSinkPlan(dgsQueryExecutor));

		clearForFlowTests();
		DataSinkPlan.setSourcePlugin(pluginService.getSystemPluginCoordinates());
		savePlugin(List.of(DataSinkPlan), pluginService.getSystemPluginCoordinates());
		assertTrue(FlowPlanDatafetcherTestHelper.removeDataSinkPlan(dgsQueryExecutor));
	}

	@Test
	void testRemoveTimedDataSourcePlan() {
		clearForFlowTests();
		TimedDataSourcePlan dataSourcePlan = new TimedDataSourcePlan("flowPlan",
				FlowType.TIMED_DATA_SOURCE, null, null, null, null);
		dataSourcePlan.setSourcePlugin(PLUGIN_COORDINATES);
		savePlugin(List.of(dataSourcePlan), PLUGIN_COORDINATES);
		assertFalse(FlowPlanDatafetcherTestHelper.removeTimedDataSourcePlan(dgsQueryExecutor));

		clearForFlowTests();
		dataSourcePlan.setSourcePlugin(pluginService.getSystemPluginCoordinates());
		savePlugin(List.of(dataSourcePlan), pluginService.getSystemPluginCoordinates());
		assertTrue(FlowPlanDatafetcherTestHelper.removeTimedDataSourcePlan(dgsQueryExecutor));
	}

	@Test
	void testRemovePluginVariables() {
		clearForFlowTests();
		PluginCoordinates system = pluginService.getSystemPluginCoordinates();
		List<Variable> variables = List.of(Variable.builder().name("a").dataType(VariableDataType.STRING).value("test").build());
		pluginVariableService.saveVariables(system, variables);
		assertTrue(FlowPlanDatafetcherTestHelper.removePluginVariables(dgsQueryExecutor));
		assertThat(pluginVariableService.getVariablesByPlugin(system)).isEmpty();
	}

	@Test
	void testStartTransformFlow() {
		clearForFlowTests();
		transformFlowRepo.save(buildTransformFlow(FlowState.STOPPED));
		refreshFlowCaches();
		assertTrue(FlowPlanDatafetcherTestHelper.startTransformFlow(dgsQueryExecutor));
	}

	@Test
	void testStopTransformFlow() {
		clearForFlowTests();
		transformFlowRepo.save(buildTransformFlow(FlowState.RUNNING));
		refreshFlowCaches();
		assertTrue(FlowPlanDatafetcherTestHelper.stopTransformFlow(dgsQueryExecutor));
	}

	@Test
	void testPauseTransformFlow() {
		clearForFlowTests();
		transformFlowRepo.save(buildTransformFlow(FlowState.RUNNING));
		refreshFlowCaches();
		assertTrue(transformFlowService.getPausedFlows().isEmpty());
		assertTrue(FlowPlanDatafetcherTestHelper.pauseTransformFlow(dgsQueryExecutor));
		refreshFlowCaches();
		assertFalse(transformFlowService.getPausedFlows().isEmpty());
	}

	@Test
	void testStartDataSink() {
		clearForFlowTests();
		dataSinkRepo.save(buildDataSink(FlowState.STOPPED));
		refreshFlowCaches();
		assertTrue(FlowPlanDatafetcherTestHelper.startDataSink(dgsQueryExecutor));
	}

	@Test
	void testStopDataSink() {
		clearForFlowTests();
		dataSinkRepo.save(buildDataSink(FlowState.RUNNING));
		refreshFlowCaches();
		assertTrue(FlowPlanDatafetcherTestHelper.stopDataSink(dgsQueryExecutor));
	}

	@Test
	void testPauseDataSink() {
		clearForFlowTests();
		dataSinkRepo.save(buildDataSink(FlowState.RUNNING));
		refreshFlowCaches();
		assertTrue(dataSinkService.getPausedFlows().isEmpty());
		assertTrue(FlowPlanDatafetcherTestHelper.pauseDataSink(dgsQueryExecutor));
		refreshFlowCaches();
		assertFalse(dataSinkService.getPausedFlows().isEmpty());
	}

	@Test
	void testStartTimedIngressFlow() {
		clearForFlowTests();
		timedDataSourceRepo.save(buildTimedDataSource(FlowState.STOPPED));
		refreshFlowCaches();
		assertTrue(FlowPlanDatafetcherTestHelper.startTimedDataSource(dgsQueryExecutor));
	}

	@Test
	void testStopTimedIngressFlow() {
		clearForFlowTests();
		timedDataSourceRepo.save(buildTimedDataSource(FlowState.RUNNING));
		refreshFlowCaches();
		assertTrue(FlowPlanDatafetcherTestHelper.stopTimedDataSource(dgsQueryExecutor));
	}

	@Test
	void testSetMemoTimedIngressWhenStopped() {
		clearForFlowTests();
		timedDataSourceRepo.save(buildTimedDataSource(FlowState.STOPPED));
		refreshFlowCaches();
		assertFalse(FlowPlanDatafetcherTestHelper.setTimedDataSourceMemo(dgsQueryExecutor, null));
		assertTrue(FlowPlanDatafetcherTestHelper.setTimedDataSourceMemo(dgsQueryExecutor, "100"));
	}

	@Test
	void testSetMemoTimedIngressWhenRunning() {
		clearForFlowTests();
		timedDataSourceRepo.save(buildTimedDataSource(FlowState.RUNNING));
		refreshFlowCaches();
		assertFalse(FlowPlanDatafetcherTestHelper.setTimedDataSourceMemo(dgsQueryExecutor, "100"));
	}

	@Test
	void testSavePluginVariables() {
		assertTrue(FlowPlanDatafetcherTestHelper.savePluginVariables(dgsQueryExecutor));
		PluginVariables variables = pluginVariableRepo.findBySourcePlugin(pluginService.getSystemPluginCoordinates()).orElse(null);
		assertThat(variables).isNotNull();
		assertThat(variables.getVariables()).hasSize(1).anyMatch(v -> v.getName().equals("var"));
	}

	@Test
	void testSetPluginVariableValues() {
		PluginVariables variables = new PluginVariables();
		variables.setSourcePlugin(PLUGIN_COORDINATES);
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
		DeltaFile before1 =fullFlowExemplarService.postTransformDeltaFile(did1);
		deltaFileRepo.save(before1);
		DeltaFile before2 =fullFlowExemplarService.postTransformDeltaFile(did2);
		before2.getFlows().forEach(f -> f.setId(UUID.randomUUID()));
		deltaFileRepo.save(before2);
		DeltaFile before3 =fullFlowExemplarService.postTransformDeltaFile(did3);
		before3.setDataSource("flow3");
		before3.getFlows().forEach(f -> f.setId(UUID.randomUUID()));
		deltaFileRepo.save(before3);

		deltaFilesService.handleActionEvent(actionEvent("error", did1, before1.lastFlow().getId()));
		deltaFilesService.handleActionEvent(actionEvent("error", did2, before2.lastFlow().getId()));
		deltaFilesService.handleActionEvent(actionEvent("error", did3, before3.lastFlow().getId()));

		DeltaFile during1 = deltaFilesService.getDeltaFile(did1);
		DeltaFile during2 = deltaFilesService.getDeltaFile(did2);
		DeltaFile during3 = deltaFilesService.getDeltaFile(did3);

		assertNull(during1.lastFlow().lastAction().getNextAutoResume());
		assertNull(during2.lastFlow().lastAction().getNextAutoResume());
		assertNull(during3.lastFlow().lastAction().getNextAutoResume());

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

		assertNotNull(final1.lastFlow().lastAction().getNextAutoResume());
		assertNotNull(final2.lastFlow().lastAction().getNextAutoResume());
		assertNotNull(final3.lastFlow().lastAction().getNextAutoResume());

		assertEquals("policy1", final1.lastFlow().lastAction().getNextAutoResumeReason());
		assertEquals("policy1", final2.lastFlow().lastAction().getNextAutoResumeReason());
		assertEquals("policy2", final3.lastFlow().lastAction().getNextAutoResumeReason());
	}

	@Test
	void testResumePolicyDryRun() {
		DeltaFile deltaFile = utilService.buildErrorDeltaFile(UUID.randomUUID(), "flow1", "errorCause", "context", NOW);
		deltaFile.setDataSource("flow1");
		deltaFileRepo.save(deltaFile);

		DeltaFile deltaFile2 = utilService.buildErrorDeltaFile(UUID.randomUUID(), "flow2", "errorCause", "context", NOW);
		deltaFile.setDataSource("flow2");
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
		DeltaFile expected = deltaFilesService.ingressRest(buildDataSource(DATA_SOURCE), INGRESS_INPUT, OffsetDateTime.now(), OffsetDateTime.now());

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
				new DeltaFileGraphQLQuery.Builder().did(expected.getDid()).build(), DELTA_FILE_PROJECTION_ROOT, SCALARS);

		Object response = dgsQueryExecutor.executeAndExtractJsonPath(
				graphQLQueryRequest.serialize(),
				"data." + DgsConstants.QUERY.DeltaFile
		);
		DeltaFile actual = flowMapper().convertValue(response, DeltaFile.class);

		assertEqualsIgnoringDates(expected, actual);
	}

	@Test
	void deltaFiles() {
		DeltaFile deltaFile = utilService.buildErrorDeltaFile(UUID.randomUUID(), "dataSource", "errorCause", "context", NOW);
		deltaFile.setContentDeleted(NOW);
		deltaFile.setContentDeletedReason("contentDeletedReason");
		Action erroredAction = deltaFile.getFlow("dataSource").lastAction();
		erroredAction.setNextAutoResume(NOW);
		erroredAction.setNextAutoResumeReason("nextAutoResumeReason");
		erroredAction.setCreated(NOW.minusSeconds(1));

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

		Object response = dgsQueryExecutor.executeAndExtractJsonPath(
				graphQLQueryRequest.serialize(),
				"data." + DgsConstants.QUERY.DeltaFiles
		);

		DeltaFiles actual = flowMapper().convertValue(response, DeltaFiles.class);

		assertEquals(expected.getOffset(), actual.getOffset());
		assertEquals(expected.getCount(), actual.getCount());
		assertEquals(expected.getTotalCount(), actual.getTotalCount());
		assertEqualsIgnoringDates(expected.getDeltaFiles().getFirst(), actual.getDeltaFiles().getFirst());
	}

	@Test
	void resume() {
		DeltaFile input = fullFlowExemplarService.postErrorDeltaFile(UUID.randomUUID());
		DeltaFile second =fullFlowExemplarService.postTransformDeltaFile(UUID.randomUUID());
		second.getFlows().forEach(f -> f.setId(UUID.randomUUID()));
		deltaFileRepo.saveAll(List.of(input, second));

		UUID badDid = UUID.randomUUID();
		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
				new ResumeGraphQLQuery.Builder()
						.dids(List.of(input.getDid(), second.getDid(), badDid))
						.build(),
				new ResumeProjectionRoot<>().did().success().error(),
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
		assertEquals(2, afterResumeFile.lastFlow().getActions().size());
		assertEquals(ActionState.RETRIED, afterResumeFile.lastFlow().firstAction().getState());
		assertEquals(QUEUED, afterResumeFile.lastFlow().lastAction().getState());
		// StateMachine will queue the failed egress action again leaving the DeltaFile in the IN_FLIGHT stage
		assertEquals(DeltaFileFlowState.IN_FLIGHT, afterResumeFile.lastFlow().getState());
		assertEquals(DeltaFileStage.IN_FLIGHT, afterResumeFile.getStage());
	}

	@Test
	void getsPlugins() throws IOException {
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), PluginEntity.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), PluginEntity.class));

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(PluginsGraphQLQuery.newRequest().build(), PLUGINS_PROJECTION_ROOT);

		List<Plugin> plugins =
				dgsQueryExecutor.executeAndExtractJsonPathAsObject(graphQLQueryRequest.serialize(),
						"data.plugins[*]", new TypeRef<>() {});

		assertEquals(2, plugins.size());

		validatePlugin1(plugins.getFirst());
	}

	@Test
	void registersPlugin() throws IOException {
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), PluginEntity.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-3.json"), PluginEntity.class));

		PluginEntity plugin = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), PluginEntity.class);
		ResponseEntity<String> response = postPluginRegistration(plugin);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		List<PluginEntity> plugins = pluginRepository.findAll();
		assertEquals(3, plugins.size());
	}

	@Test
	void overwritesExistingPlugin() throws IOException {
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), PluginEntity.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-3.json"), PluginEntity.class));

		PluginEntity plugin = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), PluginEntity.class);
		ResponseEntity<String> response = postPluginRegistration(plugin);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(3, pluginRepository.count());
		Optional<PluginEntity> result = pluginRepository.findById(plugin.getKey());
		assertTrue(result.isPresent());
		assertEquals("1.0.0", result.get().getPluginCoordinates().getVersion());

		PluginEntity pluginV2 = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1-v2.json"), PluginEntity.class);
		response = postPluginRegistration(pluginV2);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(3, pluginRepository.count());
		result = pluginRepository.findById(plugin.getKey());
		assertTrue(result.isPresent());
		assertEquals("2.0.0", result.get().getPluginCoordinates().getVersion());
	}

	@Test
	void overwritesExistingPluginWithRemovedFlowPlan() throws IOException {
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), PluginEntity.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-3.json"), PluginEntity.class));

		PluginEntity plugin = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), PluginEntity.class);
		plugin.setFlowPlans(List.of(new TransformFlowPlan("transformPlan", FlowType.TRANSFORM, "description")));
		ResponseEntity<String> response = postPluginRegistration(plugin);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(3, pluginRepository.count());

		PluginEntity pluginV2 = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), PluginEntity.class);
		response = postPluginRegistration(pluginV2);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(3, pluginRepository.count());
		Optional<PluginEntity> result = pluginService.getPlugin(plugin.getPluginCoordinates());
		assertTrue(result.isPresent());
		assertEquals(0, result.get().getFlowPlans().size());
	}

	ResponseEntity<String> postPluginRegistration(PluginEntity plugin) throws JsonProcessingException {
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
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), PluginEntity.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-3.json"), PluginEntity.class));
		PluginEntity existingPlugin = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), PluginEntity.class);
		existingPlugin.setVersion("0.0.9");
		existingPlugin.setDescription("changed");
		pluginRepository.save(existingPlugin);

		PluginEntity plugin = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), PluginEntity.class);
		ResponseEntity<String> response = postPluginRegistration(plugin);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		List<PluginEntity> plugins = pluginRepository.findAll();
		assertEquals(3, plugins.size());
		Optional<PluginEntity> newPlugin = pluginRepository.findById(plugin.getKey());
		assertThat(newPlugin).isPresent();
		assertEquals(plugin.getVersion(), newPlugin.get().getVersion());
	}

	@Test
	void registerPluginReturnsErrorsOnMissingDependencies() throws IOException {
		PluginEntity plugin = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), PluginEntity.class);
		ResponseEntity<String> response = postPluginRegistration(plugin);

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		if (response.getBody() == null) {
			fail("Missing response body");
			return;
		}
		assertTrue(response.getBody().contains("Plugin dependency not registered: org.deltafi:plugin-2:1.0.0."));
		assertTrue(response.getBody().contains("Plugin dependency not registered: org.deltafi:plugin-3:1.0.0."));
		List<PluginEntity> plugins = pluginRepository.findAll();
		assertTrue(plugins.isEmpty());
	}

	@Test
	void uninstallPluginSuccess() throws IOException {
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), PluginEntity.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-3.json"), PluginEntity.class));

		PluginEntity plugin = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), PluginEntity.class);
		UninstallPluginGraphQLQuery uninstallPluginGraphQLQuery =
				UninstallPluginGraphQLQuery.newRequest()
						.pluginCoordinates(plugin.getPluginCoordinates()).build();

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(uninstallPluginGraphQLQuery, UNINSTALL_PLUGIN_PROJECTION_ROOT);

		Result mockResult = Result.successResult();
		Mockito.when(deployerService.uninstallPlugin(plugin.getPluginCoordinates(), false)).thenReturn(mockResult);
		Result result = dgsQueryExecutor.executeAndExtractJsonPathAsObject(graphQLQueryRequest.serialize(),
				"data." + uninstallPluginGraphQLQuery.getOperationName(), Result.class);

		Mockito.verify(deployerService).uninstallPlugin(plugin.getPluginCoordinates(), false);
		assertThat(result).isEqualTo(mockResult);
	}

	@Test
	void forcedUninstallPluginSuccess() throws IOException {
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), PluginEntity.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-3.json"), PluginEntity.class));

		PluginEntity plugin = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), PluginEntity.class);
		ForcePluginUninstallGraphQLQuery query =
				ForcePluginUninstallGraphQLQuery.newRequest()
						.pluginCoordinates(plugin.getPluginCoordinates()).build();

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(query, FORCED_UNINSTALL_PLUGIN_PROJECTION_ROOT);

		Result mockResult = Result.successResult();
		Mockito.when(deployerService.uninstallPlugin(plugin.getPluginCoordinates(), true)).thenReturn(mockResult);
		Result result = dgsQueryExecutor.executeAndExtractJsonPathAsObject(graphQLQueryRequest.serialize(),
				"data." + query.getOperationName(), Result.class);

		Mockito.verify(deployerService).uninstallPlugin(plugin.getPluginCoordinates(), true);
		assertThat(result).isEqualTo(mockResult);
	}

	@Test
	void findPluginsWithDependency() throws IOException {
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), PluginEntity.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), PluginEntity.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-3.json"), PluginEntity.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-4.json"), PluginEntity.class));

		List<PluginEntity> matched = pluginRepository.findPluginsWithDependency(
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
	void testFindReadyForAutoResume() {
		DeltaFile shouldResume = utilService.buildDeltaFile(UUID.randomUUID(), TRANSFORM_FLOW_NAME, DeltaFileStage.ERROR, NOW, NOW);
		shouldResume.firstFlow().setActions(Arrays.asList(autoResumeIngress(NOW), autoResumeOther(NOW), autoResumeHit(NOW)));
		shouldResume.firstFlow().updateState();
		shouldResume.setVersion(1);

		DeltaFile shouldNotResume = utilService.buildDeltaFile(UUID.randomUUID(), TRANSFORM_FLOW_NAME, DeltaFileStage.ERROR, NOW, NOW.minusSeconds(1));
		shouldNotResume.firstFlow().setActions(Arrays.asList(autoResumeIngress(NOW), autoResumeMiss(NOW)));
		shouldNotResume.firstFlow().updateState();
		shouldNotResume.setVersion(1);

		DeltaFile notResumable = utilService.buildDeltaFile(UUID.randomUUID(), TRANSFORM_FLOW_NAME, DeltaFileStage.ERROR, NOW, NOW.minusSeconds(2));
		notResumable.firstFlow().setActions(Arrays.asList(autoResumeIngress(NOW), autoResumeNotSet(NOW)));
		notResumable.firstFlow().updateState();
		notResumable.setVersion(1);

		DeltaFile cancelled = utilService.buildDeltaFile(UUID.randomUUID(), TRANSFORM_FLOW_NAME, DeltaFileStage.CANCELLED, NOW, NOW.minusSeconds(3));
		cancelled.firstFlow().setActions(Arrays.asList(autoResumeIngress(NOW), autoResumeOther(NOW), autoResumeHit(NOW)));
		cancelled.firstFlow().updateState();
		cancelled.setVersion(1);

		DeltaFile contentDeleted = utilService.buildDeltaFile(UUID.randomUUID(), TRANSFORM_FLOW_NAME, DeltaFileStage.ERROR, NOW, NOW.minusSeconds(4));
		contentDeleted.firstFlow().setActions(List.of(autoResumeIngress(NOW), autoResumeOther(NOW),autoResumeHit(NOW)));
		contentDeleted.setContentDeleted(NOW);
		contentDeleted.firstFlow().updateState();
		contentDeleted.setVersion(1);

		DeltaFile shouldAlsoResume = utilService.buildDeltaFile(UUID.randomUUID(), TRANSFORM_FLOW_NAME, DeltaFileStage.ERROR, NOW, NOW.minusSeconds(5));
		shouldAlsoResume.firstFlow().setActions(Arrays.asList(autoResumeIngress(NOW), autoResumeOther(NOW), autoResumeHit(NOW)));
		shouldAlsoResume.firstFlow().updateState();
		shouldAlsoResume.setVersion(1);

		deltaFileRepo.insertBatch(List.of(shouldResume, shouldNotResume, notResumable, cancelled, contentDeleted, shouldAlsoResume), 1000);

		List<DeltaFile> hits = deltaFileRepo.findReadyForAutoResume(NOW, false, 5000);
		assertEquals(2, hits.size());
		assertEquals(Stream.of(shouldResume, shouldAlsoResume).map(DeltaFile::getDid).sorted().toList(),
				hits.stream().map(DeltaFile::getDid).sorted().toList());

		// test that splitting into two batches works
		assertEquals(2, deltaFilesService.autoResume(NOW, 1));

		// metrics should be incremented once for each batch
		Mockito.verify(metricService, times(2)).increment
				(new Metric(DeltaFiConstants.FILES_AUTO_RESUMED, 1)
						.addTag(DATA_SOURCE, TRANSFORM_FLOW_NAME));
		Mockito.verifyNoMoreInteractions(metricService);
	}

    @Test
    void testFindReadyForAutoResume_egressDisabled() {
        DeltaFile input = fullFlowExemplarService.postTransformDeltaFile(new UUID(0, 0));
        OffsetDateTime nextAutoResume = OffsetDateTime.now().minusMinutes(1);
        input.setStage(DeltaFileStage.ERROR);
        DeltaFileFlow dataSink = input.lastFlow();
        dataSink.setState(DeltaFileFlowState.ERROR);
        dataSink.setNextAutoResume(nextAutoResume);
        Action egressAction = dataSink.lastAction();
        egressAction.setState(ERROR);
        egressAction.setNextAutoResume(nextAutoResume);
        deltaFileRepo.save(input);

        assertThat(deltaFileRepo.findReadyForAutoResume(NOW, false, 5000)).hasSize(1);
        assertThat(deltaFileRepo.findReadyForAutoResume(NOW, true, 5000)).isEmpty();
    }

	@Test
	void testFindForResumeByFlowTypeAndName() {
		// Has an error in the transform flow with the matching flow name
		DeltaFile shouldResume = makeErroredDeltaFile();
		DeltaFile shouldAlsoResume = makeErroredDeltaFile();
		shouldAlsoResume.lastFlow().setErrorAcknowledged(NOW);

		// has matching flow type/name but no errors
		DeltaFile noErrors = fullFlowExemplarService.postTransformDeltaFile(UUID.randomUUID());
		makeFlowsUnique(noErrors);

		// deltaFile has an error in a transform flow but different flow name
		DeltaFile differentFlowName = makeErroredDeltaFile();
		differentFlowName.lastFlow().setFlowDefinition(flowDefinitionService.getOrCreateFlow("differentFlowName", differentFlowName.lastFlow().getFlowDefinition().getType()));

		// deltafi has an error in the matching flow name but the flow type is data sink instead of transform
		DeltaFile differentFlowType = makeEgressErroredDeltaFile();

		// not resumed because it is not in an error stage
		DeltaFile cancelled = makeErroredDeltaFile();
		cancelled.setStage(DeltaFileStage.CANCELLED);

		// not resumed because the content was removed
		DeltaFile contentDeleted = makeErroredDeltaFile();
		contentDeleted.setContentDeleted(NOW);

		deltaFileRepo.insertBatch(List.of(shouldResume, shouldAlsoResume, noErrors, differentFlowType, differentFlowName, cancelled, contentDeleted), 1000);

		assertThat(deltaFileRepo.findForResumeByFlowTypeAndName(FlowType.TRANSFORM, TRANSFORM_FLOW_NAME,  true,  10)).hasSize(2).contains(shouldResume, shouldAlsoResume);
		assertThat(deltaFileRepo.findForResumeByFlowTypeAndName(FlowType.TRANSFORM, TRANSFORM_FLOW_NAME,  false,  10)).hasSize(1).contains(shouldResume);
		assertThat(deltaFileRepo.findForResumeByFlowTypeAndName(FlowType.TRANSFORM, TRANSFORM_FLOW_NAME, true, 1)).hasSize(1);
	}

	@Test
	void testFindForResumeByErrorCause() {
		// next two deltaFiles are in an error state with a matching error cause
		DeltaFile shouldResumeTransformError = makeErroredDeltaFile();
		DeltaFile shouldResumeEgressError = makeEgressErroredDeltaFile();
		shouldResumeEgressError.lastFlow().setErrorAcknowledged(NOW);

		DeltaFile transformErrorDiffCause = makeErroredDeltaFile();
		transformErrorDiffCause.lastFlow().setErrorOrFilterCause("different error cause");
		DeltaFile egressErrorDiffCause = makeEgressErroredDeltaFile();
		egressErrorDiffCause.lastFlow().setErrorOrFilterCause("different error cause");

		// has a matching errorOrFilter cause but different state
		DeltaFile filterCause = makeErroredDeltaFile();
		filterCause.lastFlow().setState(DeltaFileFlowState.FILTERED);

		// not resumed because it is not in an error stage
		DeltaFile cancelled = makeErroredDeltaFile();
		cancelled.setStage(DeltaFileStage.CANCELLED);

		// not resumed because the content was removed
		DeltaFile contentDeleted = makeErroredDeltaFile();
		contentDeleted.setContentDeleted(NOW);

		deltaFileRepo.insertBatch(List.of(shouldResumeTransformError, shouldResumeEgressError, transformErrorDiffCause, egressErrorDiffCause, filterCause, cancelled, contentDeleted), 1000);

		assertThat(deltaFileRepo.findForResumeByErrorCause("transform failed",  true,10)).hasSize(2).contains(shouldResumeTransformError, shouldResumeEgressError);
		assertThat(deltaFileRepo.findForResumeByErrorCause("transform failed",  false, 10)).hasSize(1).contains(shouldResumeTransformError);
		assertThat(deltaFileRepo.findForResumeByErrorCause("transform failed", true, 1)).hasSize(1);
	}

	private DeltaFile makeErroredDeltaFile() {
		return makeFlowsUnique(fullFlowExemplarService.postTransformHadErrorDeltaFile(UUID.randomUUID()));
	}

	private DeltaFile makeEgressErroredDeltaFile() {
		DeltaFile deltaFile = makeFlowsUnique(fullFlowExemplarService.postTransformDeltaFile(UUID.randomUUID()));
		DeltaFileFlow egressFlow = deltaFile.lastFlow();
		egressFlow.getActions().getLast().error(NOW, NOW, NOW, "transform failed", "failed context");
		egressFlow.updateState();
		deltaFile.updateState(NOW);
		return deltaFile;
	}

	private DeltaFile makeFlowsUnique(DeltaFile deltaFile) {
		deltaFile.getFlows().forEach(flow -> flow.setId(UUID.randomUUID()));
		return deltaFile;
	}

	@Test
	void testRequeue() {
		DeltaFile oneHit = utilService.buildDeltaFile(UUID.randomUUID(), "flow1", DeltaFileStage.IN_FLIGHT, NOW, NOW.minusSeconds(1000));
		oneHit.firstFlow().setFlowDefinition(flowDefinitionService.getOrCreateFlow("flow1", FlowType.TRANSFORM));
		oneHit.firstFlow().setState(DeltaFileFlowState.IN_FLIGHT);
		oneHit.firstFlow().addAction("hit", null, ActionType.TRANSFORM, QUEUED, NOW.minusSeconds(1000));
		DeltaFileFlow flow2 = oneHit.addFlow(flowDefinitionService.getOrCreateFlow("flow3", FlowType.DATA_SINK), oneHit.firstFlow(), NOW.minusSeconds(1000));
		flow2.addAction("miss", null, ActionType.TRANSFORM, QUEUED, NOW.plusSeconds(1000));

		DeltaFile twoHits = utilService.buildDeltaFile(UUID.randomUUID(), "flow1", DeltaFileStage.IN_FLIGHT, NOW, NOW.minusSeconds(1000));
		twoHits.firstFlow().setState(DeltaFileFlowState.IN_FLIGHT);
		twoHits.setRequeueCount(5);
		twoHits.firstFlow().setFlowDefinition(flowDefinitionService.getOrCreateFlow("flow1", FlowType.TRANSFORM));
		twoHits.firstFlow().addAction("hit", null, ActionType.TRANSFORM, QUEUED, NOW.minusSeconds(1000));
		flow2 = twoHits.addFlow(flowDefinitionService.getOrCreateFlow("flow2", FlowType.TRANSFORM), twoHits.firstFlow(), NOW.minusSeconds(1000));
		flow2.addAction("miss", null, ActionType.TRANSFORM, QUEUED, NOW.plusSeconds(1000));
		DeltaFileFlow flow3 = twoHits.addFlow(flowDefinitionService.getOrCreateFlow("flow3", FlowType.DATA_SINK), twoHits.firstFlow(), NOW.minusSeconds(1000));
		flow3.addAction("hit", null, ActionType.TRANSFORM, QUEUED, NOW.minusSeconds(1000));

		DeltaFile miss = utilService.buildDeltaFile(UUID.randomUUID(), "flow1", DeltaFileStage.IN_FLIGHT, NOW, NOW.plusSeconds(1000));
		miss.firstFlow().setState(DeltaFileFlowState.IN_FLIGHT);
		miss.firstFlow().addAction("miss", null, ActionType.TRANSFORM, QUEUED, NOW.plusSeconds(1000));
		flow2 = miss.addFlow(flowDefinitionService.getOrCreateFlow("flow2", FlowType.TRANSFORM), miss.firstFlow(), NOW.plusSeconds(1000));
		flow2.addAction("excluded", null, ActionType.TRANSFORM, QUEUED, NOW.plusSeconds(1000));
		flow3 = miss.addFlow(flowDefinitionService.getOrCreateFlow("flow3", FlowType.DATA_SINK), miss.firstFlow(), NOW.plusSeconds(1000));
		flow3.addAction("miss", null, ActionType.TRANSFORM, QUEUED, NOW.plusSeconds(1000));
		miss.updateFlowArrays();

		DeltaFile excludedByDid = utilService.buildDeltaFile(UUID.randomUUID(), "flow1", DeltaFileStage.IN_FLIGHT, NOW, NOW.minusSeconds(1000));
		excludedByDid.firstFlow().setState(DeltaFileFlowState.IN_FLIGHT);
		excludedByDid.firstFlow().addAction("hit",null,  ActionType.TRANSFORM, QUEUED, NOW.minusSeconds(1000));

		DeltaFile wrongState = utilService.buildDeltaFile(UUID.randomUUID(), "flow1", DeltaFileStage.CANCELLED, NOW, NOW.minusSeconds(1000));
		wrongState.firstFlow().setState(DeltaFileFlowState.CANCELLED);
		wrongState.firstFlow().addAction("hit", null, ActionType.TRANSFORM, CANCELLED, NOW.minusSeconds(1000));

		deltaFileRepo.insertBatch(List.of(oneHit, twoHits, miss, excludedByDid, wrongState), 1000);

		List<DeltaFile> hits = deltaFileRepo.findForRequeue(NOW, Duration.ofSeconds(30),
				Set.of("excluded", "anotherAction"), Set.of(excludedByDid.getDid(), UUID.randomUUID()), false, 5000);

		assertEquals(2, hits.size());

		ActionConfiguration ac = new ActionConfiguration("hit", ActionType.TRANSFORM, "type");
		transformFlowRepo.save(FlowBuilders.buildTransform("flow1", List.of(ac), FlowState.RUNNING, false));
		transformFlowRepo.save(FlowBuilders.buildTransform("flow2", List.of(ac), FlowState.RUNNING, false));
		dataSinkRepo.save(FlowBuilders.buildTransform("flow3", List.of(ac), FlowState.RUNNING, false));
		refreshFlowCaches();

		deltaFiPropertiesService.getDeltaFiProperties().setRequeueDuration(Duration.ofSeconds(30));
		deltaFilesService.requeue();

		DeltaFile oneHitAfter = deltaFileRepo.findById(oneHit.getDid()).orElse(null);
		DeltaFile twoHitsAfter = deltaFileRepo.findById(twoHits.getDid()).orElse(null);
		DeltaFile missAfter = deltaFileRepo.findById(miss.getDid()).orElse(null);

        assert oneHitAfter != null;
        assertEquals(1, oneHitAfter.getRequeueCount());
        assert twoHitsAfter != null;
        assertEquals(6, twoHitsAfter.getRequeueCount());
		assertEquals(miss, missAfter);
		assertNotEquals(oneHit.firstFlow().lastAction().getModified(), oneHitAfter.firstFlow().lastAction().getModified());
		assertEquals(oneHit.lastFlow().firstAction().getModified(), oneHitAfter.lastFlow().firstAction().getModified());
		assertNotEquals(twoHits.firstFlow().lastAction().getModified(), twoHitsAfter.firstFlow().lastAction().getModified());
		List<DeltaFileFlow> flowsList = twoHits.getFlows().stream()
				.sorted(Comparator.comparingInt(DeltaFileFlow::getNumber))
				.toList();
		List<DeltaFileFlow> flowsAfterList = twoHitsAfter.getFlows().stream()
				.sorted(Comparator.comparingInt(DeltaFileFlow::getNumber))
				.toList();
		assertEquals(
				flowsList.get(1).firstAction().getModified(),
				flowsAfterList.get(1).firstAction().getModified()
		);
		assertNotEquals(
				flowsList.get(2).lastAction().getModified(),
				flowsAfterList.get(2).lastAction().getModified()
		);
	}

	@Test
	void testRequeueFlowMissingActions() {
		DeltaFile oneHit = utilService.buildDeltaFile(UUID.randomUUID(), "flow1", DeltaFileStage.IN_FLIGHT, NOW, NOW.minusSeconds(1000));
		oneHit.firstFlow().setFlowDefinition(flowDefinitionService.getOrCreateFlow("flow1", FlowType.TRANSFORM));
		oneHit.firstFlow().setState(DeltaFileFlowState.IN_FLIGHT);
		oneHit.firstFlow().setActions(new ArrayList<>());
		deltaFileRepo.save(oneHit);

		assertDoesNotThrow(() -> deltaFilesService.requeue());
	}

    @Test
    void testFindForRequeue_egressDisabled() {
        DeltaFile input = fullFlowExemplarService.postTransformDeltaFile(new UUID(0, 0));
        deltaFileRepo.save(input);

        OffsetDateTime requeueTime = NOW.plusMinutes(6);
        assertThat(deltaFileRepo.findForRequeue(requeueTime, Duration.ofMinutes(1), Set.of(), Set.of(), false, 10)).hasSize(1);
        assertThat(deltaFileRepo.findForRequeue(requeueTime, Duration.ofMinutes(1), Set.of(), Set.of(), true, 10)).isEmpty();
    }

	@Test
	void testRequeuePausedFlows() {
		DeltaFile multipleTypes = utilService.buildDeltaFile(UUID.randomUUID(), "rest1", DeltaFileStage.IN_FLIGHT, NOW, NOW.minusSeconds(1000));
		multipleTypes.firstFlow().setFlowDefinition(flowDefinitionService.getOrCreateFlow("rest1", FlowType.REST_DATA_SOURCE));
		multipleTypes.firstFlow().setState(DeltaFileFlowState.PAUSED);
		multipleTypes.addFlow(flowDefinitionService.getOrCreateFlow("transform1", FlowType.TRANSFORM), multipleTypes.firstFlow(), NOW.minusSeconds(1000))
				.setState(DeltaFileFlowState.PAUSED);
		multipleTypes.addFlow(flowDefinitionService.getOrCreateFlow("timed1", FlowType.TIMED_DATA_SOURCE), multipleTypes.firstFlow(), NOW.minusSeconds(1000))
				.setState(DeltaFileFlowState.PAUSED);
		multipleTypes.addFlow(flowDefinitionService.getOrCreateFlow("sink1", FlowType.DATA_SINK), multipleTypes.firstFlow(), NOW.minusSeconds(1000))
				.setState(DeltaFileFlowState.PAUSED);
		multipleTypes.setPaused(true);

		DeltaFile partiallySkipped = utilService.buildDeltaFile(UUID.randomUUID(), "rest1", DeltaFileStage.IN_FLIGHT, NOW, NOW.minusSeconds(1000));
		partiallySkipped.firstFlow().setFlowDefinition(flowDefinitionService.getOrCreateFlow("rest1", FlowType.REST_DATA_SOURCE));
		partiallySkipped.firstFlow().setState(DeltaFileFlowState.PAUSED);
		partiallySkipped.addFlow(flowDefinitionService.getOrCreateFlow("skipTransform", FlowType.TRANSFORM), partiallySkipped.firstFlow(), NOW.minusSeconds(1000))
				.setState(DeltaFileFlowState.PAUSED);
		partiallySkipped.addFlow(flowDefinitionService.getOrCreateFlow("timed1", FlowType.TIMED_DATA_SOURCE), partiallySkipped.firstFlow(), NOW.minusSeconds(1000))
				.setState(DeltaFileFlowState.PAUSED);
		partiallySkipped.addFlow(flowDefinitionService.getOrCreateFlow("skipSink", FlowType.DATA_SINK), partiallySkipped.firstFlow(), NOW.minusSeconds(1000))
				.setState(DeltaFileFlowState.PAUSED);
		partiallySkipped.setPaused(true);

		DeltaFile skipAll = utilService.buildDeltaFile(UUID.randomUUID(), "skipRest", DeltaFileStage.IN_FLIGHT, NOW, NOW.minusSeconds(1000));
		skipAll.firstFlow().setFlowDefinition(flowDefinitionService.getOrCreateFlow("skipRest", FlowType.REST_DATA_SOURCE));
		skipAll.firstFlow().setState(DeltaFileFlowState.PAUSED);
		skipAll.addFlow(flowDefinitionService.getOrCreateFlow("skipTransform", FlowType.TRANSFORM), skipAll.firstFlow(), NOW.minusSeconds(1000))
				.setState(DeltaFileFlowState.PAUSED);
		skipAll.addFlow(flowDefinitionService.getOrCreateFlow("skipTimed", FlowType.TIMED_DATA_SOURCE), skipAll.firstFlow(), NOW.minusSeconds(1000))
				.setState(DeltaFileFlowState.PAUSED);
		skipAll.addFlow(flowDefinitionService.getOrCreateFlow("skipSink", FlowType.DATA_SINK), skipAll.firstFlow(), NOW.minusSeconds(1000))
				.setState(DeltaFileFlowState.PAUSED);
		skipAll.setPaused(true);

		DeltaFile notPaused = utilService.buildDeltaFile(UUID.randomUUID(), "rest1", DeltaFileStage.IN_FLIGHT, NOW, NOW.minusSeconds(1000));
		notPaused.firstFlow().setFlowDefinition(flowDefinitionService.getOrCreateFlow("rest1", FlowType.REST_DATA_SOURCE));
		notPaused.firstFlow().setState(DeltaFileFlowState.PAUSED);
		notPaused.addFlow(flowDefinitionService.getOrCreateFlow("transform1", FlowType.TRANSFORM), notPaused.firstFlow(), NOW.minusSeconds(1000))
				.setState(DeltaFileFlowState.PAUSED);
		notPaused.addFlow(flowDefinitionService.getOrCreateFlow("timed1", FlowType.TIMED_DATA_SOURCE), notPaused.firstFlow(), NOW.minusSeconds(1000))
				.setState(DeltaFileFlowState.PAUSED);
		notPaused.addFlow(flowDefinitionService.getOrCreateFlow("sink1", FlowType.DATA_SINK), notPaused.firstFlow(), NOW.minusSeconds(1000))
				.setState(DeltaFileFlowState.PAUSED);
		notPaused.setPaused(false);

		deltaFileRepo.insertBatch(List.of(multipleTypes, partiallySkipped, skipAll, notPaused), 1000);

		List<DeltaFile> hits = deltaFileRepo.findPausedForRequeue(
				Set.of("skipRest"),
				Set.of("skipTimed"),
				Set.of("skipTransform"),
				Set.of("skipSink"),
                true,
				5000);
		assertEquals(Stream.of(multipleTypes.getDid(), partiallySkipped.getDid()).sorted().toList(),
				hits.stream().map(DeltaFile::getDid).sorted().toList());

		ActionConfiguration ac = new ActionConfiguration("action", ActionType.TRANSFORM, "type");
		restDataSourceRepo.save(buildRestDataSource("rest1", FlowState.RUNNING));
		restDataSourceRepo.save(buildRestDataSource("skipRest", FlowState.PAUSED));
		transformFlowRepo.save(FlowBuilders.buildTransform("transform1", List.of(ac), FlowState.RUNNING, false));
		transformFlowRepo.save(FlowBuilders.buildTransform("skipTransform", List.of(ac), FlowState.PAUSED, false));
		timedDataSourceRepo.save(buildTimedDataSource("timed1", ac, "0 0 0 1 1 ?", FlowState.RUNNING));
		timedDataSourceRepo.save(buildTimedDataSource("skipTimed", ac, "0 0 0 1 1 ?", FlowState.PAUSED));
		dataSinkRepo.save(buildDataSink("sink1", ac, FlowState.RUNNING, false));
		dataSinkRepo.save(buildDataSink("skipSink", ac, FlowState.PAUSED, false));
		refreshFlowCaches();

		deltaFiPropertiesService.getDeltaFiProperties().setRequeueDuration(Duration.ofSeconds(30));
		deltaFilesService.requeuePausedFlows();

		DeltaFile multipleTypesAfter = deltaFileRepo.findById(multipleTypes.getDid()).orElse(null);
		DeltaFile partiallySkippedAfter = deltaFileRepo.findById(partiallySkipped.getDid()).orElse(null);
		DeltaFile skipAllAfter = deltaFileRepo.findById(skipAll.getDid()).orElse(null);

		assertNotNull(multipleTypesAfter);
		assertNotNull(partiallySkippedAfter);
		assertNotNull(skipAllAfter);

		multipleTypesAfter.getFlows().forEach(flow ->
				assertNotEquals(DeltaFileFlowState.PAUSED, flow.getState()));

		List<DeltaFileFlow> flows = partiallySkippedAfter.getFlows().stream()
				.sorted(Comparator.comparingInt(DeltaFileFlow::getNumber))
				.toList();
		assertNotEquals(DeltaFileFlowState.PAUSED, flows.get(0).getState()); // rest1
		assertEquals(DeltaFileFlowState.PAUSED, flows.get(1).getState()); // skipTransform
		assertNotEquals(DeltaFileFlowState.PAUSED, flows.get(2).getState()); // timed1
		assertEquals(DeltaFileFlowState.PAUSED, flows.get(3).getState()); // skipSink

		skipAllAfter.getFlows().forEach(flow ->
				assertEquals(DeltaFileFlowState.PAUSED, flow.getState()));
	}

    @Test
    void findPausedToQueue_egressDisabled() {
        DeltaFile input = fullFlowExemplarService.postTransformDeltaFile(new UUID(0, 0));
        input.setPaused(true);
        DeltaFileFlow dataSink = input.lastFlow();
        dataSink.setState(DeltaFileFlowState.PAUSED);
        deltaFileRepo.save(input);

        assertThat(deltaFileRepo.findPausedForRequeue(Set.of(), Set.of(), Set.of(), Set.of(), false, 10)).hasSize(1);
        assertThat(deltaFileRepo.findPausedForRequeue(Set.of(), Set.of(), Set.of(), Set.of(), true, 10)).isEmpty();
    }

	@Test
	void batchedBulkDeleteByDidIn() {
		List<DeltaFile> deltaFiles = Stream.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()).map(utilService::buildDeltaFile).toList();
		deltaFiles.get(0).getAnnotations().add(new Annotation("key1", "val1"));
		deltaFiles.get(1).getAnnotations().add(new Annotation("key2", "val2"));
		deltaFiles.get(2).getAnnotations().add(new Annotation("key3", "val3"));
		deltaFileRepo.saveAll(deltaFiles);

		deltaFileFlowRepo.saveAll(deltaFiles.stream().map(DeltaFile::getFlows).flatMap(Collection::stream).toList());

		assertEquals(3, deltaFileRepo.count());
		assertEquals(3, deltaFileFlowRepo.count());
		assertEquals(3, annotationRepo.count());

		deltaFileRepo.batchedBulkDeleteByDidIn(Arrays.asList(deltaFiles.getFirst().getDid(), deltaFiles.getLast().getDid()));

		assertEquals(1, deltaFileRepo.count());
		assertEquals(deltaFiles.get(1).getDid(), deltaFileRepo.findAll().getFirst().getDid());
		assertEquals(1, deltaFileFlowRepo.count());
		assertEquals(1, annotationRepo.count());
	}

	@Test
	void testFindForDeleteCreatedBefore() {
		DeltaFile deltaFile1 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, OffsetDateTime.now().minusSeconds(1), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile2.acknowledgeErrors(OffsetDateTime.now(), "reason");
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.IN_FLIGHT, OffsetDateTime.now().plusSeconds(2), OffsetDateTime.now().plusSeconds(2));
		deltaFileRepo.save(deltaFile3);
		DeltaFile deltaFile4 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile4.acknowledgeErrors(OffsetDateTime.now(), "reason");
		deltaFile4.setContentDeleted(OffsetDateTime.now());
		deltaFileRepo.save(deltaFile4);

		List<DeltaFileDeleteDTO> deltaFiles = deltaFileRepo.findForTimedDelete(OffsetDateTime.now().plusSeconds(1), null, 0, null, false, false, 10, true, false);
		assertEquals(Stream.of(deltaFile1.getDid(), deltaFile2.getDid()).sorted().toList(), deltaFiles.stream().map(DeltaFileDeleteDTO::getDid).sorted().toList());
	}

	@Test
	void testFindForDeletePinned() {
		OffsetDateTime now = OffsetDateTime.now();

		DeltaFile deltaFile1 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, now, now.plusSeconds(1));
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, now.plusSeconds(1), now.plusSeconds(2));
		deltaFile2.setPinned(true);
		deltaFileRepo.save(deltaFile2);

		List<DeltaFileDeleteDTO> deltaFiles = deltaFileRepo.findForTimedDelete(now.plusSeconds(3), null, 0, null, false, false, 10, true, false);
		assertEquals(List.of(deltaFile1.getDid()), deltaFiles.stream().map(DeltaFileDeleteDTO::getDid).toList());
		deltaFiles = deltaFileRepo.findForTimedDelete(now.plusSeconds(3), null, 0, null, false, true, 10, true, false);
		assertEquals(List.of(deltaFile1.getDid(), deltaFile2.getDid()), deltaFiles.stream().map(DeltaFileDeleteDTO::getDid).toList());
	}

	@Test
	void testFindForDeleteCreatedBeforeBatchLimit() {
		DeltaFile deltaFile1 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.IN_FLIGHT, OffsetDateTime.now().plusSeconds(2), OffsetDateTime.now().plusSeconds(2));
		deltaFileRepo.save(deltaFile3);
		DeltaFile deltaFile4 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile4.setContentDeleted(OffsetDateTime.now());
		deltaFileRepo.save(deltaFile4);

		List<DeltaFileDeleteDTO> deltaFiles = deltaFileRepo.findForTimedDelete(OffsetDateTime.now().plusSeconds(1), null, 0, null, false, false, 1, true, false);
		assertEquals(List.of(deltaFile1.getDid()), deltaFiles.stream().map(DeltaFileDeleteDTO::getDid).toList());
	}

	@Test
	void testFindForDeleteCreatedBeforeWithMetadata() {
		DeltaFile deltaFile1 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile1.setContentDeleted(OffsetDateTime.now());
		deltaFileRepo.save(deltaFile1);

		List<DeltaFileDeleteDTO> deltaFiles = deltaFileRepo.findForTimedDelete(OffsetDateTime.now().plusSeconds(1), null, 0, null, true, false, 10, true, false);
		assertEquals(List.of(deltaFile1.getDid()), deltaFiles.stream().map(DeltaFileDeleteDTO::getDid).toList());
	}

	@Test
	void testFindForDeleteCompletedBefore() {
		DeltaFile deltaFile1 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, OffsetDateTime.now().minusSeconds(1), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now().plusSeconds(2));
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile3.firstFlow().addAction("errorAction", null, ActionType.TRANSFORM, ActionState.ERROR, OffsetDateTime.now());
		deltaFile3.updateFlags();
		deltaFileRepo.save(deltaFile3);
		DeltaFile deltaFile4 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile4.firstFlow().addAction("errorAction", null, ActionType.TRANSFORM, ActionState.ERROR, OffsetDateTime.now());
		deltaFile4.acknowledgeErrors(OffsetDateTime.now(), null);
		deltaFile4.updateFlags();
		deltaFileRepo.save(deltaFile4);
		DeltaFile deltaFile5 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile5.firstFlow().setPendingAnnotations(Set.of("a"));
		deltaFile5.updateFlags();
		deltaFileRepo.save(deltaFile5);

		List<DeltaFileDeleteDTO> deltaFiles = deltaFileRepo.findForTimedDelete(null, OffsetDateTime.now().plusSeconds(1), 0, null, false, false, 10, true, false);
		assertEquals(Stream.of(deltaFile1.getDid(), deltaFile4.getDid()).sorted().toList(), deltaFiles.stream().map(DeltaFileDeleteDTO::getDid).sorted().toList());
	}

	@Test
	void testFindForDeleteWithFlow() {
		DeltaFile deltaFile1 = utilService.buildDeltaFile(UUID.randomUUID(), "a", DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = utilService.buildDeltaFile(UUID.randomUUID(), "b", DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile2);

		List<DeltaFileDeleteDTO> deltaFiles = deltaFileRepo.findForTimedDelete(OffsetDateTime.now().plusSeconds(1), null, 0, "a", false, false, 10, true, false);
		assertEquals(List.of(deltaFile1.getDid()), deltaFiles.stream().map(DeltaFileDeleteDTO::getDid).toList());
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testFindForDelete_alreadyMarkedDeleted() {
		OffsetDateTime oneSecondAgo = OffsetDateTime.now().minusSeconds(1);

		DeltaFile deltaFile1 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, oneSecondAgo, oneSecondAgo);
		deltaFile1.setContentDeleted(oneSecondAgo);
		deltaFileRepo.save(deltaFile1);

		List<DeltaFileDeleteDTO> deltaFiles = deltaFileRepo.findForTimedDelete(OffsetDateTime.now(), null, 0, null, false, false, 10, true, false);
		assertTrue(deltaFiles.isEmpty());
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testFindForDeleteDiskSpace() {
		DeltaFile deltaFile1 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now().minusSeconds(5));
		deltaFile1.firstFlow().firstAction().setContent(List.of(new Content("content1", "mediaType1", List.of(new Segment(UUID.randomUUID(), 0, 100L, deltaFile1.getDid())))));
		deltaFile1.setTotalBytes(100L);
		deltaFile1.updateContentObjectIds();
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, OffsetDateTime.now().plusSeconds(2), OffsetDateTime.now());
		deltaFile2.firstFlow().firstAction().setContent(List.of(
				new Content("content2a", "mediaType2a", List.of(new Segment(UUID.randomUUID(), 0, 149L, deltaFile2.getDid()))),
				new Content("content2b", "mediaType2b", List.of(new Segment(UUID.randomUUID(), 0, 151L, deltaFile2.getDid())))

		));
		deltaFile2.setTotalBytes(300L);
		deltaFile2.updateContentObjectIds();
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now().plusSeconds(5));
		deltaFile3.firstFlow().firstAction().setContent(List.of(new Content("content3", "mediaType3", List.of(new Segment(UUID.randomUUID(), 0, 500L, deltaFile3.getDid())))));
		deltaFile3.setTotalBytes(500L);
		deltaFile3.updateContentObjectIds();
		deltaFileRepo.save(deltaFile3);

		List<DeltaFileDeleteDTO> deltaFiles = deltaFileRepo.findForDiskSpaceDelete(250L, 100, true);
		assertEquals(List.of(deltaFile1.getDid(), deltaFile2.getDid()), deltaFiles.stream().map(DeltaFileDeleteDTO::getDid).toList());
		assertEquals(1, deltaFiles.getFirst().getContentObjectIds().size());
		assertEquals(deltaFile1.getContentObjectIds(), deltaFiles.getFirst().getContentObjectIds());
		assertEquals(2, deltaFiles.getLast().getContentObjectIds().size());
		assertEquals(deltaFile2.getContentObjectIds(), deltaFiles.getLast().getContentObjectIds());

		List<DeltaFileDeleteDTO> deltaFilesNoContent = deltaFileRepo.findForDiskSpaceDelete(250L, 100, false);
		assertTrue(deltaFilesNoContent.stream().allMatch(d -> d.getContentObjectIds().isEmpty()));
	}

	@Test
	void testFindForDeleteDiskSpaceAll() {
		DeltaFile deltaFile1 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile1.setTotalBytes(100L);
		deltaFile1.updateFlags();
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, OffsetDateTime.now().plusSeconds(1), OffsetDateTime.now().plusSeconds(2));
		deltaFile2.setTotalBytes(300L);
		deltaFile2.updateFlags();
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, OffsetDateTime.now().plusSeconds(2), OffsetDateTime.now());
		deltaFile3.setTotalBytes(500L);
		deltaFile3.updateFlags();
		deltaFileRepo.save(deltaFile3);
		DeltaFile deltaFile4 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, OffsetDateTime.now().plusSeconds(3), OffsetDateTime.now());
		deltaFile4.setTotalBytes(500L);
		deltaFile4.setContentDeleted(OffsetDateTime.now());
		deltaFile4.updateFlags();
		deltaFileRepo.save(deltaFile4);
		DeltaFile deltaFile5 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, OffsetDateTime.now().plusSeconds(4), OffsetDateTime.now());
		deltaFile5.setTotalBytes(0L);
		deltaFile5.updateFlags();
		deltaFileRepo.save(deltaFile5);
		DeltaFile deltaFile6 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.IN_FLIGHT, OffsetDateTime.now().plusSeconds(5), OffsetDateTime.now());
		deltaFile6.setTotalBytes(50L);
		deltaFile6.updateFlags();
		deltaFileRepo.save(deltaFile6);
		DeltaFile deltaFile7 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, OffsetDateTime.now().plusSeconds(6), OffsetDateTime.now());
		deltaFile7.setTotalBytes(1000L);
		deltaFile7.firstFlow().setPendingAnnotations(Set.of("a"));
		deltaFile7.updateFlags();
		deltaFileRepo.save(deltaFile7);

		List<DeltaFileDeleteDTO> deltaFiles = deltaFileRepo.findForDiskSpaceDelete(2500L,  100, true);
		assertEquals(Stream.of(deltaFile1.getDid(), deltaFile2.getDid(), deltaFile3.getDid()).sorted().toList(), deltaFiles.stream().map(DeltaFileDeleteDTO::getDid).sorted().toList());
	}

	@Test
	void testFindForDeleteDiskSpaceBatchSizeLimited() {
		DeltaFile deltaFile1 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile1.setTotalBytes(100L);
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, OffsetDateTime.now().plusSeconds(2), OffsetDateTime.now().plusSeconds(1));
		deltaFile2.setTotalBytes(300L);
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now().plusSeconds(2));
		deltaFile3.setTotalBytes(500L);
		deltaFileRepo.save(deltaFile3);

		List<DeltaFileDeleteDTO> deltaFiles = deltaFileRepo.findForDiskSpaceDelete(2500L, 2, true);
		assertEquals(List.of(deltaFile1.getDid(), deltaFile2.getDid()), deltaFiles.stream().map(DeltaFileDeleteDTO::getDid).toList());
	}

	@Test
	void testDeltaFiles_all() {
		DeltaFile deltaFile1 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, NOW.minusSeconds(2), NOW.minusSeconds(2));
		DeltaFile deltaFile2 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, NOW.plusSeconds(2), NOW.plusSeconds(2));
		deltaFileRepo.insertBatch(List.of(deltaFile1, deltaFile2), 1000);

		DeltaFiles deltaFiles = deltaFileRepo.deltaFiles(null, 50, new DeltaFilesFilter(), null, null);
		assertEquals(deltaFiles.getDeltaFiles(), List.of(deltaFile2, deltaFile1));
	}

	@Test
	void testDeltaFiles_limit() {
		DeltaFile deltaFile1 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, NOW.minusSeconds(2), NOW.minusSeconds(2));
		DeltaFile deltaFile2 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, NOW.plusSeconds(2), NOW.plusSeconds(2));
		deltaFileRepo.insertBatch(List.of(deltaFile1, deltaFile2), 1000);

		DeltaFiles deltaFiles = deltaFileRepo.deltaFiles(null, 1, DeltaFilesFilter.newBuilder()
				.modifiedAfter(NOW.minusYears(1))
				.modifiedBefore(NOW.plusYears(1))
				.build(), null, null);
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
		DeltaFile deltaFile1 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, NOW.minusSeconds(2), NOW.plusSeconds(2));
		DeltaFile deltaFile2 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, NOW.plusSeconds(2), NOW.minusSeconds(2));
		deltaFileRepo.insertBatch(List.of(deltaFile1, deltaFile2), 1000);

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
		DeltaFile deltaFile1 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, NOW.minusSeconds(2), NOW.plusSeconds(2));
		DeltaFile deltaFile2 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.ERROR, NOW.plusSeconds(2), NOW.minusSeconds(2));
		deltaFileRepo.insertBatch(List.of(deltaFile1, deltaFile2), 1000);

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
		DeltaFile deltaFile1 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, NOW.minusSeconds(2), NOW.plusSeconds(2));
		deltaFile1.setPinned(true);
		deltaFile1.setIngressBytes(100L);
		deltaFile1.setTotalBytes(1000L);
		deltaFile1.addAnnotations(Map.of("a.1", "first", "common", "abcdef"));
		deltaFile1.setContentDeleted(NOW);
		deltaFile1.firstFlow().firstAction().setMetadata(Map.of("key1", "value1", "key2", "value2"));
		deltaFile1.setName("filename1");
		deltaFile1.setDataSource("flow1");
		DeltaFileFlow flow1 = deltaFile1.addFlow(flowDefinitionService.getOrCreateFlow("MyDataSink", FlowType.DATA_SINK), deltaFile1.firstFlow(), NOW);
		flow1.setErrorAcknowledged(NOW);
		flow1.setActions(List.of(Action.builder().name("action1")
				.state(ActionState.ERROR)
				.content(List.of(new Content("formattedFilename1", "mediaType")))
				.metadata(Map.of("formattedKey1", "formattedValue1", "formattedKey2", "formattedValue2"))
				.build()));
		flow1.setPublishTopics(List.of("topic1"));
		flow1.updateState();
		flow1.setTestModeReason("TestModeReason");
		flow1.setTestMode(true);
		deltaFile1.incrementRequeueCount();
		deltaFile1.updateFlowArrays();
		deltaFile1.setWarnings(true);

		DeltaFile deltaFile2 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.ERROR, NOW.plusSeconds(2), NOW.minusSeconds(2));
		deltaFile2.setIngressBytes(200L);
		deltaFile2.setTotalBytes(2000L);
		deltaFile2.addAnnotations(Map.of("a.2", "first", "common", "abcdef"));
		deltaFile2.setName("filename2");
		deltaFile2.setDataSource("flow2");
		DeltaFileFlow flow2 = deltaFile2.addFlow(flowDefinitionService.getOrCreateFlow("MyDataSink", FlowType.DATA_SINK), deltaFile2.firstFlow(), NOW);
		DeltaFileFlow flow2b = deltaFile2.addFlow(flowDefinitionService.getOrCreateFlow("MyDataSink2", FlowType.DATA_SINK), deltaFile2.firstFlow(), NOW);
		flow2.setActions(List.of(
				Action.builder().name("action1")
						.state(ActionState.ERROR)
						.errorCause("Cause")
						.build()));
		flow2.setPublishTopics(List.of("topic2", "topic3"));
		flow2.updateState();
		flow2b.setActions(List.of(Action.builder().name("action2")
						.state(ActionState.COMPLETE)
						.content(List.of(new Content("formattedFilename2", "mediaType")))
						.build()));
		flow2b.updateState();
		deltaFile2.setEgressed(true);
		deltaFile2.setFiltered(true);
		deltaFile2.updateFlowArrays();
		deltaFile2.setUserNotes(true);

		DeltaFile deltaFile3 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, NOW.plusSeconds(3), NOW.minusSeconds(3));
		deltaFile3.setIngressBytes(300L);
		deltaFile3.setTotalBytes(3000L);
		deltaFile3.addAnnotations(Map.of("b.2", "first", "common", "abc123"));
		deltaFile3.setName("filename3");
		deltaFile3.setDataSource("flow3");
		DeltaFileFlow flow3 = deltaFile3.addFlow(flowDefinitionService.getOrCreateFlow("MyTransformFlow", FlowType.TRANSFORM), deltaFile3.firstFlow(), NOW);
		DeltaFileFlow flow3b = deltaFile3.addFlow(flowDefinitionService.getOrCreateFlow("MyDataSink3", FlowType.DATA_SINK), deltaFile3.firstFlow(), NOW);
		flow3.setActions(List.of(
				Action.builder()
						.name("action2")
						.state(ActionState.FILTERED)
						.filteredCause("Coffee")
						.filteredContext("and donuts")
						.build()));
		flow3.updateState();
		flow3b.setActions(List.of(Action.builder()
						.name("action2")
						.state(ActionState.COMPLETE)
						.content(List.of(new Content("formattedFilename3", "mediaType")))
						.build()));
		flow3b.updateState();
		deltaFile3.setEgressed(true);
		deltaFile3.setFiltered(true);
		deltaFile3.updateFlowArrays();
		deltaFileRepo.insertBatch(List.of(deltaFile1, deltaFile2, deltaFile3), 1000);

		testFilter(DeltaFilesFilter.newBuilder().testMode(true).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().testMode(false).build(), deltaFile2, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().createdAfter(NOW).build(), deltaFile2, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().createdBefore(NOW).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().contentDeleted(true).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().contentDeleted(false).build(), deltaFile2, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().modifiedAfter(NOW).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().modifiedBefore(NOW).build(), deltaFile2, deltaFile3);
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
		testFilter(DeltaFilesFilter.newBuilder().errorCause("Cause").build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().filtered(true).filteredCause("Coffee").build(), deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().filteredCause("off").build(), deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().filteredCause("nope").build());
		testFilter(DeltaFilesFilter.newBuilder().dids(Collections.emptyList()).build(), deltaFile1, deltaFile2, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().dids(Collections.singletonList(deltaFile1.getDid())).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().dids(List.of(deltaFile1.getDid(), deltaFile3.getDid())).build(), deltaFile1, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().dids(List.of(deltaFile1.getDid(), deltaFile2.getDid())).build(), deltaFile1, deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().dids(List.of(UUID.randomUUID(), UUID.randomUUID())).build());
		testFilter(DeltaFilesFilter.newBuilder().errorAcknowledged(true).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().errorAcknowledged(false).build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().egressed(false).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().egressed(true).build(), deltaFile2, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().filtered(false).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().filtered(true).build(), deltaFile2, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().annotations(List.of(new KeyValue("common", "abcdef"))).build(), deltaFile1, deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().annotations(List.of(new KeyValue("common", "abc*"))).build(), deltaFile1, deltaFile2, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().annotations(List.of(new KeyValue("common", "a*3"))).build(), deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().annotations(List.of(new KeyValue("common", "!a*3"))).build(), deltaFile1, deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().annotations(List.of(new KeyValue("common", "!abcdef"))).build(), deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().annotations(List.of(new KeyValue("common", "*"))).build(), deltaFile1, deltaFile2, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().annotations(List.of(new KeyValue("a.1", "first"))).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().annotations(List.of(new KeyValue("a.1", "first"), new KeyValue("common", "abcdef"))).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().annotations(List.of(new KeyValue("a.1", "first"), new KeyValue("common", "abc*"))).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().annotations(List.of(new KeyValue("common", "abc*"), new KeyValue("a.1", "first"))).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().annotations(List.of(new KeyValue("common", "*"), new KeyValue("a.1", "first"))).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().annotations(List.of(new KeyValue("a.1", "first"), new KeyValue("common", "abcdef"), new KeyValue("extra", "missing"))).build());
		testFilter(DeltaFilesFilter.newBuilder().annotations(List.of(new KeyValue("a.1", "first"), new KeyValue("common", "miss"))).build());
		testFilter(DeltaFilesFilter.newBuilder().dataSinks(List.of("MyDataSinkz")).build());
		testFilter(DeltaFilesFilter.newBuilder().dataSinks(List.of("MyDataSink")).build(), deltaFile1, deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().dataSinks(List.of("MyDataSink2")).build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().dataSinks(List.of("MyDataSink", "MyDataSink2")).build(), deltaFile1, deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().dataSinks(List.of("MyDataSink", "MyDataSink3")).build(), deltaFile1, deltaFile2, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().transforms(List.of("MyTransformFlow", "MyDataSink")).build(), deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().transforms(List.of("MyDataSink")).build());
		testFilter(DeltaFilesFilter.newBuilder().dataSources(List.of("flow1", "flow3")).build(), deltaFile1, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().topics(List.of("topic1")).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().topics(List.of("topic2")).build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().topics(List.of("topic1", "topic2")).build(), deltaFile1, deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().topics(List.of("fake")).build());
		testFilter(DeltaFilesFilter.newBuilder().pinned(true).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().pinned(false).build(), deltaFile2, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().warnings(true).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().warnings(false).build(), deltaFile2, deltaFile3);
		testFilter(DeltaFilesFilter.newBuilder().userNotes(true).build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().userNotes(false).build(), deltaFile1, deltaFile3);
	}

	@Test
	void testErrorAndFilterCriteria() {
		String reason = "reason";
		OffsetDateTime time = OffsetDateTime.now(clock);
		DeltaFile acknowledgedError = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, time, time);
		acknowledgedError.setName("acknowledgedError");
		acknowledgedError.firstFlow().setErrorAcknowledged(time);
		acknowledgedError.firstFlow().addAction("ErrorAction", null, ActionType.TRANSFORM, ERROR, time).setErrorCause(reason);
		acknowledgedError.firstFlow().updateState();
		acknowledgedError.updateFlowArrays();

		time = time.minusMinutes(1);
		DeltaFile unacknowledgedError = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, time, time);
		unacknowledgedError.setName("unacknowledgedError");
		Action unacknowledgedAction = unacknowledgedError.firstFlow().addAction("ErrorAction", null, ActionType.TRANSFORM, ERROR, time);
		unacknowledgedAction.setErrorCause(reason);
		unacknowledgedError.firstFlow().updateState();
		unacknowledgedError.updateFlowArrays();

		time = time.minusMinutes(2);
		DeltaFile filtered = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, time, time);
		filtered.setName("filtered");
		Action filteredAction = filtered.firstFlow().addAction("FilteredAction", null, ActionType.TRANSFORM, FILTERED, time);
		filteredAction.setFilteredCause(reason);
		filtered.firstFlow().updateState();
		filtered.updateFlowArrays();

		time = time.minusMinutes(3);
		DeltaFile filteredAndErrored = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, time, time);
		filteredAndErrored.setName("filteredAndErrored");
		Action filteredAction2 = filteredAndErrored.firstFlow().addAction("FilteredAction", null, ActionType.TRANSFORM, FILTERED, time);
		filteredAction2.setFilteredCause(reason);
		filteredAndErrored.firstFlow().updateState();
		filteredAndErrored.addFlow(flowDefinitionService.getOrCreateFlow("flow2", FlowType.TRANSFORM), filteredAndErrored.firstFlow(), NOW);
		Action unacknowledgedAction2 = filteredAndErrored.lastFlow().addAction("ErrorAction", null, ActionType.TRANSFORM, ERROR, time);
		unacknowledgedAction2.setErrorCause(reason);
		filteredAndErrored.lastFlow().updateState();
		filteredAndErrored.updateFlowArrays();

		deltaFileRepo.insertBatch(List.of(unacknowledgedError, acknowledgedError, filtered, filteredAndErrored), 1000);

		testFilter(DeltaFilesFilter.newBuilder().errorCause(reason).build(), acknowledgedError, unacknowledgedError, filteredAndErrored);
		testFilter(DeltaFilesFilter.newBuilder().errorCause(reason).errorAcknowledged(true).build(), acknowledgedError);
		testFilter(DeltaFilesFilter.newBuilder().errorCause(reason).errorAcknowledged(false).build(), unacknowledgedError, filteredAndErrored);
		testFilter(DeltaFilesFilter.newBuilder().filteredCause(reason).build(), filtered, filteredAndErrored);
		testFilter(DeltaFilesFilter.newBuilder().errorCause(reason).filteredCause(reason).build(), filteredAndErrored);
	}

	@Test
	void testQueryByFilterMessage() {
		// Not filtered
		DeltaFile deltaFile1 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, NOW, NOW);

		deltaFile1.firstFlow().setActions(List.of(Action.builder().name("action1").state(COMPLETE).build()));
		deltaFile1.firstFlow().updateState();
		// Not filtered, with errorCause
		DeltaFile deltaFile2 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.ERROR, NOW.plusSeconds(1), NOW.plusSeconds(1));
		deltaFile2.firstFlow().setActions(List.of(Action.builder().name("action1").state(ERROR).errorCause("Error reason 1").build()));
		deltaFile2.firstFlow().updateState();
		// filtered with errorCause
		DeltaFile deltaFile3 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, NOW.plusSeconds(2), NOW.plusSeconds(2));
		deltaFile3.firstFlow().setActions(List.of(Action.builder().name("action1").state(FILTERED).errorCause("Filtered reason 1").build()));
		deltaFile3.firstFlow().updateState();
		deltaFile3.setFiltered(true);
		// errored with filteredCause
		DeltaFile deltaFile4 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, NOW.plusSeconds(3), NOW.plusSeconds(3));
		deltaFile4.firstFlow().setActions(List.of(Action.builder().name("action1").state(ERROR).filteredCause("Filtered reason 2").build()));
		deltaFile4.firstFlow().updateState();
		deltaFile4.setFiltered(true);
		// Filtered
		DeltaFile deltaFile5 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, NOW.plusSeconds(3), NOW.plusSeconds(3));
		deltaFile5.firstFlow().setActions(List.of(Action.builder().name("action1").state(FILTERED).filteredCause("Filtered reason 2").build()));
		deltaFile5.firstFlow().updateState();
		deltaFile5.setFiltered(true);
		deltaFileRepo.insertBatch(List.of(deltaFile1, deltaFile2, deltaFile3, deltaFile4, deltaFile5), 1000);

		testFilter(DeltaFilesFilter.newBuilder().errorCause("reason").build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().filteredCause("reason").build(), deltaFile5);
	}

	@Test
	void testQueryByCanReplay() {
		DeltaFile noContent = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, NOW, NOW);
		noContent.setContentDeleted(NOW);
		noContent.setEgressed(true);
		DeltaFile hasReplayDate = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, NOW, NOW.plusSeconds(1));
		hasReplayDate.setReplayed(NOW);
		DeltaFile replayable = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, NOW, NOW.plusSeconds(2));

		deltaFileRepo.insertBatch(List.of(noContent, hasReplayDate, replayable), 1000);

		testFilter(DeltaFilesFilter.newBuilder().replayable(true).build(), replayable);
		testFilter(DeltaFilesFilter.newBuilder().replayable(false).build(), hasReplayDate, noContent);

		// make sure the content or replay criteria is properly nested within the outer and criteria
		testFilter(DeltaFilesFilter.newBuilder().replayable(false).egressed(true).build(), noContent);
	}

	@Test
	void testQueryByIsReplayed() {
		DeltaFile hasReplayDate = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, NOW, NOW);
		hasReplayDate.setReplayed(NOW);
		DeltaFile noReplayDate = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, NOW, NOW);

		deltaFileRepo.insertBatch(List.of(hasReplayDate, noReplayDate), 1000);

		testFilter(DeltaFilesFilter.newBuilder().replayed(true).build(), hasReplayDate);
		testFilter(DeltaFilesFilter.newBuilder().replayed(false).build(), noReplayDate);
	}

	@Test
	void testFilterByTerminalStage() {
		DeltaFile ingress = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.IN_FLIGHT, NOW.plusSeconds(2), NOW.minusSeconds(4));
		DeltaFile enrich = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.IN_FLIGHT, NOW.plusSeconds(2), NOW.minusSeconds(3));
		DeltaFile egress = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.IN_FLIGHT, NOW.plusSeconds(2), NOW.minusSeconds(2));
		DeltaFile complete = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, NOW.plusSeconds(2), NOW.minusSeconds(4));
		DeltaFile error = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.ERROR, NOW.plusSeconds(2), NOW.minusSeconds(3));
		error.acknowledgeErrors(NOW, "acked");
		DeltaFile cancelled = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.CANCELLED, NOW.plusSeconds(2), NOW.plusSeconds(2));
		deltaFileRepo.insertBatch(List.of(ingress, enrich, egress, complete, error, cancelled), 1000);
		testFilter(DeltaFilesFilter.newBuilder().terminalStage(true).build(), cancelled, error, complete);
		testFilter(DeltaFilesFilter.newBuilder().terminalStage(false).build(), egress, enrich, ingress);
		testFilter(DeltaFilesFilter.newBuilder().stage(DeltaFileStage.CANCELLED).terminalStage(false).build());
		testFilter(DeltaFilesFilter.newBuilder().stage(DeltaFileStage.IN_FLIGHT).terminalStage(true).build());
	}

	@Test
	void testFilterByPendingAnnotations() {
		DeltaFile pending = utilService.buildDeltaFile(UUID.randomUUID(), "a", DeltaFileStage.COMPLETE, NOW, NOW);
		pending.firstFlow().setPendingAnnotations(Set.of("a"));
		DeltaFile notPending = utilService.buildDeltaFile(UUID.randomUUID(), "a", DeltaFileStage.COMPLETE, NOW, NOW);

		deltaFileRepo.insertBatch(List.of(pending, notPending), 1000);
		testFilter(DeltaFilesFilter.newBuilder().pendingAnnotations(true).build(), pending);
		testFilter(DeltaFilesFilter.newBuilder().pendingAnnotations(false).build(), notPending);
	}

	@Test
	void testFilterByPaused() {
		DeltaFile paused = utilService.buildDeltaFile(UUID.randomUUID(), "a", DeltaFileStage.IN_FLIGHT, NOW, NOW);
		paused.firstFlow().setState(DeltaFileFlowState.PAUSED);
		paused.updateFlags();
		assertTrue(paused.getPaused());
		DeltaFile notPaused = utilService.buildDeltaFile(UUID.randomUUID(), "a", DeltaFileStage.IN_FLIGHT, NOW, NOW);
		assertFalse(notPaused.getPaused());

		deltaFileRepo.insertBatch(List.of(paused, notPaused), 1000);
		testFilter(DeltaFilesFilter.newBuilder().paused(true).build(), paused);
		testFilter(DeltaFilesFilter.newBuilder().paused(false).build(), notPaused);
	}

	@Test
	void testFilterByName() {
		DeltaFile deltaFile = new DeltaFile();
		deltaFile.setModified(NOW.plusSeconds(1));
		deltaFile.setName("filename");

		DeltaFile deltaFile1 = new DeltaFile();
		deltaFile1.setModified(NOW);
		deltaFile1.setName("file");

		deltaFileRepo.saveAll(List.of(deltaFile1, deltaFile));

		testFilter(nameFilter("iLe", true));
		testFilter(nameFilter("iLe", false), deltaFile, deltaFile1);
		testFilter(nameFilter("ile", true), deltaFile, deltaFile1);
		testFilter(nameFilter("ilen", true), deltaFile);
	}

	private DeltaFilesFilter nameFilter(String filename, boolean caseSensitive) {
		NameFilter nameFilter = NameFilter.newBuilder().name(filename).caseSensitive(caseSensitive).build();
		return DeltaFilesFilter.newBuilder().nameFilter(nameFilter).build();
	}

	private void testFilter(DeltaFilesFilter filter, DeltaFile... expected) {
		DeltaFiles deltaFiles = deltaFileRepo.deltaFiles(null, 50, filter, null, null);
		assertEquals(new ArrayList<>(Arrays.asList(expected)), deltaFiles.getDeltaFiles());

		// use contains to ignore the order since this query does not include an order by clause
		assertThat(deltaFileRepo.deltaFiles(filter, 50)).contains(expected);
	}

	@Test
	void testAnnotationKeys() {
		assertThat(deltaFilesService.annotationKeys()).isEmpty();

		DeltaFile nullKeys = new DeltaFile();
		nullKeys.setAnnotations(null);
		deltaFileRepo.save(nullKeys);

		assertThat(deltaFilesService.annotationKeys()).isEmpty();

		DeltaFile emptyKeys = new DeltaFile();
		emptyKeys.setAnnotations(Set.of());
		deltaFileRepo.save(emptyKeys);

		assertThat(deltaFilesService.annotationKeys()).isEmpty();

		DeltaFile withKeys = new DeltaFile();
		withKeys.addAnnotations(Map.of("a", "x", "b", "y"));

		DeltaFile otherDomain = new DeltaFile();
		otherDomain.addAnnotations(Map.of("b", "x", "c", "y", "d", "z"));
		deltaFileRepo.saveAll(List.of(withKeys, otherDomain));

		assertThat(deltaFilesService.annotationKeys()).hasSize(4).contains("a", "b", "c", "d");
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
		assertThat(transformFlowRepo.deleteBySourcePluginAndType(pluginToDelete, FlowType.TRANSFORM)).isEqualTo(2);
		assertThat(transformFlowRepo.count()).isEqualTo(1);
	}

	@Test
	void deleteDataSinkByPlugin() {
		clearForFlowTests();
		PluginCoordinates pluginToDelete = PluginCoordinates.builder().groupId("group").artifactId("deltafi-actions").version("1.0.0").build();

		DataSink dataSinkA = new DataSink();
		dataSinkA.setName("a");
		dataSinkA.setSourcePlugin(pluginToDelete);

		DataSink dataSinkB = new DataSink();
		dataSinkB.setName("b");
		dataSinkB.setSourcePlugin(pluginToDelete);

		DataSink dataSinkC = new DataSink();
		dataSinkC.setName("c");
		dataSinkC.setSourcePlugin(PluginCoordinates.builder().groupId("group2").artifactId("deltafi-actions").version("1.0.0").build());
		dataSinkRepo.saveAll(List.of(dataSinkA, dataSinkB, dataSinkC));
		assertThat(dataSinkRepo.deleteBySourcePluginAndType(pluginToDelete, FlowType.DATA_SINK)).isEqualTo(2);
		assertThat(dataSinkRepo.count()).isEqualTo(1);
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

		List<Flow> found = transformFlowRepo.findBySourcePluginGroupIdAndSourcePluginArtifactIdAndType("group", "deltafi-actions", FlowType.TRANSFORM);
		assertThat(found).hasSize(3).contains(transformFlowA, transformFlowB, transformFlowC);
	}

	@Test
	void testDeltaFilesEndpoint() {
		DeltaFile deltaFile1 = utilService.buildDeltaFile(UUID.randomUUID(), "flow1", DeltaFileStage.COMPLETE, NOW.minusSeconds(2), NOW.minusSeconds(2));
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = utilService.buildDeltaFile(UUID.randomUUID(), "flow2", DeltaFileStage.COMPLETE, NOW.plusSeconds(2), NOW.plusSeconds(2));
		deltaFileRepo.save(deltaFile2);

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
				DeltaFilesGraphQLQuery.newRequest()
						.filter(new DeltaFilesFilter())
						.limit(50)
						.offset(null)
						.orderBy(null)
						.build(),
				new DeltaFilesProjectionRoot<>().count().totalCount().offset().deltaFiles().did()
						.flows().id().publishTopics().name().type().parent().state().getParent().created().modified()
						.actions().name().type().parent().state().parent().created().modified().attempt());

		Object response = dgsQueryExecutor.executeAndExtractJsonPath(
				graphQLQueryRequest.serialize(),
				"data." + DgsConstants.QUERY.DeltaFiles
		);

		DeltaFiles deltaFiles = flowMapper().convertValue(response, DeltaFiles.class);

		assertEquals(2, deltaFiles.getCount());
		assertEquals(2, deltaFiles.getTotalCount());
		assertEquals(0, deltaFiles.getOffset());
		assertEquals(deltaFile2.getDid(), deltaFiles.getDeltaFiles().getFirst().getDid());
		assertEquals(deltaFile2.firstFlow(), deltaFiles.getDeltaFiles().getFirst().firstFlow());
		assertEquals(deltaFile1.getDid(), deltaFiles.getDeltaFiles().get(1).getDid());
		assertEquals(deltaFile1.firstFlow().getName(), deltaFiles.getDeltaFiles().get(1).firstFlow().getName());
	}

	@Test
	void testFindVariablesIgnoringVersion() {
		PluginCoordinates oldVersion = PluginCoordinates.builder().groupId("org").artifactId("deltafi").version("1").build();
		PluginCoordinates newVersion = PluginCoordinates.builder().groupId("org").artifactId("deltafi").version("2").build();
		PluginVariables variables = new PluginVariables();
		variables.setSourcePlugin(oldVersion);

		pluginVariableRepo.save(variables);

		assertThat(pluginVariableRepo.findBySourcePlugin(newVersion)).isEmpty();
		assertThat(pluginVariableRepo.findIgnoringVersion(newVersion.getGroupId(), newVersion.getArtifactId())).hasSize(1).contains(variables);
	}

	@Test
	void testResetAllUnmaskedVariableValues() {
		PluginVariables variables = new PluginVariables();
		PluginCoordinates coords = PluginCoordinates.builder().groupId("org").artifactId("reset-test").version("1").build();
		variables.setSourcePlugin(coords);

		Variable notSet = UtilService.buildVariable("notSet", null, "default");
		Variable notSetAndMasked = UtilService.buildVariable("notSetAndMasked", null, "default");
		notSetAndMasked.setMasked(true);
		Variable setValue = UtilService.buildVariable("setValue", "value", "default");
		Variable setValueAndMasked = UtilService.buildVariable("setValueAndMasked", "value", "default");
		setValueAndMasked.setMasked(true);

		variables.setVariables(List.of(notSet, notSetAndMasked, setValue, setValueAndMasked));

		pluginVariableRepo.save(variables);

		pluginVariableRepo.resetAllUnmaskedVariableValues();

		Map<String, Variable> updatedVars = pluginVariableRepo.findBySourcePlugin(coords).orElseThrow()
				.getVariables().stream().collect(Collectors.toMap(Variable::getName, Function.identity()));

		assertThat(updatedVars)
				.containsEntry("notSet", notSet)
				.containsEntry("notSetAndMasked", notSetAndMasked)
				.containsEntry("setValueAndMasked", setValueAndMasked);

		Variable updatedSetValue = updatedVars.get("setValue");
		assertThat(updatedSetValue).isNotEqualTo(setValue);
		assertThat(updatedSetValue.getValue()).isNull();
	}

	static final PluginCoordinates OLD_VERSION = PluginCoordinates.builder().groupId("org").artifactId("deltafi").version("1").build();
	static final PluginCoordinates NEW_VERSION = PluginCoordinates.builder().groupId("org").artifactId("deltafi").version("2").build();

	@Test
	void testConcurrentPluginVariableRegistration() {
		IntStream.range(0, 10).forEach(ignore -> {
			PluginVariables pluginVariables = new PluginVariables();
			List<Variable> variableList = Stream.of("var1", "var2", "var3", "var4").map(UtilService::buildOriginalVariable).toList();
			pluginVariables.setVariables(variableList);
			pluginVariables.setSourcePlugin(OLD_VERSION);
			testConcurrentPluginVariableRegistration(pluginVariables);
		});
	}

	void testConcurrentPluginVariableRegistration(PluginVariables pluginVariables) {
		pluginVariableRepo.truncate();

		// Save the original set of variables with values set
		pluginVariableRepo.save(pluginVariables);

		// new set of variables that need to get the set value added in
		List<Variable> newVariables = Stream.of("var2", "var3", "var4", "var5").map(UtilService::buildNewVariable).toList();

		final int numMockPlugins = 15;
		Executor mockRegistryExecutor = Executors.newFixedThreadPool(3);
		List<CompletableFuture<Void>> futures = IntStream.range(0, numMockPlugins)
				.mapToObj(i -> submitNewVariables(mockRegistryExecutor, newVariables))
				.toList();

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[numMockPlugins])).join();

		PluginVariables afterRegistrations = pluginVariableRepo.findBySourcePlugin(NEW_VERSION).orElse(null);
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

	private CompletableFuture<Void> submitNewVariables(Executor executor, List<Variable> variables) {
		return CompletableFuture.runAsync(() -> pluginVariableService.saveVariables(NEW_VERSION, variables), executor);
	}

	@Test
	void testEndpointsForTestResults() {
		testResultRepo.deleteAll();
		integrationTestRepo.deleteAll();

		OffsetDateTime start = OffsetDateTime.of(2024, 1, 31, 12, 0, 30, 0, ZoneOffset.UTC);
		OffsetDateTime stop = OffsetDateTime.of(2024, 1, 31, 12, 1, 30, 0, ZoneOffset.UTC);

		TestResult testResult1 = new TestResult("1", "tn1", TestStatus.SUCCESSFUL, start, stop, Collections.emptyList());
		TestResult testResult2 = new TestResult("2", "tn2", TestStatus.FAILED, start, stop, List.of("errors"));
		testResultRepo.saveAll(List.of(testResult1, testResult2));

		List<TestResult> listResult = IntegrationDataFetcherTestHelper.getAllTestResults(dgsQueryExecutor);
		assertEquals(2, listResult.size());
		assertTrue(listResult.containsAll(List.of(testResult1, testResult2)));

		TestResult getResult = IntegrationDataFetcherTestHelper.getTestResult(dgsQueryExecutor, "1");
		assertEquals(testResult1, getResult);

		assertFalse(IntegrationDataFetcherTestHelper.removeTestResult(dgsQueryExecutor, "3"));
		assertTrue(IntegrationDataFetcherTestHelper.removeTestResult(dgsQueryExecutor, "1"));

		List<TestResult> anotherListResult = IntegrationDataFetcherTestHelper.getAllTestResults(dgsQueryExecutor);
		assertEquals(1, anotherListResult.size());
		assertTrue(anotherListResult.contains(testResult2));
	}

	IntegrationTest readYamlTestFile(String file) throws IOException {
		byte[] bytes = new ClassPathResource("/integration/" + file).getInputStream().readAllBytes();
		return YAML_MAPPER.readValue(bytes, IntegrationTest.class);
	}

	@Test
	void testIntegrationTestSerialization() throws IOException {
		testResultRepo.deleteAll();
		integrationTestRepo.deleteAll();

		TestCaseIngress ingress1 = TestCaseIngress.builder()
				.flow("ds")
				.ingressFileName("fn")
				.base64Encoded(false)
				.data("data")
				.build();
		PluginCoordinates pc = new PluginCoordinates("group", "art", "ANY");

		ExpectedDeltaFile e1 = ExpectedDeltaFile.builder()
				.stage(DeltaFileStage.COMPLETE)
				.childCount(3)
				.build();

		ExpectedDeltaFile e2 = ExpectedDeltaFile.builder()
				.stage(DeltaFileStage.ERROR)
				.childCount(0)
				.build();

		// Missing plugin and expectedDeltaFile
		IntegrationTest testCaseSimple = IntegrationTest.builder()
				.name("test4")
				.description("desc")
				.inputs(List.of(ingress1))
				.build();

		Result result = IntegrationDataFetcherTestHelper.saveIntegrationTest(
				dgsQueryExecutor, testCaseSimple);

		assertFalse(result.isSuccess());
		assertFalse(result.getErrors().isEmpty());

		// N0 ExpectedDeltaFiles
		IntegrationTest testCaseWithoutExpectedDFs = IntegrationTest.builder()
				.name("test4")
				.description("desc")
				.inputs(List.of(ingress1))
				.plugins(List.of(pc))
				.dataSources(List.of("ds"))
				.transformationFlows(List.of("t"))
				.dataSinks(List.of("e"))
				.timeout("PT3M")
				.build();

		result = IntegrationDataFetcherTestHelper.saveIntegrationTest(
				dgsQueryExecutor, testCaseWithoutExpectedDFs);

		assertFalse(result.isSuccess());
		assertFalse(result.getErrors().isEmpty());

		// FULL
		IntegrationTest testCaseFull = IntegrationTest.builder()
				.name("test4")
				.description("desc")
				.inputs(List.of(ingress1))
				.plugins(List.of(pc))
				.dataSources(List.of("ds"))
				.transformationFlows(List.of("t"))
				.dataSinks(List.of("e"))
				.timeout("PT3M")
				.expectedDeltaFiles(List.of(e1, e2))
				.build();

		IntegrationTest testCaseFromYaml = readYamlTestFile("/conversion-test.yaml");
		assertEquals(testCaseFull, testCaseFromYaml);

		result = IntegrationDataFetcherTestHelper.saveIntegrationTest(
				dgsQueryExecutor, testCaseFull);

		assertTrue(result.isSuccess());
		assertTrue(result.getErrors().isEmpty());

		// YAML as String
		result = IntegrationDataFetcherTestHelper.loadIntegrationTest(
				dgsQueryExecutor, Resource.read("/integration/conversion-test.yaml"));

		assertTrue(result.isSuccess());
		assertTrue(result.getErrors().isEmpty());
	}

	@Test
	void testLaunchIntegrationTest() throws IOException {
		testResultRepo.deleteAll();
		integrationTestRepo.deleteAll();

		IntegrationTest testCase = readYamlTestFile("/config-binary.yaml");

		Result result = IntegrationDataFetcherTestHelper.saveIntegrationTest(
				dgsQueryExecutor, testCase);

		assertTrue(result.isSuccess());
		assertTrue(result.getErrors().isEmpty());

		List<String> startErrors = List.of(
				"Could not validate config",
				"Plugin not found: org.deltafi:deltafi-core-actions:*",
				"Plugin not found: org.deltafi.testjig:deltafi-testjig:*",
				"Flow does not exist (dataSource): unarchive-passthrough-rest-data-source",
				"Flow does not exist (transformation): unarchive-passthrough-transform",
				"Flow does not exist (transformation): passthrough-transform",
				"Flow does not exist (dataSink): passthrough-egress"
		);

		TestResult testResult = IntegrationDataFetcherTestHelper.startIntegrationTest(dgsQueryExecutor, "plugin1.test1");

		assertEquals(TestStatus.INVALID, testResult.getStatus());
		assertEquals(startErrors, testResult.getErrors());

		List<TestResult> listResult = IntegrationDataFetcherTestHelper.getAllTestResults(dgsQueryExecutor);
		assertEquals(0, listResult.size());
	}

	@Test
	void testEndpointsForIntegrationTests() throws IOException {
		String testName1 = "plugin1.test1";
		String testName2 = "test2";

		testResultRepo.deleteAll();
		integrationTestRepo.deleteAll();

		IntegrationTestEntity test1Entity = IntegrationTestEntity.builder().name(testName1).description("d1").build();
		IntegrationTestEntity test2Entity = IntegrationTestEntity.builder().name(testName2).description("d2").build();

		integrationTestRepo.saveAll(List.of(test1Entity, test2Entity));

		IntegrationTest test1 = test1Entity.toIntegrationTest();
		IntegrationTest test2 = test2Entity.toIntegrationTest();

		List<IntegrationTest> listResult = IntegrationDataFetcherTestHelper.getAllIntegrationTests(dgsQueryExecutor);
		assertEquals(2, listResult.size());
		assertTrue(listResult.containsAll(List.of(test1, test2)));

		IntegrationTest updatedTest1 = readYamlTestFile("/config-binary.yaml");

		// Updates an existing test
		IntegrationDataFetcherTestHelper.saveIntegrationTest(dgsQueryExecutor, updatedTest1);

		// validator sets some defaults for missing values
		ConfigurationValidator.validateExpectedDeltaFiles(updatedTest1);

		listResult = IntegrationDataFetcherTestHelper.getAllIntegrationTests(dgsQueryExecutor);
		assertEquals(2, listResult.size());
		assertTrue(listResult.containsAll(List.of(updatedTest1, test2)));

		IntegrationTest getResult = IntegrationDataFetcherTestHelper.getIntegrationTest(dgsQueryExecutor, testName1);
		assertEquals(updatedTest1, getResult);

		getResult = IntegrationDataFetcherTestHelper.getIntegrationTest(dgsQueryExecutor, testName2);
		assertEquals(test2, getResult);

		assertFalse(IntegrationDataFetcherTestHelper.removeIntegrationTest(dgsQueryExecutor, "3"));
		assertTrue(IntegrationDataFetcherTestHelper.removeIntegrationTest(dgsQueryExecutor, testName1));

		List<IntegrationTest> anotherListResult = IntegrationDataFetcherTestHelper.getAllIntegrationTests(dgsQueryExecutor);
		assertEquals(1, anotherListResult.size());
		assertTrue(anotherListResult.contains(test2));
	}

	@Test
	void testGetErrorSummaryByFlowDatafetcher() {
		OffsetDateTime now = OffsetDateTime.now();
		OffsetDateTime plusTwo = OffsetDateTime.now().plusMinutes(2);
		loadDeltaFilesWithActionErrors(now, plusTwo);

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
				new ErrorSummaryByFlowGraphQLQuery.Builder()
						.limit(5)
						.filter(ErrorSummaryFilter.builder().modifiedBefore(plusTwo).build())
						.direction(DeltaFileDirection.DESC)
						.build(),
				ERRORS_BY_FLOW_PROJECTION_ROOT
		);

		SummaryByFlow actual = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQLQueryRequest.serialize(),
				"data." + DgsConstants.QUERY.ErrorSummaryByFlow,
				SummaryByFlow.class
		);

		assertEquals(4, actual.count());
		assertEquals(0, actual.offset());
		assertEquals(4, actual.totalCount());
		assertEquals(4, actual.countPerFlow().size());
	}

	@Test
	void testGetErrorSummaryByFlow() {
		OffsetDateTime now = OffsetDateTime.now();
		OffsetDateTime plusTwo = OffsetDateTime.now().plusMinutes(2);
		OffsetDateTime minusTwo = OffsetDateTime.now().minusDays(2);

		DeltaFile deltaFile1 = utilService.buildDeltaFile(UUID.randomUUID(), "flow1", DeltaFileStage.ERROR, now, plusTwo);
		deltaFileRepo.save(deltaFile1);

		DeltaFile deltaFile2 = utilService.buildDeltaFile(UUID.randomUUID(), "flow1", DeltaFileStage.COMPLETE, now, now);
		deltaFileRepo.save(deltaFile2);

		DeltaFile deltaFile3 = utilService.buildDeltaFile(UUID.randomUUID(), "flow2", DeltaFileStage.ERROR, now, minusTwo);
		deltaFileRepo.save(deltaFile3);

		DeltaFile deltaFile4 = utilService.buildDeltaFile(UUID.randomUUID(), "flow1", DeltaFileStage.ERROR, now, now);
		deltaFileRepo.save(deltaFile4);

		DeltaFile deltaFile5 = utilService.buildDeltaFile(UUID.randomUUID(), "flow3", DeltaFileStage.ERROR, now, now);
		deltaFileRepo.save(deltaFile5);

		SummaryByFlow firstPage = deltaFilesService.getErrorSummaryByFlow(
				0, 2, null, null, null);

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
				2, 2, null, null, null);

		assertEquals(1, secondPage.count());
		assertEquals(2, secondPage.offset());
		assertEquals(3, secondPage.totalCount());
		assertEquals(1, secondPage.countPerFlow().size());

		assertEquals("flow3", secondPage.countPerFlow().getFirst().getFlow());
		assertEquals(1, secondPage.countPerFlow().getFirst().getCount());
		assertTrue(secondPage.countPerFlow().getFirst().getDids().contains(deltaFile5.getDid()));

		DeltaFile deltaFile6 = utilService.buildDeltaFile(UUID.randomUUID(), "flow3", DeltaFileStage.ERROR, now, minusTwo);
		deltaFileRepo.save(deltaFile6);

		DeltaFile deltaFile7 = utilService.buildDeltaFile(UUID.randomUUID(), "flow3", DeltaFileStage.ERROR, now, minusTwo);
		deltaFileRepo.save(deltaFile7);

		SummaryByFlow filterByTime = deltaFilesService.getErrorSummaryByFlow(
				0, 99, ErrorSummaryFilter.builder().modifiedBefore(now).build(), DeltaFileDirection.DESC, null);

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
				DeltaFileDirection.ASC, null);

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
						.build(), null, null);

		assertEquals(0, noneFound.count());
		assertEquals(0, noneFound.offset());
		assertEquals(0, noneFound.totalCount());
		assertEquals(0, noneFound.countPerFlow().size());
	}

	@Test
	void testErrorCountsByDataSource() {
		OffsetDateTime now = OffsetDateTime.now();

		DeltaFile deltaFile1 = utilService.buildDeltaFile(UUID.randomUUID(), "flow1", DeltaFileStage.ERROR, now, null);
		deltaFileRepo.save(deltaFile1);

		DeltaFile deltaFile2 = utilService.buildDeltaFile(UUID.randomUUID(), "flow1", DeltaFileStage.COMPLETE, now, now);
		deltaFileRepo.save(deltaFile2);

		DeltaFile deltaFile3 = utilService.buildDeltaFile(UUID.randomUUID(), "flow2", DeltaFileStage.ERROR, now, null);
		deltaFileRepo.save(deltaFile3);

		DeltaFile deltaFile4 = utilService.buildDeltaFile(UUID.randomUUID(), "flow1", DeltaFileStage.ERROR, now, now);
		deltaFileRepo.save(deltaFile4);

		DeltaFile deltaFile5 = utilService.buildDeltaFile(UUID.randomUUID(), "flow3", DeltaFileStage.ERROR, now, null);
		deltaFileRepo.save(deltaFile5);

		Set<String> flowSet = new HashSet<>(Arrays.asList("flow1", "flow2", "flow3"));
		Map<String, Integer> errorCountsByDataSource = deltaFileFlowRepo.errorCountsByDataSource(flowSet);

		assertEquals(3, errorCountsByDataSource.size());
		assertEquals(2, errorCountsByDataSource.get("flow1").intValue());
		assertEquals(1, errorCountsByDataSource.get("flow2").intValue());
		assertEquals(1, errorCountsByDataSource.get("flow3").intValue());

		// Test with a non-existing dataSource in the set
		flowSet.add("flowNotFound");
		errorCountsByDataSource = deltaFileFlowRepo.errorCountsByDataSource(flowSet);

		assertEquals(3, errorCountsByDataSource.size());
		assertNull(errorCountsByDataSource.get("flowNotFound"));

		// Test with an empty set
		flowSet.clear();
		errorCountsByDataSource = deltaFileFlowRepo.errorCountsByDataSource(flowSet);

		assertEquals(0, errorCountsByDataSource.size());
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
				0, 99, filterAck, null, null);

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
				0, 99, filterNoAck, null, null);

		assertEquals(1, resultsNoAck.count());
		assertEquals(1, resultsNoAck.countPerFlow().size());
		assertEquals(2, resultsNoAck.countPerFlow().getFirst().getCount());
		assertEquals("f3", resultsNoAck.countPerFlow().getFirst().getFlow());
		assertTrue(resultsNoAck.countPerFlow().getFirst().getDids().containsAll(List.of(DIDS.get(6), DIDS.get(7))));

		ErrorSummaryFilter filterFlowOnly = ErrorSummaryFilter.builder()
				.flow("f3")
				.build();

		SummaryByFlow resultsForFlow = deltaFilesService.getErrorSummaryByFlow(
				0, 99, filterFlowOnly, null, null);

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
						.direction(DeltaFileDirection.DESC)
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
						.direction(DeltaFileDirection.DESC)
						.build(),
				FILTERED_BY_FlOW_PROJECTION_ROOT
		);

		SummaryByFlow actual = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQLQueryRequest.serialize(),
				"data." + DgsConstants.QUERY.FilteredSummaryByFlow,
				SummaryByFlow.class
		);

		assertEquals(2, actual.count());
		assertEquals(0, actual.offset());
		assertEquals(2, actual.totalCount());
		assertEquals(2, actual.countPerFlow().size());
		List<UUID> expectedDids = List.of(dids.getFirst());
		CountPerFlow message0 = actual.countPerFlow().getFirst();
		assertThat(message0.getFlow()).isEqualTo(TIMED_DATA_SOURCE_NAME);
		assertThat(message0.getDids()).isEqualTo(expectedDids);
		CountPerFlow message1 = actual.countPerFlow().getLast();
		assertThat(message1.getFlow()).isEqualTo(DATA_SINK_FLOW_NAME);
		assertThat(message1.getDids()).isEqualTo(expectedDids);
	}

	@Test
	void testGetFilteredSummaryByMessageDatafetcher() {
		OffsetDateTime plusTwo = OffsetDateTime.now().plusMinutes(2);
		List<UUID> dids = loadFilteredDeltaFiles(plusTwo);

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
				new FilteredSummaryByMessageGraphQLQuery.Builder()
						.limit(5)
						.filter(FilteredSummaryFilter.builder().modifiedBefore(plusTwo).build())
						.direction(DeltaFileDirection.DESC)
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
		assertThat(Stream.of(message0.getMessage(), message1.getMessage()).sorted().toList()).isEqualTo(List.of("filtered one", "filtered two"));
		assertThat(message1.getDids()).isEqualTo(expectedDids);
	}

	private List<UUID> loadFilteredDeltaFiles(OffsetDateTime plusTwo) {
		deltaFileRepo.deleteAllInBatch();

		DeltaFile deltaFile = fullFlowExemplarService.postTransformDeltaFile(UUID.randomUUID());
		deltaFile.setFiltered(true);
		deltaFile.firstFlow().getActions().add(filteredAction("filtered one", OffsetDateTime.now()));
		deltaFile.firstFlow().updateState();
		deltaFile.lastFlow().getActions().add(filteredAction("filtered two", OffsetDateTime.now()));
		deltaFile.lastFlow().updateState();
		deltaFile.getFlows().forEach(f -> f.setId(UUID.randomUUID()));

		DeltaFile tooNew = fullFlowExemplarService.postTransformDeltaFile(UUID.randomUUID());
		tooNew.setFiltered(true);
		tooNew.setDataSource("other");
		tooNew.setModified(plusTwo);
		tooNew.firstFlow().getActions().add(filteredAction("another message", plusTwo));
		tooNew.firstFlow().updateState();
		tooNew.getFlows().forEach(f -> f.setId(UUID.randomUUID()));

		DeltaFile notMarkedFiltered = fullFlowExemplarService.postTransformDeltaFile(UUID.randomUUID());
		notMarkedFiltered.setFiltered(null);
		notMarkedFiltered.setDataSource("other");
		notMarkedFiltered.setModified(plusTwo);
		notMarkedFiltered.firstFlow().getActions().add(filteredAction("another message", OffsetDateTime.now()));
		notMarkedFiltered.firstFlow().getActions().getLast().setState(COMPLETE);
		notMarkedFiltered.firstFlow().updateState();
		notMarkedFiltered.getFlows().forEach(f -> f.setId(UUID.randomUUID()));

		deltaFileRepo.insertBatch(List.of(deltaFile, tooNew, notMarkedFiltered), 1000);

		return List.of(deltaFile.getDid(), tooNew.getDid(), notMarkedFiltered.getDid());
	}

	private Action filteredAction(String message, OffsetDateTime time) {
		Action action = new Action();
		action.setName("someAction");
		action.setFilteredCause(message);
		action.setState(ActionState.FILTERED);
		action.setCreated(time);
		action.setModified(time);
		return action;
	}

	@Test
	void testGetErrorSummaryByMessage() {
		OffsetDateTime now = OffsetDateTime.now();
		OffsetDateTime plusTwo = OffsetDateTime.now().plusMinutes(2);

		loadDeltaFilesWithActionErrors(now, plusTwo);

		SummaryByFlowAndMessage fullSummary = deltaFilesService.getErrorSummaryByMessage(
				0, 99, null, null, null);

		assertEquals(0, fullSummary.offset());
		assertEquals(7, fullSummary.count());
		assertEquals(7, fullSummary.totalCount());
		assertEquals(7, fullSummary.countPerMessage().size());

		matchesCounterPerMessage(fullSummary, 0, "causeY", "extraFlow", List.of(DIDS.get(2)));
		matchesCounterPerMessage(fullSummary, 1, "causeA", "f1", List.of(DIDS.get(3), DIDS.get(4)));
		matchesCounterPerMessage(fullSummary, 2, "causeX", "f1", List.of(DIDS.get(1)));
		matchesCounterPerMessage(fullSummary, 3, "causeZ", "f1", List.of(DIDS.get(8)));
		matchesCounterPerMessage(fullSummary, 4, "causeX", "f2", List.of(DIDS.get(0), DIDS.get(2)));
		matchesCounterPerMessage(fullSummary, 5, "causeZ", "f2", List.of(DIDS.get(9)));
		matchesCounterPerMessage(fullSummary, 6, "causeZ", "f3", List.of(DIDS.get(5), DIDS.get(6), DIDS.get(7)));
	}

	@Test
	void testGetErrorSummaryByMessageOrdering() {
		OffsetDateTime now = OffsetDateTime.now();
		OffsetDateTime plusTwo = OffsetDateTime.now().plusMinutes(2);

		loadDeltaFilesWithActionErrors(now, plusTwo);

		SummaryByFlowAndMessage orderByFlow = deltaFilesService.getErrorSummaryByMessage(
				0, 4, null, DeltaFileDirection.ASC, null);

		assertEquals(0, orderByFlow.offset());
		assertEquals(4, orderByFlow.count());
		assertEquals(7, orderByFlow.totalCount());
		assertEquals(4, orderByFlow.countPerMessage().size());

		matchesCounterPerMessage(orderByFlow, 0, "causeY", "extraFlow", List.of(DIDS.get(2)));
		matchesCounterPerMessage(orderByFlow, 1, "causeA", "f1", List.of(DIDS.get(3), DIDS.get(4)));
		matchesCounterPerMessage(orderByFlow, 2, "causeX", "f1", List.of(DIDS.get(1)));
		matchesCounterPerMessage(orderByFlow, 3, "causeZ", "f1", List.of(DIDS.get(8)));

		SummaryByFlowAndMessage orderByCountDesc = deltaFilesService.getErrorSummaryByMessage(
				0, 4, null, DeltaFileDirection.DESC, SummaryByMessageSort.COUNT);

		assertEquals(0, orderByCountDesc.offset());
		assertEquals(4, orderByCountDesc.count());
		assertEquals(7, orderByCountDesc.totalCount());
		assertEquals(4, orderByCountDesc.countPerMessage().size());

		matchesCounterPerMessage(orderByCountDesc, 0, "causeZ", "f3", List.of(DIDS.get(6), DIDS.get(5), DIDS.get(7)));
		matchesCounterPerMessage(orderByCountDesc, 1, "causeX", "f2", List.of(DIDS.get(0), DIDS.get(2)));
		matchesCounterPerMessage(orderByCountDesc, 2, "causeA", "f1", List.of(DIDS.get(4), DIDS.get(3)));
		matchesCounterPerMessage(orderByCountDesc, 3, "causeZ", "f2", List.of(DIDS.get(9)));
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
				0, 99, filterBefore, null, null);

		assertEquals(0, resultsBefore.offset());
		assertEquals(6, resultsBefore.count());
		assertEquals(6, resultsBefore.totalCount());
		assertEquals(6, resultsBefore.countPerMessage().size());

		// no 'causeA' entry
		matchesCounterPerMessage(resultsBefore, 0, "causeY", "extraFlow", List.of(DIDS.get(2)));
		matchesCounterPerMessage(resultsBefore, 1, "causeX", "f1", List.of(DIDS.get(1)));
		matchesCounterPerMessage(resultsBefore, 2, "causeZ", "f1", List.of(DIDS.get(8)));
		matchesCounterPerMessage(resultsBefore, 3, "causeX", "f2", List.of(DIDS.get(0), DIDS.get(2)));
		matchesCounterPerMessage(resultsBefore, 4, "causeZ", "f2", List.of(DIDS.get(9)));
		matchesCounterPerMessage(resultsBefore, 5, "causeZ", "f3", List.of(DIDS.get(5), DIDS.get(6), DIDS.get(7)));

		ErrorSummaryFilter filterAfter = ErrorSummaryFilter.builder()
				.modifiedAfter(plusOne).build();

		SummaryByFlowAndMessage resultAfter = deltaFilesService.getErrorSummaryByMessage(
				0, 99, filterAfter, null, null);

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
				.flow("f1").build();

		SummaryByFlowAndMessage firstPage = deltaFilesService.getErrorSummaryByMessage(
				0, 2, filter, DeltaFileDirection.DESC, null);

		assertEquals(0, firstPage.offset());
		assertEquals(2, firstPage.count());
		assertEquals(3, firstPage.totalCount());
		assertEquals(2, firstPage.countPerMessage().size());
		matchesCounterPerMessage(firstPage, 0, "causeZ", "f1", List.of(DIDS.get(8)));
		matchesCounterPerMessage(firstPage, 1, "causeX", "f1", List.of(DIDS.get(1)));

		SummaryByFlowAndMessage pageTwo = deltaFilesService.getErrorSummaryByMessage(
				2, 2, filter, DeltaFileDirection.DESC, null);

		assertEquals(2, pageTwo.offset());
		assertEquals(1, pageTwo.count());
		assertEquals(3, pageTwo.totalCount());
		assertEquals(1, pageTwo.countPerMessage().size());
		matchesCounterPerMessage(pageTwo, 0, "causeA", "f1", List.of(DIDS.get(3), DIDS.get(4)));

		SummaryByFlowAndMessage invalidPage = deltaFilesService.getErrorSummaryByMessage(
				4, 2, filter, DeltaFileDirection.DESC, null);

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
						.flow("flowNotFound").build(), null, null);

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
		deltaFileRepo.save(utilService.buildErrorDeltaFile(
				DIDS.getFirst(), "f2", "causeX", "x", now, now, null));
		deltaFileRepo.save(utilService.buildErrorDeltaFile(
				DIDS.get(1), "f1", "causeX", "x", now));
		deltaFileRepo.save(utilService.buildErrorDeltaFile(
				DIDS.get(2), "f2", "causeX", "x", now, now, "causeY"));

		// causeA, f1: 2
		deltaFileRepo.save(utilService.buildErrorDeltaFile(
				DIDS.get(3), "f1", "causeA", "x", now, later, null));
		deltaFileRepo.save(utilService.buildErrorDeltaFile(
				DIDS.get(4), "f1", "causeA", "x", now, later, null));

		// causeZ, f2: 1, f3: 3. f1: 1 (which is not the last action)
		DeltaFile deltaFileWithAck = utilService.buildErrorDeltaFile(
				DIDS.get(5), "f3", "causeZ", "x", now);
		deltaFileWithAck.acknowledgeErrors(now, "reason");
		deltaFileRepo.save(deltaFileWithAck);
		deltaFileRepo.save(utilService.buildErrorDeltaFile(
				DIDS.get(6), "f3", "causeZ", "x", now));
		deltaFileRepo.save(utilService.buildErrorDeltaFile(
				DIDS.get(7), "f3", "causeZ", "x", now));
		deltaFileRepo.save(utilService.buildErrorDeltaFile(
				DIDS.get(8), "f1", "causeZ", "x", now, now, null));
		deltaFileRepo.save(utilService.buildErrorDeltaFile(
				DIDS.get(9), "f2", "causeZ", "x", now, now, null));

		// these have no errors
		deltaFileRepo.save(utilService.buildDeltaFile(
				DIDS.get(10), "f1", DeltaFileStage.COMPLETE, now, now));
		deltaFileRepo.save(utilService.buildDeltaFile(
				DIDS.get(11), "f4", DeltaFileStage.COMPLETE, now, now));
	}

    void updateProperties(boolean splitByGroup) {
        deltaFiPropertiesService.upsertProperties();
        // verify nothing is set for systemName to start
        deltaFiPropertiesRepo.unsetProperties(List.of(SYSTEM_NAME));
        checkSystemNameProp("DeltaFi", false, splitByGroup);

        deltaFiPropertiesService.updateProperties(List.of(new KeyValue(SYSTEM_NAME, "newName")));
        deltaFiPropertiesService.refreshProperties();

        // verify the property is updated
        checkSystemNameProp("newName", true, splitByGroup);

        // already newName no changes made, return false
        assertThat(deltaFiPropertiesRepo.updateProperty(SYSTEM_NAME, "newName")).isEqualTo(0);
    }
	@Test
	void updateProperties() {
        updateProperties(false);	}

    @Test
    void updatePropertiesWhenSplitByGroup() {
        updateProperties(true);
    }

    @Test
	void unsetProperties() {
		deltaFiPropertiesService.upsertProperties();
		assertThat(deltaFiPropertiesRepo.unsetProperties(List.of(SYSTEM_NAME))).isEqualTo(0); // nothing to unset

		// set a custom value that will be unset
		deltaFiPropertiesRepo.updateProperty(SYSTEM_NAME, "newName");
		deltaFiPropertiesService.refreshProperties();
		checkSystemNameProp("newName", true, false);

		// unset the custom value
		assertThat(deltaFiPropertiesRepo.unsetProperties(List.of(SYSTEM_NAME))).isGreaterThan(0);
		deltaFiPropertiesService.refreshProperties();

		checkSystemNameProp("DeltaFi", false, false);

		// second time no change is needed so it returns false
		assertThat(deltaFiPropertiesRepo.unsetProperties(List.of(SYSTEM_NAME))).isEqualTo(0);
	}

	private void checkSystemNameProp(String expected, boolean hasValueSet, boolean splitByGroup) {
		DeltaFiProperties current = deltaFiPropertiesService.getDeltaFiProperties();
		assertThat(current.getSystemName()).isEqualTo(expected);

		// UI_CONTROLS is last in enum PropertyGroup
        Property systemNameProp = deltaFiPropertiesService.getPopulatedProperties(splitByGroup).getLast().getProperties().stream()
				.filter(p -> p.getKey().equals(SYSTEM_NAME))
				.findFirst().orElseThrow();

		assertThat(systemNameProp.hasValue()).isEqualTo(hasValueSet);
		assertThat(systemNameProp.getValue()).isEqualTo(expected);
		assertThat(systemNameProp.getDefaultValue()).isEqualTo("DeltaFi");
	}

	@Test
	void testImportSnapshot() {
		SystemSnapshot snapshot = SystemSnapshotTestHelper.importSystemSnapshot(dgsQueryExecutor);
		assertThat(snapshot).isEqualTo(SystemSnapshotTestHelper.expectedSnapshot());
	}

	@Test
	void restoreSnapshot() {
		Mockito.when(deployerService.resetFromSnapshot(Mockito.any(), Mockito.anyBoolean())).thenReturn(Result.successResult());
		pluginService.doUpdateSystemPlugin();
		systemSnapshotRepo.save(SystemSnapshotTestHelper.expectedSnapshot());
		Result result = SystemSnapshotTestHelper.restoreSnapshot(dgsQueryExecutor);

		assertThat(result.isSuccess()).isTrue();
	}

	@Test
    void testGetSystemSnapshotsByFilter() {
        SystemSnapshot snapshot1 = SystemSnapshotTestHelper.importSystemSnapshot(dgsQueryExecutor);
        SystemSnapshot snapshot2 = SystemSnapshotTestHelper.importSystemSnapshot2(dgsQueryExecutor);

        // matches only 1
        GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(
                new GetSystemSnapshotsByFilterGraphQLQuery.Builder()
                        .limit(5)
                        .filter(SystemSnapshotFilter.newBuilder().reasonFilter(
                                        NameFilter.newBuilder().caseSensitive(true).name("REASON1").build())
                                .build())
                        .build(),
                SystemSnapshotTestHelper.BY_FILTER_PROJECTION_ROOT
        );

        SystemSnapshots actual = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + DgsConstants.QUERY.GetSystemSnapshotsByFilter,
                SystemSnapshots.class
        );

        assertEquals(0, actual.getOffset());
        assertEquals(1, actual.getCount());
        assertEquals(1, actual.getTotalCount());
        assertEquals(snapshot1, actual.getSystemSnapshots().getFirst());

        // matches both, returns reverse order by id
        graphQLQueryRequest = new GraphQLQueryRequest(
                new GetSystemSnapshotsByFilterGraphQLQuery.Builder()
                        .limit(5)
                        .sortField(SystemSnapshotSort.ID)
                        .direction(DeltaFileDirection.DESC)
                        .filter(SystemSnapshotFilter.newBuilder().reasonFilter(
                                        NameFilter.newBuilder().caseSensitive(false).name("REASON").build())
                                .build())
                        .build(),
                SystemSnapshotTestHelper.BY_FILTER_PROJECTION_ROOT
        );

        actual = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + DgsConstants.QUERY.GetSystemSnapshotsByFilter,
                SystemSnapshots.class
        );

        assertEquals(0, actual.getOffset());
        assertEquals(2, actual.getCount());
        assertEquals(2, actual.getTotalCount());
        assertEquals(snapshot2, actual.getSystemSnapshots().getFirst());
        assertEquals(snapshot1, actual.getSystemSnapshots().getLast());

        // matches both, returns ASC order by id
        graphQLQueryRequest = new GraphQLQueryRequest(
                new GetSystemSnapshotsByFilterGraphQLQuery.Builder()
                        .limit(5)
                        .sortField(SystemSnapshotSort.ID)
                        .direction(DeltaFileDirection.ASC)
                        .filter(SystemSnapshotFilter.newBuilder().reasonFilter(
                                        NameFilter.newBuilder().caseSensitive(null).name("REASON").build())
                                .build())
                        .build(),
                SystemSnapshotTestHelper.BY_FILTER_PROJECTION_ROOT
        );

        actual = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + DgsConstants.QUERY.GetSystemSnapshotsByFilter,
                SystemSnapshots.class
        );

        assertEquals(0, actual.getOffset());
        assertEquals(2, actual.getCount());
        assertEquals(2, actual.getTotalCount());
        assertEquals(snapshot1, actual.getSystemSnapshots().getFirst());
        assertEquals(snapshot2, actual.getSystemSnapshots().getLast());

        // matches both, default order (created/DESC), but only returns '1' due to offset
        graphQLQueryRequest = new GraphQLQueryRequest(
                new GetSystemSnapshotsByFilterGraphQLQuery.Builder()
                        .limit(5)
                        .offset(1)
                        .filter(SystemSnapshotFilter.newBuilder()
                                .createdAfter(OffsetDateTime.parse("2021-02-28T21:27:03.407Z"))
                                .createdBefore(OffsetDateTime.parse("2025-02-28T21:27:03.407Z"))
                                .build())
                        .build(),
                SystemSnapshotTestHelper.BY_FILTER_PROJECTION_ROOT
        );

        actual = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + DgsConstants.QUERY.GetSystemSnapshotsByFilter,
                SystemSnapshots.class
        );

        assertEquals(1, actual.getOffset());
        assertEquals(1, actual.getCount());
        assertEquals(2, actual.getTotalCount());
        assertEquals(snapshot1, actual.getSystemSnapshots().getFirst());

        // does not match anything
        graphQLQueryRequest = new GraphQLQueryRequest(
                new GetSystemSnapshotsByFilterGraphQLQuery.Builder()
                        .limit(5)
                        .filter(SystemSnapshotFilter.newBuilder()
                                .createdAfter(OffsetDateTime.parse("2025-02-28T21:27:03.407Z"))
                                .createdBefore(OffsetDateTime.parse("2021-02-28T21:27:03.407Z"))
                                .build())
                        .build(),
                SystemSnapshotTestHelper.BY_FILTER_PROJECTION_ROOT
        );

        actual = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                graphQLQueryRequest.serialize(),
                "data." + DgsConstants.QUERY.GetSystemSnapshotsByFilter,
                SystemSnapshots.class
        );

        assertEquals(0, actual.getOffset());
        assertEquals(0, actual.getCount());
        assertEquals(0, actual.getTotalCount());
    }

    @Test
	void setDataSinkExpectedAnnotations() {
		clearForFlowTests();
		DataSink dataSink = new DataSink();
		dataSink.setName("dataSink");
		dataSink.setExpectedAnnotations(Set.of("a", "b"));
		dataSinkRepo.save(dataSink);

		assertThat(dataSinkRepo.updateExpectedAnnotations("dataSink", Set.of("b", "a", "c"))).isGreaterThan(0);
		assertThat(dataSinkRepo.findByNameAndType("dataSink", FlowType.DATA_SINK, DataSink.class).orElseThrow().getExpectedAnnotations()).hasSize(3).containsAll(Set.of("a", "b", "c"));
	}

	@Test
	void testUpdatePendingAnnotationsForFlows() {
		String flow = "flowThatChanges";
		Set<String> expectedAnnotations = Set.of("f");
		DeltaFile completeAfterChange = utilService.buildDeltaFile(UUID.randomUUID());
		completeAfterChange.firstFlow().setPendingAnnotations(expectedAnnotations);
		completeAfterChange.addAnnotations(Map.of("a", "value")); // this already has the expected annotation, the dataSource state should go to complete
		setupPendingAnnotations(completeAfterChange, flow, expectedAnnotations);

		DeltaFile waitingForA = utilService.buildDeltaFile(UUID.randomUUID());
		setupPendingAnnotations(waitingForA, flow, expectedAnnotations); // this does not have the expected annotation, should have a dataSource state of PENDING_ANNOTATIONS

		DeltaFile differentFlow = utilService.buildDeltaFile(UUID.randomUUID());
		setupPendingAnnotations(differentFlow, "otherFlow", Set.of("f2")); // should not be impacted, different dataSource

		deltaFileRepo.saveAll(List.of(completeAfterChange, waitingForA, differentFlow));
		deltaFilesService.updatePendingAnnotationsForFlows(flow, Set.of("a"));

		UtilService.assertEqualsIgnoringDates(waitingForA, deltaFilesService.getDeltaFile(waitingForA.getDid()));
		UtilService.assertEqualsIgnoringDates(differentFlow, deltaFilesService.getDeltaFile(differentFlow.getDid()));

		DeltaFile updated = deltaFilesService.getDeltaFile(completeAfterChange.getDid());
		assertThat(updated.pendingAnnotationFlows()).isEmpty();
		assertThat(updated.firstFlow().getState()).isEqualTo(DeltaFileFlowState.COMPLETE);
	}

	@Test
	void testReadReceiptsPending() throws IOException {
		UUID did = UUID.randomUUID();
		deltaFileRepo.save(fullFlowExemplarService.postTransformDeltaFile(did));
		dataSinkService.setExpectedAnnotations("sampleEgress", Set.of("readReceipt"));
		deltaFilesService.handleActionEvent(actionEvent("egress", did));
		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertEqualsIgnoringDates(fullFlowExemplarService.postEgressDeltaFile(did, Set.of("readReceipt"), null), deltaFile);
		assertFalse(deltaFile.isTerminal());
	}

	@Test
	void testReadReceiptsProcessed() {
		UUID did = UUID.randomUUID();
		deltaFileRepo.save(fullFlowExemplarService.postEgressDeltaFile(did, Set.of("readReceipt"), null));
		deltaFilesService.addAnnotations(did, Map.of("readReceipt", "123"), false);
		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertEqualsIgnoringDates(fullFlowExemplarService.postEgressDeltaFile(did, null, Map.of("readReceipt", "123")), deltaFile);
		assertTrue(deltaFile.isTerminal());
	}

	@Test
	void countErrors() {
		String reason = "reason";
		OffsetDateTime time = OffsetDateTime.now(clock);
		DeltaFile acknowledgedError = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, time, time);
		acknowledgedError.setStage(DeltaFileStage.ERROR);
		acknowledgedError.firstFlow().setErrorAcknowledged(time);
		acknowledgedError.firstFlow().addAction("ErrorAction", null, ActionType.TRANSFORM, ERROR, time).setErrorCause(reason);
		acknowledgedError.firstFlow().updateState();

		DeltaFile unacknowledgedError = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, time, time);
		unacknowledgedError.setStage(DeltaFileStage.ERROR);
		Action unacknowledgedAction = unacknowledgedError.firstFlow().addAction("ErrorAction", null, ActionType.TRANSFORM, ERROR, time);
		unacknowledgedAction.setErrorCause(reason);
		unacknowledgedError.firstFlow().updateState();
		deltaFileRepo.saveAll(List.of(unacknowledgedError, acknowledgedError));

		assertThat(deltaFileFlowRepo.countUnacknowledgedErrors()).isEqualTo(1);
	}

	private void setupPendingAnnotations(DeltaFile deltaFile, String flowName, Set<String> pendingAnnotations) {
		DeltaFileFlow deltaFileFlow = deltaFile.firstFlow();
		deltaFileFlow.setState(DeltaFileFlowState.PENDING_ANNOTATIONS);
		deltaFileFlow.setFlowDefinition(flowDefinitionService.getOrCreateFlow(flowName, FlowType.DATA_SINK));
		deltaFileFlow.setPendingAnnotations(pendingAnnotations);
	}

	private void verifyActionEventResults(DeltaFile expected, ActionContext... forActions) {
		DeltaFile afterMutation = deltaFilesService.getDeltaFile(expected.getDid());
		assertEqualsIgnoringDates(expected, afterMutation);

		Mockito.verify(coreEventQueue).putActions(actionInputListCaptor.capture(), anyBoolean());
		List<WrappedActionInput> actionInputs = actionInputListCaptor.getValue();
		assertThat(actionInputs).hasSize(forActions.length);
		for (int i = 0; i < forActions.length; i++) {
			WrappedActionInput actionInput = actionInputs.get(i);
			assertThat(actionInput.getActionContext().getFlowName()).isEqualTo(forActions[i].getFlowName());
			assertThat(actionInput.getActionContext().getActionName()).isEqualTo(forActions[i].getActionName());
			assertEquals(forQueueHelper(expected, actionInput.getActionContext()), actionInput.getDeltaFileMessages().getFirst());
		}
	}

	void clearForFlowTests() {
		transformFlowRepo.deleteAllInBatch();
		dataSinkRepo.deleteAllInBatch();
		timedDataSourceRepo.deleteAllInBatch();
		onErrorDataSourceRepo.deleteAllInBatch();
		pluginVariableRepo.deleteAllInBatch();
		pluginRepository.deleteAll();
		pluginRepository.save(pluginService.createSystemPlugin());
		refreshFlowCaches();
	}

	@Test
	void annotations() {
		deltaFileRepo.save(DeltaFile.builder()
				.annotations(Set.of(new Annotation("x", "1"), new Annotation("y", "2")))
				.build());
		deltaFileRepo.save(DeltaFile.builder()
				.annotations(Set.of(new Annotation("y", "3"), new Annotation("z", "4")))
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
		return ingress(REST_DATA_SOURCE_NAME, filename, body);
	}

	@SneakyThrows
	private ResponseEntity<String> ingress(String dataSource, String filename, byte[] body) {
		HttpHeaders headers = new HttpHeaders();
		if (filename != null) {
			headers.add("Filename", filename);
		}
		headers.add("DataSource", dataSource);
		headers.add("Metadata", METADATA);
		headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM);
		headers.add(USER_NAME_HEADER, USERNAME);
		headers.add(DeltaFiConstants.PERMISSIONS_HEADER, DeltaFiConstants.ADMIN_PERMISSION);
		HttpEntity<byte[]> request = new HttpEntity<>(body, headers);

		Content defaultContent = new Content(filename, MediaType.APPLICATION_OCTET_STREAM, new Segment(UUID.randomUUID(), 0, CONTENT_DATA.length(), UUID.randomUUID()));
		Mockito.lenient().when(contentStorageService.save(any(), (InputStream) any(), any(), any())).thenReturn(defaultContent);

		return restTemplate.postForEntity("/api/v2/deltafile/ingress", request, String.class);
	}

	@Test
	@SneakyThrows
	void testIngressFromAction() {
		UUID taskedDid = UUID.randomUUID();
		UUID did = UUID.randomUUID();

		timedDataSourceService.setLastRun(TIMED_DATA_SOURCE_NAME, OffsetDateTime.now(), taskedDid);
		deltaFilesService.handleActionEvent(actionEvent("ingress", taskedDid, did));

		verifyActionEventResults(fullFlowExemplarService.ingressedFromAction(did, TIMED_DATA_SOURCE_NAME),
				ActionContext.builder().flowName("sampleTransform").actionName("Utf8TransformAction").build());

		verifyIngressMetrics(TIMED_DATA_SOURCE_NAME, 36);
		verifySubscribeMetrics(TIMED_DATA_SOURCE_NAME, "sampleTransform", 36);

		Map<String, String> tags = tagsFor(ActionEventType.INGRESS, "SampleTimedIngressAction", TIMED_DATA_SOURCE_NAME, null);
		extendTagsForAction(tags, "type");
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.ACTION_EXECUTION_TIME_MS, 1).addTags(tags));
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.ACTION_EXECUTION, 1).addTags(tags));

		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	@SneakyThrows
	void testTimedIngressUsingAnnotationConfig() {
		UUID taskedDid = UUID.randomUUID();
		UUID did = UUID.randomUUID();

		timedDataSourceService.setLastRun(TIMED_DATA_SOURCE_WITH_ANNOTATION_CONFIG_NAME, OffsetDateTime.now(), taskedDid);
		deltaFilesService.handleActionEvent(actionEvent("ingressAnnot", taskedDid, did));

		DeltaFile afterMutation = deltaFilesService.getDeltaFile(did);
		assertEquals("annotValue", Annotation.toMap(afterMutation.getAnnotations()) .get("timedAnnotKey"));
		assertEquals("b", Annotation.toMap(afterMutation.getAnnotations()) .get("a"));
		assertEquals("d", Annotation.toMap(afterMutation.getAnnotations()) .get("c"));
		assertEquals(3, afterMutation.getAnnotations().size());

		verifyIngressMetrics(TIMED_DATA_SOURCE_WITH_ANNOTATION_CONFIG_NAME, 36);
		verifySubscribeMetrics(TIMED_DATA_SOURCE_WITH_ANNOTATION_CONFIG_NAME, "sampleTransform", 36);

		Map<String, String> tags = tagsFor(ActionEventType.INGRESS, "SampleTimedIngressAction", TIMED_DATA_SOURCE_WITH_ANNOTATION_CONFIG_NAME, null);
		extendTagsForAction(tags, "type");
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.ACTION_EXECUTION_TIME_MS, 1).addTags(tags));
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.ACTION_EXECUTION, 1).addTags(tags));

		Mockito.verifyNoMoreInteractions(metricService);

	}

	private void verifyIngressMetrics(String dataSource, int bytes) {
		Map<String, String> pubTags = Map.of(
				"dataSource", dataSource
		);
		Mockito.verify(metricService).increment(new Metric(BYTES_FROM_SOURCE, bytes).addTags(pubTags));
		Mockito.verify(metricService).increment(new Metric(FILES_FROM_SOURCE, 1).addTags(pubTags));
	}

	private void verifySubscribeMetrics(String dataSource, String flowName, int bytes) {
		Map<String, String> subTags = Map.of(
				"dataSource", dataSource,
				"flowName", flowName
		);
		Mockito.verify(metricService).increment(new Metric(BYTES_IN, bytes).addTags(subTags));
		Mockito.verify(metricService).increment(new Metric(FILES_IN, 1).addTags(subTags));
	}

	@Test
	@SneakyThrows
	void testIngressErrorFromAction() {
		UUID taskedDid = UUID.randomUUID();
		UUID did = UUID.randomUUID();

		timedDataSourceService.setLastRun(TIMED_DATA_SOURCE_ERROR_NAME, OffsetDateTime.now(), taskedDid);
		deltaFilesService.handleActionEvent(actionEvent("ingressError", taskedDid, did));

		DeltaFile actual = deltaFilesService.getDeltaFile(did);
		DeltaFile expected = fullFlowExemplarService.ingressedFromActionWithError(did);
		assertEqualsIgnoringDates(expected, actual);

		Map<String, String> tags = tagsFor(ActionEventType.INGRESS, "SampleTimedIngressErrorAction", TIMED_DATA_SOURCE_ERROR_NAME, null);
		Map<String, String> flowTags = Map.of(
				"dataSource", TIMED_DATA_SOURCE_ERROR_NAME);

		Mockito.verify(metricService).increment(new Metric(FILES_FROM_SOURCE, 1).addTags(flowTags));
		Mockito.verify(metricService).increment(new Metric(BYTES_FROM_SOURCE, 36).addTags(flowTags));
		Mockito.verify(metricService).increment(new Metric(FILES_ERRORED, 1).addTags(tags));

		extendTagsForAction(tags, "type");
		Mockito.verify(metricService).increment(new Metric(ACTION_EXECUTION_TIME_MS, 1).addTags(tags));
		Mockito.verify(metricService).increment(new Metric(ACTION_EXECUTION, 1).addTags(tags));

		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testIngress() {
		ResponseEntity<String> response = ingress(FILENAME, CONTENT_DATA.getBytes());
		assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());
		assertNotNull(response.getBody());
		Optional<DeltaFile> deltaFile = deltaFileRepo.findById(UUID.fromString(response.getBody()));
		assertTrue(deltaFile.isPresent());
	}

	@Test
	void testIngress_missingFilename() {
		ResponseEntity<String> response = ingress(null, CONTENT_DATA.getBytes());
		assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatusCode().value());
		assertEquals("Filename must be passed in as a header", response.getBody());
	}

	@Test
	void testIngress_disabled() {
		deltaFiPropertiesService.getDeltaFiProperties().setIngressEnabled(false);
		ResponseEntity<String> response = ingress(FILENAME, CONTENT_DATA.getBytes());
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), response.getStatusCode().value());
	}

	@Test
	@SneakyThrows
	void testIngress_storageLimit() {
		Mockito.when(contentStorageService.save(any(), (InputStream) any(), any(), any())).thenThrow(new ObjectStorageException("out of space"));

		// Make the REST call directly to avoid the helper method's mock setup
		HttpHeaders headers = new HttpHeaders();
		headers.add("Filename", FILENAME);
		headers.add("DataSource", REST_DATA_SOURCE_NAME);
		headers.add("Metadata", METADATA);
		headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM);
		headers.add(USER_NAME_HEADER, USERNAME);
		headers.add(DeltaFiConstants.PERMISSIONS_HEADER, DeltaFiConstants.ADMIN_PERMISSION);
		HttpEntity<byte[]> request = new HttpEntity<>(CONTENT_DATA.getBytes(), headers);

		ResponseEntity<String> response = restTemplate.postForEntity("/api/v2/deltafile/ingress", request, String.class);
		assertEquals(HttpStatus.INSUFFICIENT_STORAGE.value(), response.getStatusCode().value());
	}

	@Test
	void testRestDataSourceFilesRateLimitDuringIngress() {
		RestDataSource dataSource = buildRestDataSource("files-rate-test", FlowState.RUNNING);
		dataSource.setName("files-rate-test");
		RateLimit rateLimit = RateLimit.newBuilder()
				.unit(RateLimitUnit.FILES)
				.maxAmount(2L)
				.durationSeconds(60)
				.build();
		dataSource.setRateLimit(rateLimit);
		restDataSourceRepo.save(dataSource);
		refreshFlowCaches();

		// First ingress should succeed (file 1/2)
		ResponseEntity<String> response = ingress("files-rate-test", "test1.txt", "test content 1".getBytes());
		assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());

		// Second ingress should succeed (file 2/2)
		response = ingress("files-rate-test", "test2.txt", "test content 2".getBytes());
		assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());

		// Third ingress should fail due to rate limit exceeded
		response = ingress("files-rate-test", "test3.txt", "test content 3".getBytes());
		assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response.getStatusCode().value());
		assertEquals("Rate limit exceeded - files-rate-test allows 2 files per 60 seconds", response.getBody());
	}

	@Test
	void testRestDataSourceBytesRateLimitDuringIngress() {
		RestDataSource dataSource = buildRestDataSource("bytes-rate-test", FlowState.RUNNING);
		dataSource.setName("bytes-rate-test");
		RateLimit rateLimit = RateLimit.newBuilder()
				.unit(RateLimitUnit.BYTES)
				.maxAmount(20L)
				.durationSeconds(60)
				.build();
		dataSource.setRateLimit(rateLimit);
		restDataSourceRepo.save(dataSource);
		refreshFlowCaches();

		// First ingress with 10 bytes should succeed
		ResponseEntity<String> response = ingress("bytes-rate-test", "test1.txt", "1234567890".getBytes());
		assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());

		// Second ingress with 11 bytes should succeed (total: 21/20 bytes)
		response =  ingress("bytes-rate-test", "test2.txt", "1234567890a".getBytes());
		assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());

		// Third ingress with 5 bytes should fail (20 byte limit has been exceeded by previous ingress)
		response = ingress("bytes-rate-test", "test3.txt", "12345".getBytes());
		assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response.getStatusCode().value());
		assertEquals("Rate limit exceeded - bytes-rate-test allows 20 bytes per 60 seconds", response.getBody());
	}

	@Test
	void testSetContentDeletedByDidIn() {
		DeltaFile deltaFile1 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, NOW.minusSeconds(2), NOW.minusSeconds(2));
		DeltaFile deltaFile2 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, NOW.plusSeconds(2), NOW.plusSeconds(1));
		DeltaFile deltaFile3 = utilService.buildDeltaFile(UUID.randomUUID(), "dataSource", DeltaFileStage.COMPLETE, NOW.plusSeconds(2), NOW.plusSeconds(2));
		deltaFileRepo.insertBatch(List.of(deltaFile1, deltaFile2, deltaFile3), 1000);

		List<UUID> dids = new ArrayList<>();
		dids.add(deltaFile1.getDid());
		dids.add(deltaFile3.getDid());
		for (int i = 0; i < 3000; i++) {
			dids.add(UUID.randomUUID());
		}
		Collections.shuffle(dids);

		deltaFileRepo.setContentDeletedByDidIn(dids, NOW, "MyPolicy");
		DeltaFiles deltaFiles = deltaFileRepo.deltaFiles(null, 50, new DeltaFilesFilter(), null, null);
		deltaFile1.setContentDeleted(NOW);
		deltaFile1.setContentDeletedReason("MyPolicy");
		deltaFile3.setContentDeleted(NOW);
		deltaFile3.setContentDeletedReason("MyPolicy");
		assertEquals(List.of(deltaFile3, deltaFile2, deltaFile1), deltaFiles.getDeltaFiles());
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testDeleteMultipleBatches() {
		deltaFiPropertiesService.updateProperties(List.of(new KeyValue("deletePolicyBatchSize", "1000")));
		List<DeltaFile> deltaFiles = new ArrayList<>();
		for (int i = 0; i < 1500; i++) {
			DeltaFile deltaFile = DeltaFile.builder()
					.did(UUID.randomUUID())
					.stage(DeltaFileStage.COMPLETE)
					.created(OffsetDateTime.now().minusDays(1))
					.totalBytes(10)
					.build();
			deltaFile.updateFlags();
			deltaFiles.add(deltaFile);
		}
		deltaFileRepo.insertBatch(deltaFiles, 1000);
		assertEquals(1500, deltaFileRepo.count());
		boolean moreToDelete = deltaFilesService.timedDelete(OffsetDateTime.now(), null, 0L, null, "policyName", true, 1000, false);
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
				.flows(Set.of(DeltaFileFlow.builder()
						.flowDefinition(flowDefinitionService.getOrCreateFlow("flow", FlowType.TRANSFORM))
						.actions(List.of(
								Action.builder().state(ERROR).build())).build()))
				.build();
		error.updateFlags();

		DeltaFile complete = DeltaFile.builder()
				.did(UUID.randomUUID())
				.created(OffsetDateTime.now())
				.totalBytes(2)
				.stage(DeltaFileStage.COMPLETE)
				.flows(Set.of(DeltaFileFlow.builder()
						.flowDefinition(flowDefinitionService.getOrCreateFlow("flow", FlowType.TRANSFORM))
						.actions(List.of(
								Action.builder().state(COMPLETE).build())).build()))
				.build();
		complete.updateFlags();

		DeltaFile errorAcked = DeltaFile.builder()
				.did(UUID.randomUUID())
				.created(OffsetDateTime.now())
				.totalBytes(4)
				.stage(DeltaFileStage.ERROR)
				.flows(Set.of(DeltaFileFlow.builder()
						.flowDefinition(flowDefinitionService.getOrCreateFlow("flow", FlowType.TRANSFORM))
						.actions(List.of(
								Action.builder().state(ERROR).build())).errorAcknowledged(OffsetDateTime.now()).build()))
				.build();
		errorAcked.updateFlags();

		deltaFileRepo.saveAll(List.of(error, complete, errorAcked));
		deltaFilesService.diskSpaceDelete(500, 1000);
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.DELETED_FILES, 2).addTag("policy", DiskSpaceDelete.POLICY_NAME));
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.DELETED_BYTES, 6).addTag("policy", DiskSpaceDelete.POLICY_NAME));
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
				.flows(Set.of())
				.build();
		complete.updateFlags();

		DeltaFile contentDeleted = DeltaFile.builder()
				.did(UUID.randomUUID())
				.created(OffsetDateTime.now())
				.totalBytes(2)
				.stage(DeltaFileStage.COMPLETE)
				.contentDeleted(OffsetDateTime.now())
				.flows(Set.of())
				.build();
		contentDeleted.updateFlags();

		deltaFileRepo.saveAll(List.of(complete, contentDeleted));
		boolean moreToDelete = deltaFilesService.timedDelete(OffsetDateTime.now().plusSeconds(5), null, 0L, null, "policyName", true, 1000, false);
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
		// Queue counts should be present (calculated from QueueManagementService)
		assertNotNull(none.getWarmQueuedCount());
		assertNotNull(none.getColdQueuedCount());

		DeltaFile deltaFile1 = utilService.emptyDeltaFile(UUID.randomUUID(), "dataSource", List.of());
		deltaFile1.setTotalBytes(1L);
		deltaFile1.setReferencedBytes(2L);
		deltaFile1.setStage(DeltaFileStage.IN_FLIGHT);

		DeltaFile deltaFile2 = utilService.emptyDeltaFile(UUID.randomUUID(), "dataSource", List.of());
		deltaFile2.setTotalBytes(2L);
		deltaFile2.setReferencedBytes(4L);
		deltaFile2.setContentDeleted(OffsetDateTime.now());
		deltaFile2.setStage(DeltaFileStage.IN_FLIGHT);

		DeltaFile deltaFile3 = utilService.emptyDeltaFile(UUID.randomUUID(), "dataSource", List.of());
		deltaFile3.setTotalBytes(4L);
		deltaFile3.setReferencedBytes(8L);
		deltaFile3.setStage(DeltaFileStage.COMPLETE);

		deltaFileRepo.saveAll(List.of(deltaFile1, deltaFile2, deltaFile3));

		DeltaFileStats all = deltaFilesService.deltaFileStats();
		assertEquals(3, deltaFileRepo.count());
		assertEquals(6L, all.getInFlightBytes());
		assertEquals(2L, all.getInFlightCount());
		assertEquals(0L, all.getWarmQueuedCount());
		// coldQueuedCount comes from Valkey (populated by ColdQueueCheck), which is 0 in tests
		assertEquals(0L, all.getColdQueuedCount());
	}

    @Test
    void testTransformUtf8() throws IOException {
        UUID did = UUID.randomUUID();
        deltaFileRepo.save(fullFlowExemplarService.postIngressDeltaFile(did));

        deltaFilesService.handleActionEvent(actionEvent("transformUtf8", did));

        verifyActionEventResults(fullFlowExemplarService.postTransformUtf8DeltaFile(did),
                ActionContext.builder().flowName("sampleTransform").actionName("SampleTransformAction").build());

        Map<String, String> tags = tagsFor(ActionEventType.TRANSFORM, "Utf8TransformAction", REST_DATA_SOURCE_NAME, null);
        extendTagsForAction(tags, "type");
        Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.ACTION_EXECUTION_TIME_MS, 1).addTags(tags));
        Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.ACTION_EXECUTION, 1).addTags(tags));
        Mockito.verifyNoMoreInteractions(metricService);
    }

    @Test
    void testTransformUtf8FlowNoLongerRunning() throws IOException {
        UUID did = UUID.randomUUID();
        deltaFileRepo.save(fullFlowExemplarService.postIngressDeltaFile(did));
        transformFlowService.stopFlow(SAMPLE_TRANSFORM_FLOW.getName());

        // From transformUtf8.json:
        UUID segmentUuid = UUID.fromString("11111111-1111-1111-1111-111111111114");
        deltaFilesService.handleActionEvent(actionEvent("transformUtf8", did));

        DeltaFile actual = deltaFilesService.getDeltaFile(did);
        DeltaFile expected = fullFlowExemplarService.postTransformUtf8NotRunningDeltaFile(did);
        assertEqualsIgnoringDates(expected, actual);

        Mockito.verify(contentStorageService).deleteAllByObjectName(List.of(Segment.objectName(did, segmentUuid)));
    }

    @Test
    void testTransformUtf8FlowNoLongerRunningSubsegment() throws IOException {
        UUID did = UUID.randomUUID();
        deltaFileRepo.save(fullFlowExemplarService.postIngressDeltaFile(did));
        transformFlowService.stopFlow(SAMPLE_TRANSFORM_FLOW.getName());

        // transformUtf8Subsegment.json -- uses same uuid as from ingress (i.e. subsegment)
        // adds content with the same did
        deltaFilesService.handleActionEvent(actionEventWithParent("transformUtf8Subsegment", did, did));

        DeltaFile actual = deltaFilesService.getDeltaFile(did);
        DeltaFile expected = fullFlowExemplarService.postTransformUtf8NotRunningDeltaFile(did);
        assertEqualsIgnoringDates(expected, actual);

        Mockito.verifyNoInteractions(contentStorageService);
    }

    @Test
    void testTransformUtf8FlowNoLongerRunningParentContent() throws IOException {
        UUID did = UUID.randomUUID();
        UUID parentDid = UUID.randomUUID();
        deltaFileRepo.save(fullFlowExemplarService.postIngressDeltaFile(did, parentDid));
        transformFlowService.stopFlow(SAMPLE_TRANSFORM_FLOW.getName());

        // transformUtf8Subsegment.json -- uses same uuid as from ingress (i.e. subsegment)
        // adds content with the parent's did
        deltaFilesService.handleActionEvent(actionEventWithParent("transformUtf8Subsegment", did, parentDid));

        DeltaFile actual = deltaFilesService.getDeltaFile(did);
        DeltaFile expected = fullFlowExemplarService.postTransformUtf8NotRunningDeltaFile(did, parentDid);
        assertEqualsIgnoringDates(expected, actual);

        Mockito.verifyNoInteractions(contentStorageService);
    }

    @Test
	void testTransform() throws IOException {
		UUID did = UUID.randomUUID();
		deltaFileRepo.save(fullFlowExemplarService.postTransformUtf8DeltaFile(did));

		deltaFilesService.handleActionEvent(actionEvent("transform", did));

		verifyActionEventResults(fullFlowExemplarService.postTransformDeltaFile(did),
				ActionContext.builder().flowName(DATA_SINK_FLOW_NAME).actionName(SAMPLE_EGRESS_ACTION).build());

		verifyCommonMetrics(ActionEventType.TRANSFORM, "SampleTransformAction", REST_DATA_SOURCE_NAME, TRANSFORM_FLOW_NAME, null, "type");
	}

	@Test
	void testResumeNoSubscribers() throws IOException {
		UUID did = UUID.randomUUID();
		deltaFileRepo.save(fullFlowExemplarService.postTransformUtf8NoSubscriberDeltaFile(did));

		List<RetryResult> retryResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("resumeNoSubscribers"), did),
				"data." + DgsConstants.MUTATION.Resume,
				new TypeRef<>() {});

		assertEquals(1, retryResults.size());
		assertEquals(did, retryResults.getFirst().getDid());
		assertTrue(retryResults.getFirst().getSuccess());

		DeltaFile expected = fullFlowExemplarService.postResumeNoSubscribersDeltaFile(did);
		verifyActionEventResults(expected, ActionContext.builder().flowName(DATA_SINK_FLOW_NAME).actionName(SAMPLE_EGRESS_ACTION).build());
	}

	@Test
	void testEgress() throws IOException {
		UUID did = UUID.randomUUID();
		deltaFileRepo.save(fullFlowExemplarService.postTransformDeltaFile(did));

		deltaFilesService.handleActionEvent(actionEvent("egress", did));

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertEqualsIgnoringDates(fullFlowExemplarService.postEgressDeltaFile(did), deltaFile);

		Mockito.verify(coreEventQueue, never()).putActions(any(), anyBoolean());

		Map<String, String> tags = tagsFor(ActionEventType.EGRESS, "SampleEgressAction", REST_DATA_SOURCE_NAME, DATA_SINK_FLOW_NAME);
		Map<String, String> flowTags = Map.of(
				"dataSource", REST_DATA_SOURCE_NAME,
				"dataSink", DATA_SINK_FLOW_NAME);

		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.FILES_TO_SINK, 1).addTags(flowTags));
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.BYTES_TO_SINK, 500).addTags(flowTags));

		Mockito.verify(metricService).increment(argThat(metric ->
				metric.getName().equals(DeltaFiConstants.EXECUTION_TIME_MS) &&
						metric.getTags().equals(tags)
		));

		extendTagsForAction(tags, "type");
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.ACTION_EXECUTION, 1).addTags(tags));

		Mockito.verify(metricService).increment(argThat(metric ->
				metric.getName().equals(DeltaFiConstants.ACTION_EXECUTION_TIME_MS) &&
						metric.getTags().equals(tags)
		));

		Mockito.verifyNoMoreInteractions(metricService);

	}

	@Test
	void testSplit() throws IOException {
		UUID did = UUID.randomUUID();
		UUID childOne = UUID.randomUUID();
		UUID childTwo = UUID.randomUUID();
		DeltaFile postUtf8Transform = fullFlowExemplarService.postTransformUtf8DeltaFile(did);
		assertFalse(postUtf8Transform.getWaitingForChildren());
		assertFalse(postUtf8Transform.isContentDeletable());
		deltaFileRepo.save(postUtf8Transform);

		deltaFilesService.handleActionEvent(actionEvent("split", did, childOne, childTwo));

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);
		assertEquals(DeltaFileStage.COMPLETE, deltaFile.getStage());
		assertEquals(2, deltaFile.getChildDids().size());
		assertEquals(ActionState.SPLIT, deltaFile.lastFlow().lastAction().getState());
		assertTrue(deltaFile.getWaitingForChildren());
		assertFalse(postUtf8Transform.isContentDeletable());

		List<DeltaFile> children = deltaFilesService.deltaFiles(0, 50, DeltaFilesFilter.newBuilder().dids(deltaFile.getChildDids()).build(), DeltaFileOrder.newBuilder().field("created").direction(DeltaFileDirection.ASC).build()).getDeltaFiles();
		assertEquals(2, children.size());
		assertEquals(List.of("child1", "child2"), children.stream().map(DeltaFile::getName).sorted().toList());

		DeltaFile child1 = children.stream().filter(d -> d.getName().equals("child1")).findFirst().orElseThrow();
		assertThat(child1.getDid()).isEqualTo(childOne);
		assertEquals(DeltaFileStage.IN_FLIGHT, child1.getStage());
		assertEquals(Map.of("childIndex", "1"), child1.annotationMap());
		assertNull(child1.firstFlow().getTestModeReason());
		assertEquals(Collections.singletonList(deltaFile.getDid()), child1.getParentDids());
		assertEquals(deltaFile.lastFlow().getDepth() + 1, child1.lastFlow().getDepth(), "Split child should inherit parent depth + 1");
		assertEquals(0, child1.firstFlow().lastCompleteAction().orElseThrow().getContent().getFirst().getSegments().getFirst().getOffset());

		DeltaFile child2 = children.stream().filter(d -> d.getName().equals("child2")).findFirst().orElseThrow();
		assertThat(child2.getDid()).isEqualTo(childTwo);
		assertEquals(DeltaFileStage.IN_FLIGHT, child2.getStage());
		assertEquals(Map.of("childIndex", "2"), child2.annotationMap());
		assertNull(child2.firstFlow().getTestModeReason());
		assertEquals(Collections.singletonList(deltaFile.getDid()), child2.getParentDids());
		assertEquals(deltaFile.lastFlow().getDepth() + 1, child2.lastFlow().getDepth(), "Split child should inherit parent depth + 1");
		assertEquals(100, child2.firstFlow().lastCompleteAction().orElseThrow().getContent().getFirst().getSegments().getFirst().getOffset());

		Mockito.verify(coreEventQueue).putActions(actionInputListCaptor.capture(), anyBoolean());
		assertEquals(2, actionInputListCaptor.getValue().size());
		assertEquals(child1.getDid(), actionInputListCaptor.getValue().getFirst().getActionContext().getDid());
		assertEquals(child2.getDid(), actionInputListCaptor.getValue().get(1).getActionContext().getDid());

		assertEquals(Set.of("a", "b"), child1.firstFlow().lastAction().getContent().getLast().getTags());
		assertEquals(Set.of("b", "c"), child2.firstFlow().lastAction().getContent().getLast().getTags());

		Map<String, String> tags = tagsFor(ActionEventType.TRANSFORM, "SampleTransformAction", REST_DATA_SOURCE_NAME, null);

		Map<String, String> flowTags = Map.of(
				"dataSource", REST_DATA_SOURCE_NAME,
				"flowName", TRANSFORM_FLOW_NAME);

		Mockito.verify(metricService, times(2)).increment(new Metric(DeltaFiConstants.FILES_OUT, 1).addTags(flowTags));
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.BYTES_OUT, 100).addTags(flowTags));
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.BYTES_OUT, 400).addTags(flowTags));

		extendTagsForAction(tags, "type");
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.ACTION_EXECUTION_TIME_MS, 1).addTags(tags));
		Mockito.verify(metricService).increment(new Metric(DeltaFiConstants.ACTION_EXECUTION, 1).addTags(tags));
		Mockito.verifyNoMoreInteractions(metricService);
	}

	@Test
	void testCountUnacknowledgedErrors() {
		List<DeltaFile> deltaFiles = new ArrayList<>();
		for (int i = 0; i < 505; i++) {
			DeltaFile deltaFile = utilService.buildDeltaFile(UUID.randomUUID(), List.of());
			deltaFile.firstFlow().getActions().getFirst().setState(ERROR);
			deltaFile.firstFlow().updateState();
			deltaFile.setStage(DeltaFileStage.ERROR);

			if (i >= 501) {
				deltaFile.acknowledgeErrors(OffsetDateTime.now(), "acked");
			}
			deltaFiles.add(deltaFile);
		}
		deltaFileRepo.insertBatch(deltaFiles, 1000);

		assertEquals(505, deltaFileRepo.count());
		assertEquals(501, deltaFilesService.countUnacknowledgedErrors());
	}

	@Test
	void queuesJoiningTransformActionOnMaxNum() {
		TransformFlow transformFlow = joiningTransformFlow("join-max-num", new JoinConfiguration(Duration.parse("PT1H"), null, 2, null));
		RestDataSource restDataSource = buildDataSource(JOIN_TOPIC);
		transformFlowRepo.save(transformFlow);
		restDataSourceRepo.save(restDataSource);
		refreshFlowCaches();

		IngressEventItem ingress1 = new IngressEventItem(UUID.randomUUID(), FILENAME, restDataSource.getName(), null,
				Collections.emptyList(), Collections.emptyMap());
		deltaFilesService.ingressRest(restDataSource, ingress1, OffsetDateTime.now(), OffsetDateTime.now());

		IngressEventItem ingress2 = new IngressEventItem(UUID.randomUUID(), "file-2", restDataSource.getName(), null,
				Collections.emptyList(), Collections.emptyMap());
		deltaFilesService.ingressRest(restDataSource, ingress2, OffsetDateTime.now(), OffsetDateTime.now());

		Mockito.verify(coreEventQueue).putActions(actionInputListCaptor.capture(), Mockito.anyBoolean());
		verifyActionInputs(actionInputListCaptor.getValue(), ingress1.getDid(), ingress2.getDid(), transformFlow.getName());
	}

	private void verifyActionInputs(List<WrappedActionInput> actionInputs, UUID did1, UUID did2, String actionFlow) {
		assertThat(actionInputs).hasSize(1);

		DeltaFile parent1 = deltaFileRepo.findById(did1).orElseThrow();
		Action action = parent1.getFlow(actionFlow).actionNamed(DeltaFiCoreApplicationTests.JOINING_TRANSFORM_ACTION).orElseThrow();
		assertEquals(ActionState.JOINING, action.getState());

		DeltaFile parent2 = deltaFileRepo.findById(did2).orElseThrow();
		action = parent2.getFlow(actionFlow).actionNamed(DeltaFiCoreApplicationTests.JOINING_TRANSFORM_ACTION).orElseThrow();
		assertEquals(ActionState.JOINING, action.getState());

		ActionInput actionInput = actionInputs.getFirst();
		assertEquals(2, actionInput.getDeltaFileMessages().size());
		assertEquals(forQueueHelper(parent1.lastFlow()), actionInput.getDeltaFileMessages().getFirst());
		assertEquals(forQueueHelper(parent2.lastFlow()), actionInput.getDeltaFileMessages().get(1));
	}

	private DeltaFileMessage forQueueHelper(DeltaFile deltaFile, ActionContext actionContext) {
		DeltaFileFlow deltaFileFlow = deltaFile.getFlows().stream().filter(f -> f.getName().equals(actionContext.getFlowName())).findFirst().orElse(null);
        assert deltaFileFlow != null;
        return forQueueHelper(deltaFileFlow);
	}

	private DeltaFileMessage forQueueHelper(DeltaFileFlow deltaFileFlow) {
		return new DeltaFileMessage(deltaFileFlow.getMetadata(), deltaFileFlow.lastContent());
	}

	@Test
	void queuesJoiningTransformActionOnTimeout() {
		TransformFlow transformFlow = joiningTransformFlow("join-on-timeout", new JoinConfiguration(Duration.parse("PT3S"), null, 5, null));
		RestDataSource restDataSource = buildDataSource(JOIN_TOPIC);
		transformFlowRepo.save(transformFlow);
		restDataSourceRepo.save(restDataSource);
		refreshFlowCaches();
		String dataSourceName = restDataSource.getName();

		IngressEventItem ingress1 = new IngressEventItem(UUID.randomUUID(), FILENAME, dataSourceName, null,
				Collections.emptyList(), Collections.emptyMap());
		deltaFilesService.ingressRest(restDataSource, ingress1, OffsetDateTime.now(), OffsetDateTime.now());

		IngressEventItem ingress2 = new IngressEventItem(UUID.randomUUID(), "file-2", dataSourceName, null,
				Collections.emptyList(), Collections.emptyMap());
		deltaFilesService.ingressRest(restDataSource, ingress2, OffsetDateTime.now(), OffsetDateTime.now());

		TEST_CLOCK.setInstant(Instant.now().plusSeconds(4));
		scheduledJoinService.handleTimedOutJoins();

		Mockito.verify(coreEventQueue).putActions(actionInputListCaptor.capture(), Mockito.anyBoolean());

		verifyActionInputs(actionInputListCaptor.getValue(), ingress1.getDid(), ingress2.getDid(), transformFlow.getName());
	}

	@Test
	void queuesJoiningTransformActionOnMaxNumGrouping() {
		TransformFlow transformFlow = joiningTransformFlow("join-max-num-grouping", new JoinConfiguration(Duration.parse("PT1H"), null, 2, "a"));
		RestDataSource restDataSource = buildDataSource(JOIN_TOPIC);
		transformFlow.getFlowStatus().setState(FlowState.RUNNING);
		String dataSourceName = restDataSource.getName();
		String transformFlowName = transformFlow.getName();

		transformFlowRepo.save(transformFlow);
		restDataSourceRepo.save(restDataSource);
		refreshFlowCaches();

		IngressEventItem ingress1 = new IngressEventItem(UUID.randomUUID(),
				FILENAME, dataSourceName, Map.of("a", "1"), Collections.emptyList(), Collections.emptyMap());
		deltaFilesService.ingressRest(restDataSource, ingress1, OffsetDateTime.now(), OffsetDateTime.now());

		IngressEventItem ingress2 = new IngressEventItem(UUID.randomUUID(),
				"file-2", dataSourceName, Map.of("a", "2"), Collections.emptyList(), Collections.emptyMap());
		deltaFilesService.ingressRest(restDataSource, ingress2, OffsetDateTime.now(), OffsetDateTime.now());

		IngressEventItem ingress3 = new IngressEventItem(UUID.randomUUID(),
				"file-3", dataSourceName, Map.of("a", "2"), Collections.emptyList(), Collections.emptyMap());
		deltaFilesService.ingressRest(restDataSource, ingress3, OffsetDateTime.now(), OffsetDateTime.now());

		IngressEventItem ingress4 = new IngressEventItem(UUID.randomUUID(),
				"file-4", dataSourceName, Map.of("a", "1"), Collections.emptyList(), Collections.emptyMap());
		deltaFilesService.ingressRest(restDataSource, ingress4, OffsetDateTime.now(), OffsetDateTime.now());

		Mockito.verify(coreEventQueue, Mockito.times(2))
				.putActions(actionInputListCaptor.capture(), Mockito.anyBoolean());
		List<List<WrappedActionInput>> actionInputLists = actionInputListCaptor.getAllValues();
		verifyActionInputs(actionInputLists.getFirst(), ingress2.getDid(), ingress3.getDid(), transformFlowName);
		verifyActionInputs(actionInputLists.get(1), ingress1.getDid(), ingress4.getDid(), transformFlowName);
	}

	@Test
	void failsJoiningTransformActionOnMinNum() {
		TransformFlow transformFlow = joiningTransformFlow("join-fail-min-num", new JoinConfiguration(Duration.parse("PT3S"), 3, 5, null));
		String transformFlowName = transformFlow.getName();
		RestDataSource restDataSource = buildDataSource(JOIN_TOPIC);
		transformFlowRepo.save(transformFlow);
		restDataSourceRepo.save(restDataSource);
		refreshFlowCaches();
		String dataSourceName = restDataSource.getName();

		IngressEventItem ingress1 = new IngressEventItem(UUID.randomUUID(), FILENAME, dataSourceName, null,
				Collections.emptyList(), Collections.emptyMap());
		deltaFilesService.ingressRest(restDataSource, ingress1, OffsetDateTime.now(), OffsetDateTime.now());

		IngressEventItem ingress2 = new IngressEventItem(UUID.randomUUID(), "file-2", dataSourceName, null,
				Collections.emptyList(), Collections.emptyMap());
		deltaFilesService.ingressRest(restDataSource, ingress2, OffsetDateTime.now(), OffsetDateTime.now());

		TEST_CLOCK.setInstant(Instant.now().plusSeconds(4));
		scheduledJoinService.handleTimedOutJoins();
		assertThat(hasErroredJoiningAction(ingress1.getDid(), transformFlowName)).isTrue();
		assertThat(hasErroredJoiningAction(ingress2.getDid(), transformFlowName)).isTrue();
	}

	private boolean hasErroredJoiningAction(UUID did, String actionFlow) {
		DeltaFile deltaFile = deltaFileRepo.findById(did).orElseThrow();
		if (deltaFile.getStage() != DeltaFileStage.ERROR) {
			return false;
		}
		DeltaFileFlow deltaFileFlow = deltaFile.getFlow(actionFlow);
		if (deltaFileFlow.getState() != DeltaFileFlowState.ERROR) {
			return false;
		}
		Action action = deltaFileFlow.actionNamed(JOINING_TRANSFORM_ACTION).orElseThrow();
		return action.getState() == ActionState.ERROR;
	}

	@Test
	void testResumeAggregate() throws IOException {
		TransformFlow transformFlow = joiningTransformFlow("join-resume", new JoinConfiguration(Duration.parse("PT1H"), null, 2, null));
		transformFlowRepo.save(transformFlow);
		refreshFlowCaches();

		UUID did = UUID.randomUUID();

		DeltaFile parent1 = utilService.emptyDeltaFile(UUID.randomUUID(), transformFlow.getName());
		parent1.firstFlow().setJoinId(did);
		deltaFileRepo.save(parent1);
		DeltaFile parent2 = utilService.emptyDeltaFile(UUID.randomUUID(), transformFlow.getName());
		parent2.firstFlow().setJoinId(did);
		deltaFileRepo.save(parent2);

		List<UUID> parentDids = List.of(parent1.getDid(), parent2.getDid());
		DeltaFile aggregate = utilService.emptyDeltaFile(did, transformFlow.getName());
		aggregate.setJoinId(did);
		aggregate.setParentDids(parentDids);
		aggregate.setStage(DeltaFileStage.ERROR);

		DeltaFileFlow aggregateFlow = aggregate.firstFlow();
		aggregateFlow.setFlowDefinition(flowDefinitionService.getOrCreateFlow(aggregateFlow.getFlowDefinition().getName(), FlowType.TRANSFORM));
		aggregateFlow.queueNewAction(JOINING_TRANSFORM_ACTION, null, ActionType.TRANSFORM, false, OffsetDateTime.now(clock));
		aggregateFlow.actionNamed(JOINING_TRANSFORM_ACTION).orElseThrow().error(START_TIME, STOP_TIME, OffsetDateTime.now(clock), "collect action failed", "message");
		aggregateFlow.setPendingActions(transformFlow.allActionConfigurations().stream().map(ActionConfiguration::getName).toList());
		deltaFileRepo.save(aggregate);

		List<RetryResult> retryResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("resumeAggregate"), did),
				"data." + DgsConstants.MUTATION.Resume,
				new TypeRef<>() {});

		assertEquals(1, retryResults.size());
		assertEquals(did, retryResults.getFirst().getDid());
		assertTrue(retryResults.getFirst().getSuccess());

		Mockito.verify(coreEventQueue).putActions(actionInputListCaptor.capture(), Mockito.anyBoolean());
		List<WrappedActionInput> actionInputs = actionInputListCaptor.getValue();
		assertThat(actionInputs).hasSize(1);

		ActionInput actionInput = actionInputs.getFirst();
		assertEquals(parentDids, actionInput.getActionContext().getJoinedDids());
		assertEquals(forQueueHelper(parent1.firstFlow()), actionInput.getDeltaFileMessages().getFirst());
		assertEquals(forQueueHelper(parent2.firstFlow()), actionInput.getDeltaFileMessages().get(1));
	}

	private TransformFlow joiningTransformFlow(String name, JoinConfiguration configuration) {
		TransformFlow transformFlow = new TransformFlow();
		transformFlow.setName(name);
		ActionConfiguration transformAction = new ActionConfiguration(JOINING_TRANSFORM_ACTION,
				ActionType.TRANSFORM, "org.deltafi.action.SomeJoiningTransformAction");
		transformAction.setJoin(configuration);
		transformFlow.getTransformActions().add(transformAction);
		transformFlow.getFlowStatus().setState(FlowState.RUNNING);
		transformFlow.setSubscribe(Set.of(new Rule(JOIN_TOPIC)));
		return transformFlow;
	}

    @Test
    void findEventsByTime() {
        OffsetDateTime first = OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC)).plusDays(1);
        OffsetDateTime second = first.plusMinutes(5);

        Event event1 = Event.builder().severity("info").summary("first").timestamp(first).build();
        Event event2 = Event.builder().severity("warn").summary("second").timestamp(second).acknowledged(true).build();

        eventRepo.saveAll(List.of(event1, event2));

        List<Event> found = eventRepo.findEvents(eventFilters(first.minusSeconds(1), second.plusSeconds(1)), 0, 20);
        assertThat(found).isEqualTo(List.of(event2, event1));

        found = eventRepo.findEvents(eventFilters(first.minusSeconds(1), second), 0, 20);
        assertThat(found).hasSize(1).containsExactly(event1);

        found = eventRepo.findEvents(eventFilters(first, second.plusSeconds(1)), 0, 20);
        assertThat(found).hasSize(1).containsExactly(event2);

        Map<String, String> filters = new HashMap<>(eventFilters(first.minusSeconds(1), second.plusSeconds(1)));
        filters.put("acknowledged", "true");
        found = eventRepo.findEvents(filters, 0, 20);
        assertThat(found).isEqualTo(List.of(event2));

		found = eventRepo.findEvents(eventFilters(first.minusSeconds(1), second.plusSeconds(1)), 0, 1);
		assertThat(found).isEqualTo(List.of(event2));

		found = eventRepo.findEvents(eventFilters(first.minusSeconds(1), second.plusSeconds(1)), 1, 1000);
		assertThat(found).isEqualTo(List.of(event1));
    }

    private Map<String, String> eventFilters(OffsetDateTime start, OffsetDateTime stop) {
        return Map.of("start", start.format(DateTimeFormatter.ISO_DATE_TIME), "end", stop.format(DateTimeFormatter.ISO_DATE_TIME));
    }

    @Test
    void updateAcknowledged() {
        Event event1 = Event.builder().severity("info").summary("first").timestamp(OffsetDateTime.now(clock)).build();
        Event event2 = Event.builder().severity("warn").summary("second").timestamp(OffsetDateTime.now(clock)).acknowledged(true).build();

        eventRepo.saveAll(List.of(event1, event2));

        Event updated = eventRepo.updateAcknowledged(event1.getId(), true).orElseThrow();
        assertThat(updated.isAcknowledged()).isTrue();

        Event noChange = eventRepo.updateAcknowledged(event2.getId(), true).orElseThrow();
        assertThat(noChange).isEqualTo(event2);

        assertThat(eventRepo.updateAcknowledged(UUID.randomUUID(), true)).isEmpty();
    }

	@Test
	void saveDeltaFileLink() {
		uiLinkRepo.deleteAll();
		assertThat(uiLinkService.saveLink(link())).isNotNull();

		// verify duplicate link (by name/type) is rejected
		assertThatThrownBy(() -> uiLinkService.saveLink(link()))
				.isInstanceOf(ValidationException.class).hasMessageContaining("already exists");
	}

	@Test
	void adminUserCreated() {
		// admin user that should have been created on initialization
		DeltaFiUser admin = userRepo.findById(ADMIN_ID).orElseThrow();

		assertThat(admin.getRoles()).hasSize(1);
		Role adminRole = admin.getRoles().iterator().next();
		assertThat(adminRole.getPermissions()).contains("Admin");

		// mock running create admin again, verify not changes are made
		userRepo.createAdmin(ADMIN_ID, OffsetDateTime.now().plusYears(20));
		DeltaFiUser afterCreate = userRepo.findById(ADMIN_ID).orElseThrow();
		assertThat(afterCreate.getCreatedAt()).isEqualTo(admin.getCreatedAt());
		assertThat(afterCreate.getUpdatedAt()).isEqualTo(admin.getUpdatedAt());

		// note - if user removes the Admin role from the admin it will be put back on reboot
		assertThat(afterCreate.getRoles()).hasSize(1).contains(adminRole);
	}

	@Test
	void removeRole() {
		Role role = Role.builder().id(UUID.randomUUID()).permission("UIAccess").build();
		roleRepo.save(role);

		DeltaFiUserDTO one = userService.createUser(DeltaFiUser.Input.builder().name("one").username("one").roleIds(Set.of(role.getId())).build());
		DeltaFiUserDTO two = userService.createUser(DeltaFiUser.Input.builder().name("two").username("two").roleIds(Set.of(ADMIN_ID, role.getId())).build());

		roleService.deleteRole(role.getId());

		assertThat(userRepo.findById(one.id()).orElseThrow().getRoles()).isEmpty();
		assertThat(userRepo.findById(two.id()).orElseThrow().getRoles()).hasSize(1).doesNotContain(role);
		// cleanup after test
		userRepo.deleteAllById(List.of(one.id(), two.id()));
	}

	@Test
	void removeUser() {
		Role role = Role.builder().id(UUID.randomUUID()).permission("UIAccess").build();
		roleRepo.save(role);

		DeltaFiUserDTO one = userService.createUser(DeltaFiUser.Input.builder().name("one").username("one").roleIds(Set.of(role.getId())).build());

		assertThat(countRolesForUser(one.id())).isEqualTo(1);
		userService.deleteUser(one.id());
		assertThat(countRolesForUser(one.id())).isZero();

		// cleanup after the test
		userRepo.deleteById(one.id());
		roleRepo.deleteById(role.getId());
	}

	private int countRolesForUser(UUID id) {
		return jdbcTemplate.queryForObject("select count(*) from user_roles where user_id = ?", Integer.class, id);
	}

	@Test
	void checkDuplicateUserFields() {
		DeltaFiUser bob = DeltaFiUser.builder().id(UUID.randomUUID()).name("bob").dn("CN=bob").username("bob").build();
		DeltaFiUser jane = DeltaFiUser.builder().id(UUID.randomUUID()).name("jane").dn("CN=jane").username("jane").build();
		userRepo.saveAll(List.of(bob, jane));

		assertThat(userRepo.existsByName("bob")).isTrue();
		assertThat(userRepo.existsByIdNotAndName(jane.getId(), "bob")).isTrue();
		assertThat(userRepo.existsByIdNotAndName(bob.getId(), "bob")).isFalse();

		assertThat(userRepo.existsByDn("CN=bob")).isTrue();
		assertThat(userRepo.existsByIdNotAndDn(jane.getId(), "CN=bob")).isTrue();
		assertThat(userRepo.existsByIdNotAndDn(bob.getId(), "CN=bob")).isFalse();

		assertThat(userRepo.existsByUsername("bob")).isTrue();
		assertThat(userRepo.existsByIdNotAndUsername(jane.getId(), "bob")).isTrue();
		assertThat(userRepo.existsByIdNotAndUsername(bob.getId(), "bob")).isFalse();
	}

	@Test
	void rejectInvalidRole() {
		DeltaFiUser bob = DeltaFiUser.builder().id(UUID.randomUUID()).name("name").username("uname").build();
		userRepo.save(bob);

		DeltaFiUser.Input update = DeltaFiUser.Input.builder().roleIds(Set.of(ADMIN_ID, UUID.randomUUID())).build();
		UUID id = bob.getId();
		assertThatThrownBy(() -> userService.updateUser(id, update))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessage("One or more role ids were not found");
	}

	@Test
	void checkDuplicateRoleFields() {
		Role a = Role.builder().id(UUID.randomUUID()).name("a").permission("UIAccess").build();
		Role b = Role.builder().id(UUID.randomUUID()).name("b").permission("UIAccess").build();
		roleRepo.saveAll(List.of(a, b));

		assertThat(roleRepo.existsByName("a")).isTrue();
		assertThat(roleRepo.existsByIdNotAndName(b.getId(), "a")).isTrue();
		assertThat(roleRepo.existsByIdNotAndName(a.getId(), "a")).isFalse();
	}

	@Test
	void rolesCreated() {
		List<Role> roles = roleRepo.findAll();
		assertThat(roles).hasSizeGreaterThanOrEqualTo(3);
		assertThat(roleRepo.existsById(ADMIN_ID)).isTrue();
		assertThat(roles.stream().anyMatch(r -> "Admin".equals(r.getName()))).isTrue();
		assertThat(roles.stream().anyMatch(r -> "Ingress Only".equals(r.getName()))).isTrue();
		assertThat(roles.stream().anyMatch(r -> "Read Only".equals(r.getName()))).isTrue();
	}

	@Test
	void testUpdateSystemPluginFlowVersions() {
		TransformFlow transformFlow = new TransformFlow();
		transformFlow.setName("abc");
		transformFlow.setSourcePlugin(pluginService.getSystemPluginCoordinates());
		transformFlowRepo.save(transformFlow);
		transformFlowRepo.updateSystemPluginFlowVersions("blah", FlowType.TRANSFORM);

		TransformFlow after = transformFlowRepo.findByNameAndType("abc", FlowType.TRANSFORM, TransformFlow.class).orElseThrow();
		PluginCoordinates expected = pluginService.getSystemPluginCoordinates();
		expected.setVersion("blah");
		assertThat(after.getSourcePlugin()).isEqualTo(expected);
		transformFlowRepo.deleteById(after.getId());
	}

	@Test
	void testUpdateSystemPluginVersions() {
		pluginService.doUpdateSystemPlugin();
		assertEquals(1, pluginRepository.findAll().stream()
				.filter(p -> p.getPluginCoordinates().getArtifactId().equals(SYSTEM_PLUGIN_ARTIFACT_ID))
				.toList().size());
		PluginEntity systemPlugin = pluginService.getSystemPlugin();
		systemPlugin.setVersion("0.0");
		pluginRepository.save(systemPlugin);
		pluginService.doUpdateSystemPlugin();
		assertEquals(1, pluginRepository.findAll().stream()
				.filter(p -> p.getPluginCoordinates().getArtifactId().equals(SYSTEM_PLUGIN_ARTIFACT_ID))
				.toList().size());
	}

	@Test
	void testUpdateFlowStatusState() {
		TransformFlow transformFlow = new TransformFlow();
		transformFlow.setName("abc");
		transformFlow.setSourcePlugin(pluginService.getSystemPluginCoordinates());
		transformFlow.setFlowStatus(FlowStatus.newBuilder().state(FlowState.PAUSED).build());
		transformFlowRepo.save(transformFlow);
		transformFlowRepo.updateFlowStatusState("abc", FlowState.RUNNING, FlowType.TRANSFORM);

		TransformFlow after = transformFlowRepo.findByNameAndType("abc", FlowType.TRANSFORM, TransformFlow.class).orElseThrow();
		assertThat(after.isRunning()).isTrue();
		transformFlowRepo.deleteById(after.getId());
	}

	@Test
	void testUpdateFlowStatusTestMode() {
		TransformFlow transformFlow = new TransformFlow();
		transformFlow.setName("abc");
		transformFlow.setSourcePlugin(pluginService.getSystemPluginCoordinates());
		transformFlow.setFlowStatus(FlowStatus.newBuilder().state(FlowState.PAUSED).testMode(false).build());
		transformFlowRepo.save(transformFlow);
		transformFlowRepo.updateFlowStatusTestMode("abc", true, FlowType.TRANSFORM);

		TransformFlow after = transformFlowRepo.findByNameAndType("abc", FlowType.TRANSFORM, TransformFlow.class).orElseThrow();
		assertThat(after.isTestMode()).isTrue();
		transformFlowRepo.deleteById(after.getId());
	}

	@Test
	void testFindRunningBySourcePlugin() {
		PluginCoordinates plugin1 = new PluginCoordinates("group", "art-a",  "1");
		PluginCoordinates plugin2 = new PluginCoordinates("group", "art-b",  "1");

		TransformFlow plugin1Running1 = new TransformFlow();
		plugin1Running1.setSourcePlugin(plugin1);
		plugin1Running1.setName("p1Running1");
		plugin1Running1.setFlowStatus(FlowStatus.newBuilder().state(FlowState.RUNNING).build());
		plugin1Running1 = transformFlowRepo.save(plugin1Running1);

		TransformFlow plugin1Running2 = new TransformFlow();
		plugin1Running2.setSourcePlugin(plugin1);
		plugin1Running2.setName("p1Running2");
		plugin1Running2.setFlowStatus(FlowStatus.newBuilder().state(FlowState.RUNNING).build());
		plugin1Running2 = transformFlowRepo.save(plugin1Running2);

		TransformFlow plugin1Stopped = new TransformFlow();
		plugin1Stopped.setName("p1Stopped");
		plugin1Stopped.setSourcePlugin(plugin1);
		plugin1Stopped.setFlowStatus(FlowStatus.newBuilder().state(FlowState.STOPPED).build());
		plugin1Stopped = transformFlowRepo.save(plugin1Stopped);

		TransformFlow plugin2Running = new TransformFlow();
		plugin2Running.setSourcePlugin(plugin2);
		plugin2Running.setName("p2Running");
		plugin2Running.setFlowStatus(FlowStatus.newBuilder().state(FlowState.RUNNING).build());
		plugin2Running = transformFlowRepo.save(plugin2Running);

		assertThat(transformFlowRepo.findRunningBySourcePlugin("group", "art-a", "1", FlowType.TRANSFORM))
				.isEqualTo(List.of("p1Running1", "p1Running2"));

		assertThat(transformFlowRepo.findRunningBySourcePlugin("group", "art-b", "1", FlowType.TRANSFORM))
				.isEqualTo(List.of("p2Running"));

		transformFlowRepo.deleteAllById(Stream.of(plugin1Running1, plugin1Running2, plugin1Stopped, plugin2Running)
						.map(Flow::getId).toList());
	}

	@Test
	void testUserRolesSoftReset() {
		Role unchangedRole = role("noChange", "UIAccess", "StatusView");
		Role roleToReplace = role("replace", "StatusView", "SurveyCreate");
		roleRepo.saveAll(List.of(unchangedRole, roleToReplace));

		DeltaFiUser unchangedUser = user("noChange", unchangedRole);
		DeltaFiUser userToChangeRoles = user("changeRoles", unchangedRole, roleToReplace);
		DeltaFiUser userToReplace = user("replace", unchangedRole, roleToReplace);
		userRepo.saveAll(List.of(unchangedUser, userToChangeRoles, userToReplace));

		RoleSnapshot snapReplacementRole = new RoleSnapshot(role("replace", "DeltaFileMetadataView"));
		RoleSnapshot snapshotOnlyRole = new RoleSnapshot(role("fromSnap", "Admin", "OldPermission"));

		UserSnapshot snapshotReplacementUser = new UserSnapshot(userToReplace);
		snapshotReplacementUser.setName("changed");
		snapshotReplacementUser.setRoles(Set.of(snapshotOnlyRole));
		UserSnapshot snapshotOnlyUser = new UserSnapshot(user("snap", snapshotOnlyRole.toRole()));

		Snapshot snapshot = new Snapshot();
		snapshot.setRoles(List.of(snapReplacementRole, snapshotOnlyRole));
		snapshot.setUsers(List.of(snapshotReplacementUser, snapshotOnlyUser));

		userService.resetFromSnapshot(snapshot, false);

		assertThat(userRepo.findById(unchangedUser.getId()).orElseThrow()).isEqualTo(unchangedUser);

		Set<String> changePermissions = new DeltaFiUserDTO(userRepo.findById(userToChangeRoles.getId()).orElseThrow()).permissions();
		assertThat(changePermissions).contains("UIAccess", "StatusView", "DeltaFileMetadataView");

		DeltaFiUser replacedUser = userRepo.findById(userToReplace.getId()).orElseThrow();
		assertThat(replacedUser.getUsername()).isEqualTo(userToReplace.getUsername());
		assertThat(replacedUser.getName()).isEqualTo("changed");
		assertThat(replacedUser.getRoles()).hasSize(1);
		assertThat(replacedUser.getRoles().iterator().next().getPermissions()).hasSize(1).contains("Admin");

		assertThat(roleRepo.findById(unchangedRole.getId()).orElseThrow()).isEqualTo(unchangedRole);
		assertThat(roleRepo.findById(roleToReplace.getId()).orElseThrow().getPermissions()).isEqualTo(snapReplacementRole.toRole().getPermissions());
		// new role was added from the snapshot, and the invalid permission was pruned
		assertThat(roleRepo.findById(snapshotOnlyRole.getId()).orElseThrow().getPermissions()).hasSize(1).contains("Admin");
	}

	private Role role(String name, String ... permissions) {
		Role role = new Role(new Role.Input(name, List.of(permissions)));
		role.setUpdatedAt(NOW);
		role.setCreatedAt(NOW);
		return role;
	}

	private DeltaFiUser user(String name, Role ... roles) {
		DeltaFiUser user = new DeltaFiUser(DeltaFiUser.Input.builder().name(name).username(name).build());
		user.setRoles(Set.of(roles));
		user.setCreatedAt(NOW);
		user.setUpdatedAt(NOW);
		return user;
	}

	Link link() {
		Link link = new Link();
		link.setName("a");
		link.setDescription("description");
		link.setUrl("google.com");
		link.setLinkType(LinkType.EXTERNAL);
		return link;
	}

	@Test
	void testCompleteParents() {
		DeltaFile parent = utilService.buildDeltaFile(UUID.randomUUID(), "flow1", DeltaFileStage.COMPLETE, NOW, NOW);
		parent.setWaitingForChildren(true);
		parent.setContentDeletable(false);
		parent.setTerminal(false);

		DeltaFile terminalChild = utilService.buildDeltaFile(UUID.randomUUID(), "flow2", DeltaFileStage.COMPLETE, NOW, NOW);
		terminalChild.setTerminal(true);

		parent.setChildDids(List.of(terminalChild.getDid()));
		deltaFileRepo.insertBatch(List.of(parent, terminalChild), 1000);

		assertThat(deltaFilesService.completeParents()).isEqualTo(1);

		DeltaFile updated = deltaFileRepo.findById(parent.getDid()).orElseThrow();
		assertThat(updated.getWaitingForChildren()).isFalse();
		assertThat(updated.isContentDeletable()).isTrue();
		assertThat(updated.isTerminal()).isTrue();
	}

	@Test
	void testCompleteParentsWithPendingAnnotations() {
		DeltaFile parent = utilService.buildDeltaFile(UUID.randomUUID(), "flow1", DeltaFileStage.COMPLETE, NOW, NOW);
		parent.getFlow("flow1").setPendingAnnotations(Set.of("abc"));
		parent.setWaitingForChildren(true);
		parent.setTerminal(false);

		DeltaFile terminalChild = utilService.buildDeltaFile(UUID.randomUUID(), "flow2", DeltaFileStage.COMPLETE, NOW, NOW);
		terminalChild.setTerminal(true);

		parent.setChildDids(List.of(terminalChild.getDid()));
		deltaFileRepo.insertBatch(List.of(parent, terminalChild), 1000);

		assertThat(deltaFilesService.completeParents()).isEqualTo(1);

		DeltaFile updated = deltaFileRepo.findById(parent.getDid()).orElseThrow();
		assertThat(updated.getWaitingForChildren()).isFalse();
		assertThat(updated.isContentDeletable()).isFalse();
		assertThat(updated.isTerminal()).isFalse();
	}

	@Test
	void testCompleteParentsNotReadyToComplete() {
		DeltaFile parent = utilService.buildDeltaFile(UUID.randomUUID(), "flow1", DeltaFileStage.COMPLETE, NOW, NOW);
		parent.setWaitingForChildren(true);
		parent.setContentDeletable(false);
		parent.setTerminal(false);

		DeltaFile nonTerminalChild = utilService.buildDeltaFile(UUID.randomUUID(), "flow2", DeltaFileStage.IN_FLIGHT, NOW, NOW);
		nonTerminalChild.setTerminal(false);

		parent.setChildDids(List.of(nonTerminalChild.getDid()));
		deltaFileRepo.insertBatch(List.of(parent, nonTerminalChild), 1000);

		assertThat(deltaFilesService.completeParents()).isEqualTo(0);

		DeltaFile unchanged = deltaFileRepo.findById(parent.getDid()).orElseThrow();
		assertThat(unchanged.getWaitingForChildren()).isTrue();
		assertThat(unchanged.isContentDeletable()).isFalse();
		assertThat(unchanged.isTerminal()).isFalse();
	}

	@Test
	void testAnyColdQueued() {
		String actionClass = "org.plugin.SlowAction";
		DeltaFile coldQueued = fullFlowExemplarService.postIngressDeltaFile(UUID.randomUUID());
		coldQueued.lastFlow().lastAction().setActionClass(actionClass);
		coldQueued.lastFlow().lastAction().setState(ActionState.COLD_QUEUED);
		coldQueued.lastFlow().setColdQueued(true);
        coldQueued.lastFlow().setColdQueuedAction(actionClass);

		DeltaFile paused = fullFlowExemplarService.postIngressDeltaFile(UUID.randomUUID());
		paused.lastFlow().lastAction().setActionClass(actionClass);
		paused.lastFlow().setId(UUID.randomUUID());
		paused.lastFlow().lastAction().setState(COMPLETE);
		paused.lastFlow().setState(DeltaFileFlowState.PAUSED);

		deltaFileRepo.insertOne(paused);

		// inflight exists but it is not cold queued
		assertThat(deltaFileFlowRepo.isColdQueued(actionClass)).isFalse();

		// add new DeltaFile that is cold queued
		deltaFileRepo.insertOne(coldQueued);
		assertThat(deltaFileFlowRepo.isColdQueued(actionClass)).isTrue();

		// action name not found
		assertThat(deltaFileFlowRepo.isColdQueued(actionClass+"1")).isFalse();
	}

	@Test
	void testRestDataSourceRateLimitSetAndRemove() {
		// Create a REST data source
		RestDataSource dataSource = buildRestDataSource("rate-limit-test", FlowState.RUNNING);
		dataSource.setRateLimit(null);
		restDataSourceRepo.save(dataSource);
		refreshFlowCaches();

		// Test setting a rate limit via GraphQL
		RateLimitInput rateLimitInput = RateLimitInput.newBuilder()
				.unit(RateLimitUnit.FILES)
				.maxAmount(100L)
				.durationSeconds(60)
				.build();

		SetRestDataSourceRateLimitGraphQLQuery mutation = SetRestDataSourceRateLimitGraphQLQuery.newRequest()
				.name("rate-limit-test")
				.rateLimit(rateLimitInput)
				.build();

		Boolean result = dgsQueryExecutor.executeAndExtractJsonPath(
				new GraphQLQueryRequest(mutation).serialize(),
				"data.setRestDataSourceRateLimit"
		);

		assertThat(result).isTrue();

		// Verify rate limit was set in database
		RestDataSource updated = restDataSourceRepo.findByNameAndType("rate-limit-test", FlowType.REST_DATA_SOURCE, RestDataSource.class)
				.orElseThrow(() -> new AssertionError("Data source not found"));
		assertThat(updated.getRateLimit()).isNotNull();
		assertThat(updated.getRateLimit().getUnit()).isEqualTo(RateLimitUnit.FILES);
		assertThat(updated.getRateLimit().getMaxAmount()).isEqualTo(100L);
		assertThat(updated.getRateLimit().getDurationSeconds()).isEqualTo(60);

		// Test removing rate limit via GraphQL
		RemoveRestDataSourceRateLimitGraphQLQuery removeMutation = RemoveRestDataSourceRateLimitGraphQLQuery.newRequest()
				.name("rate-limit-test")
				.build();

		Boolean removeResult = dgsQueryExecutor.executeAndExtractJsonPath(
				new GraphQLQueryRequest(removeMutation).serialize(),
				"data.removeRestDataSourceRateLimit"
		);

		assertThat(removeResult).isTrue();

		// Verify rate limit was removed from database
		RestDataSource afterRemove = restDataSourceRepo.findByNameAndType("rate-limit-test", FlowType.REST_DATA_SOURCE, RestDataSource.class)
				.orElseThrow(() -> new AssertionError("Data source not found"));
		assertThat(afterRemove.getRateLimit()).isNull();
	}

	@Test
	void testCleanUnusedAnnotationsReturnsCorrectCounts() {
		IntStream.range(0, 75).forEach(i -> {
			UUID did = UUID.randomUUID();
            eventAnnotationsRepo.save(new EventAnnotationEntity(new EventAnnotationId(did, i), i));
		});

		assertEquals(75, eventAnnotationsRepo.count());
		assertEquals(50, eventAnnotationsRepo.deleteUnusedEventAnnotations(50));
		assertEquals(25, eventAnnotationsRepo.count());
		assertEquals(25, eventAnnotationsRepo.deleteUnusedEventAnnotations(50));
		assertEquals(0, eventAnnotationsRepo.count());
	}

	@Autowired
	LookupTableService lookupTableService;

	@Autowired
	MemberMonitorService memberMonitorService;

	@Test
	public void testLookupTableService() throws LookupTableServiceException {
		LookupTable lookupTable = LookupTable.builder()
				.name("test_lookup_1")
				.columns(List.of("column_a", "column_b", "column_c"))
				.keyColumns(List.of("column_a"))
				.serviceBacked(false)
				.backingServiceActive(false)
				.build();
		lookupTableService.createLookupTable(lookupTable, true);

		lookupTableService.upsertRows(lookupTable.getName(), List.of(Map.of("column_a", "Column A Value 1", "column_b", "Column B Value 1", "column_c", "Column C Value 1")));
		lookupTableService.upsertRows(lookupTable.getName(), List.of(Map.of("column_a", "Column A Value 2", "column_b", "Column B Value 2")));
		lookupTableService.upsertRows(lookupTable.getName(), List.of(Map.of("column_a", "Column A Value 3", "column_c", "Column C Value 3")));

		Pair<Integer, List<Map<String, String>>> results = lookupTableService.lookup(lookupTable.getName(),
				Map.of("column_c", Set.of("Column C Value 1")), null, null, null, null, null);
		assertThat(results.getRight().getFirst().get("column_a")).isEqualTo("Column A Value 1");
		assertThat(results.getRight().getFirst().get("column_b")).isEqualTo("Column B Value 1");
		assertThat(results.getRight().getFirst().get("column_c")).isEqualTo("Column C Value 1");

		results = lookupTableService.lookup(lookupTable.getName(),
				Map.of("column_c", Set.of("Column C Value 1")), List.of("column_a", "column_c"), null, null, null, null);
		assertThat(results.getRight().getFirst().get("column_a")).isEqualTo("Column A Value 1");
		assertThat(results.getRight().getFirst().get("column_b")).isNull();
		assertThat(results.getRight().getFirst().get("column_c")).isEqualTo("Column C Value 1");

		lookupTableService.upsertRows(lookupTable.getName(), List.of(Map.of("column_a", "Column A Value 1", "column_c", "Column C Value 4")));

		results = lookupTableService.lookup(lookupTable.getName(),
				Map.of("column_a", Set.of("Column A Value 1")), null, null, null, null, null);
		assertThat(results.getRight().getFirst().get("column_a")).isEqualTo("Column A Value 1");
		assertThat(results.getRight().getFirst().get("column_b")).isNull();
		assertThat(results.getRight().getFirst().get("column_c")).isEqualTo("Column C Value 4");

		lookupTableService.removeRows(lookupTable.getName(), List.of(Map.of("column_a", "Column A Value 1"),
				Map.of("column_a", "Column A Value 3")));
		results = lookupTableService.lookup(lookupTable.getName(), null, null, null, null, null, null);
		assertThat(results.getRight().size()).isEqualTo(1);
		assertThat(results.getRight().getFirst().get("column_a")).isEqualTo("Column A Value 2");

		lookupTableService.deleteLookupTable(lookupTable.getName());

		assertThat(lookupTableService.getLookupTables()).isEmpty();
	}

	@Test
	void testDeltaFiPropertiesHasMembersWithEmptyConfig() {
		DeltaFiProperties properties = new DeltaFiProperties();
		properties.setLeaderConfig("{}");

		assertThat(properties.hasMembers()).isFalse();
		assertThat(properties.getMemberConfigs()).isEmpty();
	}

	@Test
	void testDeltaFiPropertiesGetMemberConfigsWithSingleMember() {
		DeltaFiProperties properties = new DeltaFiProperties();
		String config = """
			{
			  "site1": {
				"url": "https://site1.example.com",
				"tags": ["east", "production"]
			  }
			}
			""";
		properties.setLeaderConfig(config);

		assertThat(properties.hasMembers()).isTrue();
		assertThat(properties.getMemberConfigs()).hasSize(1);
		assertThat(properties.getMemberConfigs().get(0).name()).isEqualTo("site1");
		assertThat(properties.getMemberConfigs().get(0).url()).isEqualTo("https://site1.example.com");
		assertThat(properties.getMemberConfigs().get(0).tags()).containsExactly("east", "production");
	}

	@Test
	void testDeltaFiPropertiesGetMemberConfigsWithCredentials() {
		DeltaFiProperties properties = new DeltaFiProperties();
		String config = """
			{
			  "dev": {
				"url": "https://dev.deltafi.org",
				"tags": ["development"],
				"credentials": {
				  "type": "basic",
				  "username": "admin",
				  "passwordEnvVar": "DEV_PASSWORD"
				}
			  }
			}
			""";
		properties.setLeaderConfig(config);

		assertThat(properties.hasMembers()).isTrue();
		assertThat(properties.getMemberConfigs()).hasSize(1);
		assertThat(properties.getMemberConfigs().get(0).credentials()).isNotNull();
		assertThat(properties.getMemberConfigs().get(0).credentials().type()).isEqualTo("basic");
		assertThat(properties.getMemberConfigs().get(0).credentials().username()).isEqualTo("admin");
		assertThat(properties.getMemberConfigs().get(0).credentials().passwordEnvVar()).isEqualTo("DEV_PASSWORD");
	}

	@Test
	void testDeltaFiPropertiesSetLeaderConfigClearsCachedValue() {
		DeltaFiProperties properties = new DeltaFiProperties();
		properties.setLeaderConfig("{\"site1\": {\"url\": \"https://site1.example.com\"}}");

		var members1 = properties.getMemberConfigs();
		assertThat(members1).hasSize(1);
		assertThat(members1.get(0).name()).isEqualTo("site1");

		properties.setLeaderConfig("{\"site2\": {\"url\": \"https://site2.example.com\"}}");

		var members2 = properties.getMemberConfigs();
		assertThat(members2).hasSize(1);
		assertThat(members2.get(0).name()).isEqualTo("site2");
		assertThat(members1).isNotSameAs(members2);
	}

	@Test
	void testLeaderConfigValidationRejectsMissingUrl() {
		DeltaFiProperties properties = new DeltaFiProperties();
		String config = """
			{
			  "site1": {
				"tags": ["east"]
			  }
			}
			""";
		assertThatThrownBy(() -> properties.setLeaderConfig(config))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("url is required");
	}

	@Test
	void testLeaderConfigValidationRejectsInvalidTags() {
		DeltaFiProperties properties = new DeltaFiProperties();
		String config = """
			{
			  "site1": {
				"url": "https://site1.example.com",
				"tags": "not-a-list"
			  }
			}
			""";
		assertThatThrownBy(() -> properties.setLeaderConfig(config))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("tags must be a list");
	}

	@Test
	void testLeaderConfigValidationRejectsInvalidCredentialsType() {
		DeltaFiProperties properties = new DeltaFiProperties();
		String config = """
			{
			  "site1": {
				"url": "https://site1.example.com",
				"credentials": {
				  "type": "oauth",
				  "username": "admin",
				  "passwordEnvVar": "PASSWORD"
				}
			  }
			}
			""";
		assertThatThrownBy(() -> properties.setLeaderConfig(config))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("credentials.type must be 'basic'");
	}

	@Test
	void testLeaderConfigValidationRejectsMissingCredentialsUsername() {
		DeltaFiProperties properties = new DeltaFiProperties();
		String config = """
			{
			  "site1": {
				"url": "https://site1.example.com",
				"credentials": {
				  "type": "basic",
				  "passwordEnvVar": "PASSWORD"
				}
			  }
			}
			""";
		assertThatThrownBy(() -> properties.setLeaderConfig(config))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("credentials.username is required");
	}

	@Test
	void testLeaderConfigValidationRejectsMissingPasswordEnvVar() {
		DeltaFiProperties properties = new DeltaFiProperties();
		String config = """
			{
			  "site1": {
				"url": "https://site1.example.com",
				"credentials": {
				  "type": "basic",
				  "username": "admin"
				}
			  }
			}
			""";
		assertThatThrownBy(() -> properties.setLeaderConfig(config))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("credentials.passwordEnvVar is required");
	}

	@Test
	void testLeaderConfigValidationRejectsInvalidJson() {
		DeltaFiProperties properties = new DeltaFiProperties();
		assertThatThrownBy(() -> properties.setLeaderConfig("not valid json"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Invalid leaderConfig JSON");
	}

	@Test
	void testMemberMonitorServiceGetLeaderStatus() {
		com.fasterxml.jackson.databind.node.ObjectNode statusNode = OBJECT_MAPPER.createObjectNode();
		statusNode.put("state", "Healthy");
		statusNode.put("code", 0);
		statusNode.putArray("checks");

		Mockito.when(systemService.systemStatus()).thenReturn(new SystemService.Status(statusNode));

		// Mock node metrics with cpu, memory, and disk data
		org.deltafi.core.types.NodeMetrics nodeMetrics = new org.deltafi.core.types.NodeMetrics("test-node");
		nodeMetrics.addMetric("cpu", "usage", 500L);
		nodeMetrics.addMetric("cpu", "limit", 1000L);
		nodeMetrics.addMetric("memory", "usage", 1024L);
		nodeMetrics.addMetric("memory", "limit", 2048L);
		nodeMetrics.addMetric("disk", "usage", 50000L);
		nodeMetrics.addMetric("disk", "limit", 100000L);
		Mockito.when(systemService.nodeAppsAndMetrics()).thenReturn(java.util.List.of(nodeMetrics));

		var leaderStatus = memberMonitorService.getLeaderStatus();

		assertThat(leaderStatus).isNotNull();
		assertThat(leaderStatus.memberName()).isEqualTo("Leader");
		assertThat(leaderStatus.url()).isEqualTo("local");
		assertThat(leaderStatus.tags()).contains("leader");
		assertThat(leaderStatus.isLeader()).isTrue();
		assertThat(leaderStatus.connectionState()).isEqualTo(org.deltafi.core.types.leader.ConnectionState.CONNECTED);
		assertThat(leaderStatus.status()).isNotNull();
		assertThat(leaderStatus.errorCount()).isNotNull();
		assertThat(leaderStatus.inFlightCount()).isNotNull();
		assertThat(leaderStatus.warmQueuedCount()).isNotNull();
		assertThat(leaderStatus.coldQueuedCount()).isNotNull();
		// System metrics should be non-null (values may vary by environment)
		assertThat(leaderStatus.cpuUsage()).isNotNull();
		assertThat(leaderStatus.memoryUsage()).isNotNull();
		assertThat(leaderStatus.diskUsage()).isNotNull();
	}

	@Test
	void testMemberMonitorServiceFormatHttpError() throws Exception {
		var formatHttpError = org.deltafi.core.services.MemberMonitorService.class
				.getDeclaredMethod("formatHttpError", int.class, String.class);
		formatHttpError.setAccessible(true);

		assertThat(formatHttpError.invoke(memberMonitorService, 401, "https://example.com"))
				.isEqualTo("HTTP 401: Authentication required (https://example.com)");
		assertThat(formatHttpError.invoke(memberMonitorService, 404, "https://example.com"))
				.isEqualTo("HTTP 404: Endpoint not found (https://example.com)");
		assertThat(formatHttpError.invoke(memberMonitorService, 500, "https://example.com"))
				.isEqualTo("HTTP 500: Internal server error (https://example.com)");
		assertThat(formatHttpError.invoke(memberMonitorService, 999, "https://example.com"))
				.isEqualTo("HTTP 999: HTTP error (https://example.com)");
	}

	@Test
	void testMemberMonitorServiceFormatException() throws Exception {
		var formatException = org.deltafi.core.services.MemberMonitorService.class
				.getDeclaredMethod("formatException", Exception.class);
		formatException.setAccessible(true);

		Exception connectionRefused = new Exception("Connection refused");
		assertThat(formatException.invoke(memberMonitorService, connectionRefused))
				.isEqualTo("Connection refused");

		Exception timeout = new Exception("Connection timed out");
		assertThat(formatException.invoke(memberMonitorService, timeout))
				.isEqualTo("Connection timed out");

		Exception unknownHost = new Exception("unknown host");
		assertThat(formatException.invoke(memberMonitorService, unknownHost))
				.isEqualTo("Host not found");

		Exception alreadyFormatted = new Exception("HTTP 401: Authentication required (https://example.com)");
		assertThat(formatException.invoke(memberMonitorService, alreadyFormatted))
				.isEqualTo("HTTP 401: Authentication required (https://example.com)");
	}

	@Test
	void testLeaderRestEndpoint() {
		com.fasterxml.jackson.databind.node.ObjectNode statusNode = OBJECT_MAPPER.createObjectNode();
		statusNode.put("state", "Healthy");
		statusNode.put("code", 0);
		statusNode.putArray("checks");

		Mockito.when(systemService.systemStatus()).thenReturn(new SystemService.Status(statusNode));

		HttpHeaders headers = new HttpHeaders();
		headers.add(USER_NAME_HEADER, USERNAME);
		HttpEntity<String> request = new HttpEntity<>(headers);

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v2/leader/members",
				HttpMethod.GET,
				request,
				String.class
		);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody()).contains("\"members\"");
		assertThat(response.getBody()).contains("\"memberName\":\"Leader\"");
	}

	@Test
	void testLeaderStatsEndpoint() {
		com.fasterxml.jackson.databind.node.ObjectNode statusNode = OBJECT_MAPPER.createObjectNode();
		statusNode.put("state", "Healthy");
		statusNode.put("code", 0);
		statusNode.putArray("checks");

		Mockito.when(systemService.systemStatus()).thenReturn(new SystemService.Status(statusNode));

		HttpHeaders headers = new HttpHeaders();
		headers.add(USER_NAME_HEADER, USERNAME);
		HttpEntity<String> request = new HttpEntity<>(headers);

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v2/leader/stats",
				HttpMethod.GET,
				request,
				String.class
		);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody()).contains("\"totalInFlight\"");
		assertThat(response.getBody()).contains("\"totalErrors\"");
		assertThat(response.getBody()).contains("\"totalWarmQueue\"");
		assertThat(response.getBody()).contains("\"totalColdQueue\"");
		assertThat(response.getBody()).contains("\"memberCount\"");
		assertThat(response.getBody()).contains("\"healthyCount\"");
		assertThat(response.getBody()).contains("\"unhealthyCount\"");
	}

	@Test
	void testAggregatedStatsCalculation() {
		com.fasterxml.jackson.databind.node.ObjectNode statusNode = OBJECT_MAPPER.createObjectNode();
		statusNode.put("state", "Healthy");
		statusNode.put("code", 0);
		statusNode.putArray("checks");

		Mockito.when(systemService.systemStatus()).thenReturn(new SystemService.Status(statusNode));

		var stats = memberMonitorService.getAggregatedStats();

		assertThat(stats).isNotNull();
		assertThat(stats.memberCount()).isGreaterThanOrEqualTo(1);
		assertThat(stats.healthyCount()).isGreaterThanOrEqualTo(0);
		assertThat(stats.unhealthyCount()).isGreaterThanOrEqualTo(0);
		assertThat(stats.totalInFlight()).isGreaterThanOrEqualTo(0);
		assertThat(stats.totalErrors()).isGreaterThanOrEqualTo(0);
		assertThat(stats.totalWarmQueue()).isGreaterThanOrEqualTo(0);
		assertThat(stats.totalColdQueue()).isGreaterThanOrEqualTo(0);
	}

	@Test
	void testUiConfigLeaderFlag() {
		HttpHeaders headers = new HttpHeaders();
		headers.add(USER_NAME_HEADER, USERNAME);
		HttpEntity<String> request = new HttpEntity<>(headers);

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v2/config",
				HttpMethod.GET,
				request,
				String.class
		);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody()).contains("\"leader\"");
	}

	@Test
	void testSetStateByTagFilter() {
		setupFlowTagTestData();

		FlowTagFilter filter = FlowTagFilter.builder()
			.all(Set.of("a", "b"))
			.any(Set.of("c", "d"))
			.none(Set.of("skip"))
			.types(Set.of(FlowType.REST_DATA_SOURCE, FlowType.TRANSFORM))
			.build();

		List<Flow> updated = unifiedFlowService.setFlowStateByTags(filter, FlowState.STOPPED);

		assertThat(updated).hasSize(2);
		assertThat(updated).extracting(Flow::getName)
			.containsExactlyInAnyOrder("dataSourceMatch", "transformMatch");
	}

	@Test
	void testFindByTagsAndNewState() {
		setupFlowTagTestData();

		FlowTagFilter filter = FlowTagFilter.builder()
			.all(Set.of("a", "b"))
			.any(Set.of("c", "d"))
			.none(Set.of("skip"))
			.types(Set.of(FlowType.REST_DATA_SOURCE, FlowType.TRANSFORM))
			.build();

		List<Flow> updated = unifiedFlowService.findByTagsAndNewState(filter, FlowState.STOPPED);

		assertThat(updated).hasSize(2);
		assertThat(updated).extracting(Flow::getName)
			.containsExactlyInAnyOrder("dataSourceMatch", "transformMatch");
	}

	@Test
	void testFindByTags() {
		setupFlowTagTestData();

		FlowTagFilter filter = FlowTagFilter.builder()
			.all(Set.of("a", "b"))
			.any(Set.of("c", "d"))
			.none(Set.of("skip"))
			.types(Set.of(FlowType.REST_DATA_SOURCE, FlowType.TRANSFORM))
			.build();

		List<Flow> updated = unifiedFlowService.findByTags(filter);

		// the findByTags does not exlude anything based on state, so the invalid flow and alreadyStopped flow are both returned
		assertThat(updated).hasSize(4)
			.extracting(Flow::getName)
			.containsExactlyInAnyOrder("dataSourceMatch", "transformMatch", "invalid", "alreadyStopped");
	}


	private void setupFlowTagTestData() {
		// create matching flows, show only one of the `any` must be included
		RestDataSource dataSourceMatch = FlowBuilders.buildRestDataSource("dataSourceMatch", FlowState.RUNNING,
				Set.of("a", "b", "c"));
		TransformFlow transformMatch = FlowBuilders.buildTransformFlow("transformMatch", FlowState.RUNNING,
				Set.of("a", "b", "d"));

		// missing the required tag `b`
		RestDataSource missingOneOfAll = FlowBuilders.buildRestDataSource("missingOneOfAll", FlowState.RUNNING,
				Set.of("a", "c", "d"));

		// has all the required tags, but it is exluded due to the none clause with skip
		RestDataSource excluded = FlowBuilders.buildRestDataSource("excluded", FlowState.RUNNING,
				Set.of("a", "b", "d", "skip"));

		// has all the tags needed but it is currently invalid
		RestDataSource invalid = FlowBuilders.buildRestDataSource("invalid", FlowState.RUNNING,
				Set.of("a", "b", "d"));
		invalid.getFlowStatus().setValid(false);

		// has all the tags needed but it is already stopped
		RestDataSource alreadyStopped = FlowBuilders.buildRestDataSource("alreadyStopped", FlowState.STOPPED,
				Set.of("a", "b", "c"));

		// has the required tags but doesn't have any of c or d
		RestDataSource missingAny = FlowBuilders.buildRestDataSource("missingAny", FlowState.RUNNING,
				Set.of("a", "b"));

		// has everything to match except its the wrong type
		TimedDataSource wrongType = FlowBuilders.buildTimedDataSource("wrongType", FlowState.RUNNING,
				Set.of("a", "b", "d"));

		restDataSourceRepo.saveAll(List.of(dataSourceMatch, missingOneOfAll, excluded, invalid, missingAny, alreadyStopped));
		transformFlowRepo.save(transformMatch);
		timedDataSourceRepo.save(wrongType);
	}
}
