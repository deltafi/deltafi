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
package org.deltafi.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import graphql.ExecutionResult;
import org.deltafi.common.action.ActionEventQueue;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.resource.Resource;
import org.deltafi.common.types.*;
import org.deltafi.core.configuration.*;
import org.deltafi.core.configuration.EgressActionConfiguration;
import org.deltafi.core.configuration.FormatActionConfiguration;
import org.deltafi.core.configuration.LoadActionConfiguration;
import org.deltafi.core.configuration.TransformActionConfiguration;
import org.deltafi.core.configuration.server.constants.PropertyConstants;
import org.deltafi.core.configuration.server.environment.DeltaFiCompositeEnvironmentRepository;
import org.deltafi.core.configuration.server.environment.MongoEnvironmentRepository;
import org.deltafi.core.configuration.server.repo.PropertyRepository;
import org.deltafi.core.configuration.server.repo.PropertyRepositoryImpl;
import org.deltafi.core.configuration.server.repo.StateHolderRepositoryInMemoryImpl;
import org.deltafi.core.configuration.server.service.PropertyMetadataLoader;
import org.deltafi.core.configuration.server.service.PropertyService;
import org.deltafi.core.configuration.server.service.StateHolderService;
import org.deltafi.core.datafetchers.FlowPlanDatafetcherTestHelper;
import org.deltafi.core.datafetchers.PropertiesDatafetcherTestHelper;
import org.deltafi.core.delete.DeletePolicyWorker;
import org.deltafi.core.delete.DeleteRunner;
import org.deltafi.core.generated.DgsConstants;
import org.deltafi.core.generated.client.*;
import org.deltafi.core.generated.types.ConfigType;
import org.deltafi.core.generated.types.DeltaFiConfiguration;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.plugin.Plugin;
import org.deltafi.core.plugin.PluginRepository;
import org.deltafi.core.repo.*;
import org.deltafi.core.services.*;
import org.deltafi.core.types.*;
import org.deltafi.core.types.DomainActionSchema;
import org.deltafi.core.types.EgressActionSchema;
import org.deltafi.core.types.EgressFlowPlanInput;
import org.deltafi.core.types.EnrichActionSchema;
import org.deltafi.core.types.FlowAssignmentRule;
import org.deltafi.core.types.FormatActionSchema;
import org.deltafi.core.types.LoadActionSchema;
import org.deltafi.core.types.PluginVariables;
import org.deltafi.core.types.TransformActionSchema;
import org.deltafi.core.types.ValidateActionSchema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
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
import static org.deltafi.core.configuration.server.constants.PropertyConstants.DELTAFI_PROPERTY_SET;
import static org.deltafi.core.Util.assertEqualsIgnoringDates;
import static org.deltafi.core.Util.buildDeltaFile;
import static org.deltafi.core.datafetchers.ActionSchemaDatafetcherTestHelper.*;
import static org.deltafi.core.datafetchers.DeletePolicyDatafetcherTestHelper.*;
import static org.deltafi.core.datafetchers.DeltaFilesDatafetcherTestHelper.*;
import static org.deltafi.core.datafetchers.FlowAssignmentDatafetcherTestHelper.*;
import static org.deltafi.core.plugin.PluginDataFetcherTestHelper.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;

@SpringBootTest
@TestPropertySource(properties = {"deltafi.deltaFileTtl=3d", "enableScheduling=false"})
@Testcontainers
class DeltaFiCoreApplicationTests {

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
	DeletePolicyService deletePolicyService;

	@Autowired
	DeleteRunner deleteRunner;

	@Autowired
	DeltaFileRepo deltaFileRepo;

	@Autowired
	ActionSchemaRepo actionSchemaRepo;

	@Autowired
	DeletePolicyRepo deletePolicyRepo;

	@Autowired
	FlowAssignmentRuleRepo flowAssignmentRuleRepo;

	@Autowired
	PluginRepository pluginRepository;

	@Autowired
	IngressFlowService ingressFlowService;

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
	PluginVariableRepo pluginVariableRepo;

	@Autowired
	PropertyRepository propertyRepository;

	@Autowired
	EgressFlowPlanService egressFlowPlanService;

	@MockBean
	StorageConfigurationService storageConfigurationService;

	@Captor
	ArgumentCaptor<List<ActionInput>> actionInputListCaptor;

	static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

	// mongo eats microseconds, jump through hoops
	private final OffsetDateTime MONGO_NOW =  OffsetDateTime.of(LocalDateTime.ofEpochSecond(OffsetDateTime.now().toInstant().toEpochMilli(), 0, ZoneOffset.UTC), ZoneOffset.UTC);

	private final OffsetDateTime START_TIME = OffsetDateTime.of(2021, 7, 11, 13, 44, 22, 183, ZoneOffset.UTC);
	private final OffsetDateTime STOP_TIME = OffsetDateTime.of(2021, 7, 11, 13, 44, 22, 184, ZoneOffset.UTC);

	private static final String TEST_PLUGIN = "test-plugin";
	private static final String EDITABLE = "editable";
	private static final String NOT_EDITABLE = "not-editable";
	private static final String ORIGINAL_VALUE = "original";

	@TestConfiguration
	@EnableConfigurationProperties(ConfigServerProperties.class)
	public static class Configuration {
		@Bean
		public ActionEventQueue actionEventQueue() {
			ActionEventQueue actionEventQueue = Mockito.mock(ActionEventQueue.class);
			try {
				// Allows the ActionEventScheduler to not hold up other scheduled tasks (by default, Spring Boot uses a
				// single thread for all scheduled tasks). Throwing an exception here breaks it out of its tight loop.
				Mockito.when(actionEventQueue.takeResult()).thenThrow(new RuntimeException());
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			}
			return actionEventQueue;
		}

		// Create the property related beans that are normally created in the DeltaFiConfigDataLocationResolver
		@Bean
		public StateHolderService stateHolderService() {
			return new StateHolderService(new StateHolderRepositoryInMemoryImpl());
		}

		@Bean
		public PropertyRepository propertyRepository(MongoTemplate mongoTemplate) {
			return new PropertyRepositoryImpl(mongoTemplate);
		}

		@Bean
		public PropertyService propertyService(PropertyRepository propertyRepository, StateHolderService stateHolderService) {
			return new PropertyService(propertyRepository, new PropertyMetadataLoader(), stateHolderService, null, null);
		}

		@Bean
		public EnvironmentRepository envRepo(PropertyService propertiesService, StateHolderService stateHolderService, ConfigServerProperties configServerProperties) {
			MongoEnvironmentRepository mongoEnvironmentRepository = new MongoEnvironmentRepository(propertiesService, stateHolderService, configServerProperties);
			return new DeltaFiCompositeEnvironmentRepository(new ArrayList<>(List.of(mongoEnvironmentRepository)), false, stateHolderService);
		}
	}

	@Autowired
	ActionEventQueue actionEventQueue;

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
		deltaFileRepo.deleteAll();
		flowAssignmentRuleRepo.deleteAll();
		loadConfig();
		loadTestProperties();

		Mockito.clearInvocations(actionEventQueue);
	}

	void loadConfig() {
		loadIngressConfig();
		loadEnrichConfig();
		loadEgressConfig();
	}

	void loadIngressConfig() {
		org.deltafi.core.configuration.LoadActionConfiguration lc = new org.deltafi.core.configuration.LoadActionConfiguration();
		lc.setName("SampleLoadAction");
		org.deltafi.core.configuration.TransformActionConfiguration tc = new org.deltafi.core.configuration.TransformActionConfiguration();
		tc.setName("Utf8TransformAction");
		TransformActionConfiguration tc2 = new TransformActionConfiguration();
		tc2.setName("SampleTransformAction");

		IngressFlow sampleIngressFlow = buildRunningFlow("sample", lc, List.of(tc, tc2));
		IngressFlow retryFlow = buildRunningFlow("theFlow", lc, null);
		IngressFlow childFlow = buildRunningFlow("childFlow", lc, List.of(tc2));

		ingressFlowRepo.saveAll(List.of(sampleIngressFlow, retryFlow, childFlow));
	}

    void loadEgressConfig() {
		org.deltafi.core.configuration.ValidateActionConfiguration authValidate = new org.deltafi.core.configuration.ValidateActionConfiguration();
		authValidate.setName("AuthorityValidateAction");
		org.deltafi.core.configuration.ValidateActionConfiguration sampleValidate = new org.deltafi.core.configuration.ValidateActionConfiguration();
		sampleValidate.setName("SampleValidateAction");

		org.deltafi.core.configuration.FormatActionConfiguration sampleFormat = new org.deltafi.core.configuration.FormatActionConfiguration();
		sampleFormat.setName("sample.SampleFormatAction");
		sampleFormat.setRequiresDomains(List.of("sample"));
		sampleFormat.setRequiresEnrichment(List.of("sampleEnrichment"));

		org.deltafi.core.configuration.EgressActionConfiguration sampleEgress = new org.deltafi.core.configuration.EgressActionConfiguration();
		sampleEgress.setName("SampleEgressAction");

		EgressFlow sampleEgressFlow = buildRunningFlow("sample", sampleFormat, sampleEgress);
		sampleEgressFlow.setValidateActions(List.of(authValidate, sampleValidate));

		FormatActionConfiguration errorFormat = new FormatActionConfiguration();
		errorFormat.setName("ErrorFormatAction");
		errorFormat.setRequiresDomains(List.of("error"));
		EgressActionConfiguration errorEgress = new EgressActionConfiguration();
		errorEgress.setName("ErrorEgressAction");
		EgressFlow errorFlow = buildRunningFlow("error", errorFormat, errorEgress);

		egressFlowRepo.saveAll(List.of(sampleEgressFlow, errorFlow));
    }

	void loadEnrichConfig() {
		enrichFlowRepo.save(buildEnrichFlow(FlowState.RUNNING));
	}

	void loadTestProperties() {
		propertyRepository.removeAll();
		PropertySet common = buildPropertySet(DELTAFI_PROPERTY_SET);
		PropertySet actionKit = buildPropertySet(PropertyConstants.ACTION_KIT_PROPERTY_SET);
		PropertySet testPlugin = buildPropertySet(TEST_PLUGIN);

		propertyRepository.saveAll(List.of(common, actionKit, testPlugin));
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
			if (policy instanceof DiskSpaceDeletePolicy) {
				DiskSpaceDeletePolicy diskPolicy = (DiskSpaceDeletePolicy) policy;
				if (diskPolicy.getName().equals(DISK_SPACE_PERCENT_POLICY)) {
					assertTrue(diskPolicy.isEnabled());
					assertFalse(diskPolicy.isLocked());
					foundDiskSpacePercent = true;
				}
			} else if (policy instanceof TimedDeletePolicy) {
				TimedDeletePolicy timedPolicy = (TimedDeletePolicy) policy;
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

	private String graphQL(String filename) throws IOException {
		return new String(Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("full-flow/" + filename + ".graphql")).readAllBytes());
	}

	DeltaFile postIngressDeltaFile(String did) {
		DeltaFile deltaFile = Util.emptyDeltaFile(did, "flow");
		deltaFile.setIngressBytes(500L);
		deltaFile.queueAction("Utf8TransformAction");
		deltaFile.setSourceInfo(new SourceInfo("input.txt", "sample", new ArrayList<>(List.of(new KeyValue("AuthorizedBy", "XYZ"), new KeyValue("removeMe", "whatever")))));
		Content content = Content.newBuilder().contentReference(new ContentReference("objectName", 0, 500, did, "application/octet-stream")).build();
		deltaFile.getProtocolStack().add(new ProtocolLayer(INGRESS_ACTION, List.of(content), null));
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
		deltaFile.completeAction("Utf8TransformAction", START_TIME, STOP_TIME);
		deltaFile.queueAction("SampleTransformAction");
		Content content = Content.newBuilder().name("file.json").contentReference(new ContentReference("utf8ObjectName", 0, 500, did, "application/octet-stream")).build();
		deltaFile.getProtocolStack().add(new ProtocolLayer("Utf8TransformAction", List.of(content), null));
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
		deltaFile.completeAction("SampleTransformAction", START_TIME, STOP_TIME);
		deltaFile.queueAction("SampleLoadAction");
		Content content = Content.newBuilder().contentReference(new ContentReference("objectName", 0, 500, did, "application/octet-stream")).build();
		deltaFile.getProtocolStack().add(new ProtocolLayer("SampleTransformAction", List.of(content), transformSampleMetadata));
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
		deltaFile.errorAction("SampleTransformAction", START_TIME, STOP_TIME, "transform failed", "message");
		/*
		 * Even though this action is being used as an ERROR, fake its
		 * protocol layer results so that we can verify the State Machine
		 * will still recognize that Transform actions are incomplete,
		 * and not attempt to queue the Load action, too.
		 */
		Content content = Content.newBuilder().contentReference(new ContentReference("objectName", 0, 500, did, "application/octet-stream")).build();
		deltaFile.getProtocolStack().add(new ProtocolLayer("SampleTransformAction", List.of(content), transformSampleMetadata));
		return deltaFile;
	}

	@SuppressWarnings("SameParameterValue")
	DeltaFile postRetryTransformDeltaFile(String did, String retryAction) {
		DeltaFile deltaFile = postTransformHadErrorDeltaFile(did);
		deltaFile.retryErrors();
		deltaFile.setStage(DeltaFileStage.INGRESS);
		deltaFile.getActions().add(Action.newBuilder().name(retryAction).state(ActionState.QUEUED).build());
		return deltaFile;
	}

	@Test
	void test06Retry() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postTransformHadErrorDeltaFile(did));

		List<RetryResult> retryResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("06.resume"), did),
				"data." + DgsConstants.MUTATION.Resume,
				new TypeRef<>() {});

		assertEquals(1, retryResults.size());
		assertEquals(did, retryResults.get(0).getDid());
		assertTrue(retryResults.get(0).getSuccess());

		DeltaFile expected = postRetryTransformDeltaFile(did, "SampleLoadAction");
		expected.getSourceInfo().setFilename("newFilename");
		expected.getSourceInfo().setFlow("theFlow");
		expected.getSourceInfo().setMetadata(List.of(new KeyValue("AuthorizedBy", "ABC"), new KeyValue("sourceInfo.filename.original", "input.txt"), new KeyValue("sourceInfo.flow.original", "sample"), new KeyValue("removeMe.original", "whatever"), new KeyValue("AuthorizedBy.original", "XYZ"), new KeyValue("anotherKey", "anotherValue")));
		verifyActionEventResults(expected, "SampleLoadAction");
	}

	@Test
	void test06ResumeContentDeleted() throws IOException {
		String did = UUID.randomUUID().toString();
		DeltaFile contentDeletedDeltaFile = postTransformHadErrorDeltaFile(did);
		contentDeletedDeltaFile.setContentDeleted(OffsetDateTime.now());
		contentDeletedDeltaFile.setContentDeletedReason("aged off");
		deltaFileRepo.save(contentDeletedDeltaFile);

		List<RetryResult> retryResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("06.resume"), did),
				"data." + DgsConstants.MUTATION.Resume,
				new TypeRef<>() {});

		assertEquals(1, retryResults.size());
		assertEquals(did, retryResults.get(0).getDid());
		assertFalse(retryResults.get(0).getSuccess());
	}

	DeltaFile postLoadDeltaFile(String did) {
		DeltaFile deltaFile = postTransformDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.ENRICH);
		deltaFile.queueAction("SampleDomainAction");
		deltaFile.completeAction("SampleLoadAction", START_TIME, STOP_TIME);
		deltaFile.addDomain("sample", "sampleDomain", "application/octet-stream");
		Content content = Content.newBuilder().contentReference(new ContentReference("objectName", 0, 500, did, "application/octet-stream")).build();
		deltaFile.getProtocolStack().add(new ProtocolLayer("SampleLoadAction", List.of(content), loadSampleMetadata));
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

		verifyActionEventResults(postLoadDeltaFile(did), "SampleDomainAction");
	}

	DeltaFile post09MissingEnrichDeltaFile(String did) {
		DeltaFile deltaFile = postLoadDeltaFile(did);
		deltaFile.completeAction("SampleDomainAction", START_TIME, STOP_TIME);
		deltaFile.addIndexedMetadata(Map.of("domainKey", "domain metadata"));
		deltaFile.setStage(DeltaFileStage.ERROR);
		deltaFile.queueNewAction(DeltaFiConstants.NO_EGRESS_FLOW_CONFIGURED_ACTION);
		deltaFile.errorAction(DeltaFilesService.buildNoEgressConfiguredErrorEvent(deltaFile));
		deltaFile.addDomain("sample", "sampleDomain", "application/octet-stream");
		deltaFile.getLastProtocolLayer().setMetadata(loadWrongMetadata);
		return deltaFile;
	}

	@Test
	void test09EnrichSkippedWrongMetadata() throws IOException {
		// Test is similar to 08.load, but has the wrong metadata value, which
		// results in the enrich action not being run, and cascades through.
		String did = UUID.randomUUID().toString();
		DeltaFile loaded = postLoadDeltaFile(did);

		// mock loading the incorrect metadata so the enrichAction is not fired
		loaded.getLastProtocolLayer().setMetadata(loadWrongMetadata);
		deltaFileRepo.save(loaded);

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("11.domain"), did),
				"data." + DgsConstants.MUTATION.ActionEvent,
				DeltaFile.class);

		DeltaFile deltaFile = deltaFilesService.getDeltaFile(did);

		assertEqualsIgnoringDates(post09MissingEnrichDeltaFile(did), deltaFile);
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

		Mockito.verify(actionEventQueue).putActions(actionInputListCaptor.capture());
		List<ActionInput> actionInputs = actionInputListCaptor.getValue();
		assertThat(actionInputs).hasSize(2);

		assertEqualsIgnoringDates(child1.forQueue("SampleTransformAction"), actionInputs.get(0).getDeltaFile());
		assertEqualsIgnoringDates(child2.forQueue("SampleTransformAction"), actionInputs.get(1).getDeltaFile());
	}

	DeltaFile postDomainDeltaFile(String did) {
		DeltaFile deltaFile = postLoadDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.ENRICH);
		deltaFile.queueAction("SampleEnrichAction");
		deltaFile.completeAction("SampleDomainAction", START_TIME, STOP_TIME);
		deltaFile.addIndexedMetadata(Map.of("domainKey", "domain metadata"));
		return deltaFile;
	}

	@Test
	void test11Domain() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postLoadDeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("11.domain"), did),
				"data." + DgsConstants.MUTATION.ActionEvent,
				DeltaFile.class);

		verifyActionEventResults(postDomainDeltaFile(did), "SampleEnrichAction");
	}

	DeltaFile postEnrichDeltaFile(String did) {
		DeltaFile deltaFile = postDomainDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.EGRESS);
		deltaFile.queueAction("sample.SampleFormatAction");
		deltaFile.completeAction("SampleEnrichAction", START_TIME, STOP_TIME);
		deltaFile.addEnrichment("sampleEnrichment", "enrichmentData");
		deltaFile.addIndexedMetadata(Map.of("first", "one", "second", "two"));
		return deltaFile;
	}

	@Test
	void test12Enrich() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postDomainDeltaFile(did));

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("12.enrich"), did),
				"data." + DgsConstants.MUTATION.ActionEvent,
				DeltaFile.class);

		verifyActionEventResults(postEnrichDeltaFile(did), "sample.SampleFormatAction");
	}

	DeltaFile postFormatDeltaFile(String did) {
		DeltaFile deltaFile = postEnrichDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.EGRESS);
		deltaFile.queueActionsIfNew(Arrays.asList("AuthorityValidateAction", "SampleValidateAction"));
		deltaFile.completeAction("sample.SampleFormatAction", START_TIME, STOP_TIME);
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

		Mockito.verify(actionEventQueue).putActions(actionInputListCaptor.capture());
		assertEquals(4, actionInputListCaptor.getValue().size());
		assertEquals(child1.getDid(), actionInputListCaptor.getValue().get(0).getActionContext().getDid());
		assertEquals(child1.getDid(), actionInputListCaptor.getValue().get(1).getActionContext().getDid());
		assertEquals(child2.getDid(), actionInputListCaptor.getValue().get(2).getActionContext().getDid());
		assertEquals(child2.getDid(), actionInputListCaptor.getValue().get(3).getActionContext().getDid());
	}

	DeltaFile postValidateDeltaFile(String did) {
		DeltaFile deltaFile = postFormatDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.EGRESS);
		deltaFile.completeAction("SampleValidateAction", START_TIME, STOP_TIME);
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
		assertEqualsIgnoringDates(postValidateDeltaFile(did), deltaFile);

		Mockito.verify(actionEventQueue, never()).putActions(any());
		assertEqualsIgnoringDates(postValidateDeltaFile(did), deltaFile);
	}

	DeltaFile postErrorDeltaFile(String did) {
		DeltaFile deltaFile = postValidateDeltaFile(did);
		deltaFile.setStage(DeltaFileStage.ERROR);
		deltaFile.errorAction("AuthorityValidateAction", START_TIME, STOP_TIME, "Authority XYZ not recognized", "Dead beef feed face cafe");
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
		assertEqualsIgnoringDates(expected, actual);
	}

	@SuppressWarnings("SameParameterValue")
	DeltaFile postResumeDeltaFile(String did, String retryAction) {
		DeltaFile deltaFile = postErrorDeltaFile(did);
		deltaFile.retryErrors();
		deltaFile.setStage(DeltaFileStage.EGRESS);
		deltaFile.getActions().add(Action.newBuilder().name(retryAction).state(ActionState.QUEUED).build());
		return deltaFile;
	}

	@Test
	void test18Resume() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postErrorDeltaFile(did));

		List<RetryResult> retryResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("18.resume"), did),
				"data." + DgsConstants.MUTATION.Resume,
				new TypeRef<>() {});

		assertEquals(2, retryResults.size());
		assertEquals(did, retryResults.get(0).getDid());
		assertTrue(retryResults.get(0).getSuccess());
		assertFalse(retryResults.get(1).getSuccess());

		verifyActionEventResults(postResumeDeltaFile(did, "AuthorityValidateAction"), "AuthorityValidateAction");
	}

	@Test
	void test19ResumeClearsAcknowledged() throws IOException {
		String did = UUID.randomUUID().toString();
		DeltaFile postErrorDeltaFile = postErrorDeltaFile(did);
		postErrorDeltaFile.setErrorAcknowledged(OffsetDateTime.now());
		postErrorDeltaFile.setErrorAcknowledgedReason("some reason");
		deltaFileRepo.save(postErrorDeltaFile);

		dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("18.resume"), did),
				"data." + DgsConstants.MUTATION.Resume,
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
		deltaFile.completeAction("AuthorityValidateAction", START_TIME, STOP_TIME);
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
		deltaFile.setEgressed(true);
		deltaFile.completeAction("SampleEgressAction", START_TIME, STOP_TIME);
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
		assertEqualsIgnoringDates(postEgressDeltaFile(did), deltaFile);

		Mockito.verify(actionEventQueue, never()).putActions(any());
	}

	@Test
	void test23Replay() throws IOException {
		String did = UUID.randomUUID().toString();
		deltaFileRepo.save(postEgressDeltaFile(did));

		List<RetryResult> results = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("23.replay"), did),
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
		expected.getActions().get(1).setName("SampleLoadAction");
		expected.getSourceInfo().setFilename("newFilename");
		expected.getSourceInfo().setFlow("theFlow");
		expected.getSourceInfo().setMetadata(List.of(new KeyValue("AuthorizedBy", "ABC"), new KeyValue("sourceInfo.filename.original", "input.txt"), new KeyValue("sourceInfo.flow.original", "sample"), new KeyValue("removeMe.original", "whatever"), new KeyValue("AuthorizedBy.original", "XYZ"), new KeyValue("anotherKey", "anotherValue")));
		verifyActionEventResults(expected, "SampleLoadAction");

		List<RetryResult> secondResults = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("23.replay"), did),
				"data." + DgsConstants.MUTATION.Replay,
				new TypeRef<>() {});

		assertEquals(1, secondResults.size());
		assertFalse(secondResults.get(0).getSuccess());
	}

	@Test
	void test23ReplayContentDeleted() throws IOException {
		String did = UUID.randomUUID().toString();
		DeltaFile contentDeletedDeltaFile = postEgressDeltaFile(did);
		contentDeletedDeltaFile.setContentDeleted(OffsetDateTime.now());
		contentDeletedDeltaFile.setContentDeletedReason("aged off");
		deltaFileRepo.save(contentDeletedDeltaFile);

		List<RetryResult> results = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("23.replay"), did),
				"data." + DgsConstants.MUTATION.Replay,
				new TypeRef<>() {});

		assertEquals(1, results.size());
		assertFalse(results.get(0).getSuccess());

		DeltaFile parent = deltaFilesService.getDeltaFile(did);
		assertTrue(parent.getChildDids().isEmpty());
	}

	@Test
	void testSourceMetadataUnion() throws IOException {
		DeltaFile deltaFile1 = buildDeltaFile("did1", List.of(
				new KeyValue("key", "val1")));
		DeltaFile deltaFile2 = buildDeltaFile("did2", List.of(
				new KeyValue("key", "val2")));
		List<DeltaFile> deltaFiles = List.of(deltaFile1, deltaFile2);
		deltaFileRepo.saveAll(deltaFiles);

		List<UniqueKeyValues> metadataUnion = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				String.format(graphQL("24.source"), "did1", "did2"),
				"data." + DgsConstants.QUERY.SourceMetadataUnion,
				new TypeRef<>() {
				});

		assertEquals(1, metadataUnion.size());
		assertEquals("key", metadataUnion.get(0).getKey());
		assertEquals(2, metadataUnion.get(0).getValues().size());
		assertTrue(metadataUnion.get(0).getValues().containsAll(List.of("val1", "val2")));
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
		assertEquals(true, deltaFile.getFiltered());

		Mockito.verify(actionEventQueue, never()).putActions(any());
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

		Optional<DeltaFile> deltaFile = deltaFileRepo.findById(did);
		assertTrue(deltaFile.isPresent());
		assertNotNull(deltaFile.get().getContentDeleted());
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

		boolean foundEgress = false;
		boolean foundDomain = false;
		boolean foundEnrich = false;
		boolean foundFormat = false;
		boolean foundLoad = false;
		boolean foundTransform = false;
		boolean foundValidate = false;

		for (ActionSchema schema : schemas) {
			if (schema instanceof EgressActionSchema) {
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
			} else if (schema instanceof DomainActionSchema) {
				checkDomainSchema((DomainActionSchema) schema);
				foundDomain = true;
			}
		}

		assertTrue(foundEgress);
		assertTrue(foundEnrich);
		assertTrue(foundFormat);
		assertTrue(foundLoad);
		assertTrue(foundTransform);
		assertTrue(foundValidate);
		assertTrue(foundDomain);
	}

	private void checkActionCommonFields(ActionSchema schema) {
		assertEquals(PARAM_CLASS, schema.getParamClass());
		assertNotNull(schema.getLastHeard());
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

	private void checkDomainSchema(DomainActionSchema schema) {
		checkActionCommonFields(schema);
		assertEquals(DOMAIN_ACTION, schema.getId());
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
				.parent();

		DeltaFiConfigsGraphQLQuery findConfig = DeltaFiConfigsGraphQLQuery.newRequest().configQuery(configQueryInput).build();

		TypeRef<List<DeltaFiConfiguration>> listOfConfigs = new TypeRef<>() {
		};
		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(findConfig, projection);
		List<DeltaFiConfiguration> configs = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQLQueryRequest.serialize(),
				"data." + findConfig.getOperationName(),
				listOfConfigs);

		assertTrue(configs.get(0) instanceof org.deltafi.core.generated.types.LoadActionConfiguration);

		org.deltafi.core.generated.types.LoadActionConfiguration loadActionConfiguration = (org.deltafi.core.generated.types.LoadActionConfiguration) configs.get(0);
		assertEquals(name, loadActionConfiguration.getName());
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
	void getRunningFlows() {
		clearForFlowTests();

		ingressFlowRepo.save(buildIngressFlow(FlowState.STOPPED));
		assertTrue(FlowPlanDatafetcherTestHelper.startIngressFlow(dgsQueryExecutor));

		egressFlowRepo.save(buildEgressFlow(FlowState.STOPPED));
		assertTrue(FlowPlanDatafetcherTestHelper.startEgressFlow(dgsQueryExecutor));

		SystemFlows flows = FlowPlanDatafetcherTestHelper.getRunningFlows(dgsQueryExecutor);
		assertThat(flows.getIngress()).hasSize(1).matches(ingressFlows -> "ingressFlow".equals(ingressFlows.get(0).getName()));
		assertThat(flows.getEgress()).hasSize(1).matches(egressFlows -> "egressFlow".equals(egressFlows.get(0).getName()));
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
	void testNullIncludeIngress() {
		clearForFlowTests();
		PluginCoordinates coords = PluginCoordinates.builder().artifactId("test-actions").groupId("org.deltafi").version("1.0").build();
		EgressFlowPlanInput egressFlowPlanInput = new EgressFlowPlanInput();
		egressFlowPlanInput.setSourcePlugin(coords);
		egressFlowPlanInput.setName("withNullInclude");
		egressFlowPlanInput.setIncludeIngressFlows(null);
		egressFlowPlanInput.setExcludeIngressFlows(List.of());
        egressFlowPlanInput.setFormatAction(FormatActionConfigurationInput.newBuilder().name("format").type("org.deltafi.actions.Formatter").requiresDomains(List.of("domain")).build());
		egressFlowPlanInput.setEgressAction(EgressActionConfigurationInput.newBuilder().name("egress").type("org.deltafi.actions.EgressAction").build());

		egressFlowPlanService.saveFlowPlan(egressFlowPlanInput);

		EgressFlowPlan egressFlowPlan = egressFlowPlanService.getPlanByName("withNullInclude");
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

		assertThat(getActionNames(actionFamilies, "ingress")).hasSize(1).contains("IngressAction");
		assertThat(getActionNames(actionFamilies, "transform")).hasSize(2).contains("Utf8TransformAction", "SampleTransformAction");
		assertThat(getActionNames(actionFamilies, "load")).hasSize(1).contains("SampleLoadAction");
		assertThat(getActionNames(actionFamilies, "domain")).hasSize(1).contains("SampleDomainAction");
		assertThat(getActionNames(actionFamilies, "enrich")).hasSize(1).contains("SampleEnrichAction");
		assertThat(getActionNames(actionFamilies, "format")).hasSize(1).contains("sample.SampleFormatAction");
		assertThat(getActionNames(actionFamilies, "validate")).isEmpty();
		assertThat(getActionNames(actionFamilies, "egress")).hasSize(1).contains("SampleEgressAction");
	}

	@Test
	void testGetPropertySets() {
		List<PropertySet> propertySets = PropertiesDatafetcherTestHelper.getPropertySets(dgsQueryExecutor);
		assertThat(propertySets).hasSize(3);
	}

	@Test
	void testRemovePluginPropertySet_commonFails() {
		ExecutionResult result = PropertiesDatafetcherTestHelper.removePluginPropertySet_commonFails(dgsQueryExecutor);
		assertThat(result.getErrors()).hasSize(1);
		assertThat(result.getErrors().get(0).getMessage()).isEqualTo("java.lang.IllegalArgumentException: Core PropertySet: deltafi-common cannot be added, replaced or removed");
	}

	@Test
	void testUpdateProperties() {
		Integer result = PropertiesDatafetcherTestHelper.updateProperties(dgsQueryExecutor);
		assertThat(result).isEqualTo(1);
	}

	@Test
	void testRemovePluginPropertySet() {
		assertThat(PropertiesDatafetcherTestHelper.removePluginPropertySet(dgsQueryExecutor)).isTrue();
	}

	@Test
	void testAddPluginPropertySet_commonFails() {
		ExecutionResult result = PropertiesDatafetcherTestHelper.addPluginPropertySet_commonFails(dgsQueryExecutor);
		assertThat(result.getErrors()).hasSize(1);
		assertThat(result.getErrors().get(0).getMessage()).isEqualTo("java.lang.IllegalArgumentException: Core PropertySet: deltafi-common cannot be added, replaced or removed");
	}

	@Test
	void testAddPluginPropertySet_valid() {
		assertThat(PropertiesDatafetcherTestHelper.addPluginPropertySet_valid(dgsQueryExecutor)).isTrue();
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
	void testResolveFlowAssignment() {
		saveFirstRule(dgsQueryExecutor);
		assertEquals(FLOW_NAME1, resolveFlowAssignment(dgsQueryExecutor, SourceInfo.builder()
				.flow("").filename(FILENAME_REGEX).build()));
		assertNull(resolveFlowAssignment(dgsQueryExecutor, SourceInfo.builder()
				.flow("").filename("not-found").build()));
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
		deltaFile.errorAction("SampleLoadAction", START_TIME, STOP_TIME, "blah", "blah");
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
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), org.deltafi.core.plugin.Plugin.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), org.deltafi.core.plugin.Plugin.class));

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(PluginsGraphQLQuery.newRequest().build(), PLUGINS_PROJECTION_ROOT);

		List<org.deltafi.core.plugin.Plugin> plugins =
				dgsQueryExecutor.executeAndExtractJsonPathAsObject(graphQLQueryRequest.serialize(),
						"data.plugins[*]", new TypeRef<>() {});

		assertEquals(2, plugins.size());

		validatePlugin1(plugins.get(0));
	}

	@Test
	void registersPlugin() throws IOException {
		pluginRepository.deleteAll();
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), org.deltafi.core.plugin.Plugin.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-3.json"), org.deltafi.core.plugin.Plugin.class));

		PluginInput pluginInput = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), PluginInput.class);
		RegisterPluginGraphQLQuery registerPluginGraphQLQuery = RegisterPluginGraphQLQuery.newRequest().pluginInput(pluginInput).build();

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(registerPluginGraphQLQuery, REGISTER_PLUGIN_PROJECTION_ROOT);

		Result result = dgsQueryExecutor.executeAndExtractJsonPathAsObject(graphQLQueryRequest.serialize(),
				"data." + registerPluginGraphQLQuery.getOperationName(), Result.class);

		assertTrue(result.isSuccess());
		List<org.deltafi.core.plugin.Plugin> plugins = pluginRepository.findAll();
		assertEquals(3, plugins.size());
	}

	@Test
	void registerPluginReplacesExistingPlugin() throws IOException {
		pluginRepository.deleteAll();
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), org.deltafi.core.plugin.Plugin.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-3.json"), org.deltafi.core.plugin.Plugin.class));
		org.deltafi.core.plugin.Plugin existingPlugin =
				OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), org.deltafi.core.plugin.Plugin.class);
		existingPlugin.getPluginCoordinates().setVersion("0.0.9");
		existingPlugin.setDescription("changed");
		pluginRepository.save(existingPlugin);

		PluginInput pluginInput = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), PluginInput.class);
		RegisterPluginGraphQLQuery registerPluginGraphQLQuery = RegisterPluginGraphQLQuery.newRequest().pluginInput(pluginInput).build();

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(registerPluginGraphQLQuery, REGISTER_PLUGIN_PROJECTION_ROOT);

		Result result = dgsQueryExecutor.executeAndExtractJsonPathAsObject(graphQLQueryRequest.serialize(),
				"data." + registerPluginGraphQLQuery.getOperationName(), Result.class);

		assertTrue(result.isSuccess());
		List<org.deltafi.core.plugin.Plugin> plugins = pluginRepository.findAll();
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

		assertFalse(result.isSuccess());
		assertEquals(2, result.getErrors().size());
		assertTrue(result.getErrors().contains("Plugin dependency not registered: org.deltafi:plugin-2:1.0.0."));
		assertTrue(result.getErrors().contains("Plugin dependency not registered: org.deltafi:plugin-3:1.0.0."));
		List<org.deltafi.core.plugin.Plugin> plugins = pluginRepository.findAll();
		assertTrue(plugins.isEmpty());
	}

	@Test
	void uninstallPluginSuccess() throws IOException {
		pluginRepository.deleteAll();
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), org.deltafi.core.plugin.Plugin.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-3.json"), org.deltafi.core.plugin.Plugin.class));

		PluginInput pluginInput = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), PluginInput.class);
		UninstallPluginGraphQLQuery uninstallPluginGraphQLQuery =
				UninstallPluginGraphQLQuery.newRequest().dryRun(false)
						.pluginCoordinatesInput(pluginInput.getPluginCoordinates()).build();

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(uninstallPluginGraphQLQuery, UNINSTALL_PLUGIN_PROJECTION_ROOT);

		Result result = dgsQueryExecutor.executeAndExtractJsonPathAsObject(graphQLQueryRequest.serialize(),
				"data." + uninstallPluginGraphQLQuery.getOperationName(), Result.class);

		assertTrue(result.isSuccess());
		assertEquals(1, pluginRepository.count());
		Mockito.verify(actionEventQueue).drop(any());
	}

	@Test
	void uninstallPluginDryRun() throws IOException {
		pluginRepository.deleteAll();
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), org.deltafi.core.plugin.Plugin.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-3.json"), org.deltafi.core.plugin.Plugin.class));

		PluginInput pluginInput = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), PluginInput.class);
		UninstallPluginGraphQLQuery uninstallPluginGraphQLQuery =
				UninstallPluginGraphQLQuery.newRequest().dryRun(true)
								.pluginCoordinatesInput(pluginInput.getPluginCoordinates()).build();

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(uninstallPluginGraphQLQuery, UNINSTALL_PLUGIN_PROJECTION_ROOT);

		Result result = dgsQueryExecutor.executeAndExtractJsonPathAsObject(graphQLQueryRequest.serialize(),
				"data." + uninstallPluginGraphQLQuery.getOperationName(), Result.class);

		assertTrue(result.isSuccess());
		assertEquals(2, pluginRepository.count());
	}

	@Test
	void uninstallPluginNotFound() throws IOException {
		pluginRepository.deleteAll();
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), org.deltafi.core.plugin.Plugin.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-3.json"), org.deltafi.core.plugin.Plugin.class));

		PluginInput pluginInput = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), PluginInput.class);
		UninstallPluginGraphQLQuery uninstallPluginGraphQLQuery =
				UninstallPluginGraphQLQuery.newRequest().dryRun(false)
						.pluginCoordinatesInput(pluginInput.getPluginCoordinates()).build();

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(uninstallPluginGraphQLQuery, UNINSTALL_PLUGIN_PROJECTION_ROOT);

		Result result = dgsQueryExecutor.executeAndExtractJsonPathAsObject(graphQLQueryRequest.serialize(),
				"data." + uninstallPluginGraphQLQuery.getOperationName(), Result.class);

		assertFalse(result.isSuccess());
		assertEquals(1, result.getErrors().size());
		assertEquals("Plugin not found", result.getErrors().get(0));
		assertEquals(2, pluginRepository.count());
	}

	@Test
	void uninstallPluginFails() throws IOException {
		pluginRepository.deleteAll();
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), org.deltafi.core.plugin.Plugin.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), org.deltafi.core.plugin.Plugin.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-3.json"), org.deltafi.core.plugin.Plugin.class));

		PluginInput pluginInput = OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), PluginInput.class);
		UninstallPluginGraphQLQuery uninstallPluginGraphQLQuery =
				UninstallPluginGraphQLQuery.newRequest().dryRun(false)
						.pluginCoordinatesInput(pluginInput.getPluginCoordinates()).build();

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(uninstallPluginGraphQLQuery, UNINSTALL_PLUGIN_PROJECTION_ROOT);

		Result result = dgsQueryExecutor.executeAndExtractJsonPathAsObject(graphQLQueryRequest.serialize(),
				"data." + uninstallPluginGraphQLQuery.getOperationName(), Result.class);

		assertFalse(result.isSuccess());
		assertEquals(1, result.getErrors().size());
		assertEquals("The following plugins depend on this plugin: org.deltafi:plugin-1:1.0.0", result.getErrors().get(0));
		assertEquals(3, pluginRepository.count());
	}

    @Test
	void findPluginsWithDependency() throws IOException {
		pluginRepository.deleteAll();
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-1.json"), org.deltafi.core.plugin.Plugin.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-2.json"), org.deltafi.core.plugin.Plugin.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-3.json"), org.deltafi.core.plugin.Plugin.class));
		pluginRepository.save(OBJECT_MAPPER.readValue(Resource.read("/plugins/plugin-4.json"), org.deltafi.core.plugin.Plugin.class));

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
		List<DeltaFile> deltaFiles = dids.stream().map(Util::buildDeltaFile).collect(Collectors.toList());
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
		assertEquals(List.of(deltaFile1.getDid(), deltaFile2.getDid()), deltaFiles.stream().map(DeltaFile::getDid).collect(Collectors.toList()));
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
		assertEquals(List.of(deltaFile1.getDid()), deltaFiles.stream().map(DeltaFile::getDid).collect(Collectors.toList()));
	}

	@Test
	void testFindForDeleteCreatedBeforeWithMetadata() {
		DeltaFile deltaFile1 = buildDeltaFile("1", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile1.setContentDeleted(OffsetDateTime.now());
		deltaFileRepo.save(deltaFile1);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForDelete(OffsetDateTime.now().plusSeconds(1), null, 0, null, "policy", true, 10);
		assertEquals(List.of(deltaFile1.getDid()), deltaFiles.stream().map(DeltaFile::getDid).collect(Collectors.toList()));
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
		assertEquals(List.of(deltaFile1.getDid(), deltaFile4.getDid()), deltaFiles.stream().map(DeltaFile::getDid).collect(Collectors.toList()));
	}

	@Test
	void testFindForDeleteWithFlow() {
		DeltaFile deltaFile1 = buildDeltaFile("1", "a", DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile("2", "b", DeltaFileStage.ERROR, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFileRepo.save(deltaFile2);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForDelete(OffsetDateTime.now().plusSeconds(1), null, 0, "a", "policy", false, 10);
		assertEquals(List.of(deltaFile1.getDid()), deltaFiles.stream().map(DeltaFile::getDid).collect(Collectors.toList()));
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
		DeltaFile deltaFile1 = buildDeltaFile("1", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile1.setTotalBytes(100L);
		deltaFileRepo.save(deltaFile1);
		DeltaFile deltaFile2 = buildDeltaFile("2", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now().plusSeconds(2));
		deltaFile2.setTotalBytes(300L);
		deltaFileRepo.save(deltaFile2);
		DeltaFile deltaFile3 = buildDeltaFile("3", null, DeltaFileStage.COMPLETE, OffsetDateTime.now(), OffsetDateTime.now());
		deltaFile3.setTotalBytes(500L);
		deltaFileRepo.save(deltaFile3);

		List<DeltaFile> deltaFiles = deltaFileRepo.findForDelete(250L, null, "policy", 100);
		assertEquals(List.of(deltaFile1.getDid(), deltaFile2.getDid()), deltaFiles.stream().map(DeltaFile::getDid).collect(Collectors.toList()));
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

		List<DeltaFile> deltaFiles = deltaFileRepo.findForDelete(2500L, null, "policy", 100);
		assertEquals(List.of(deltaFile1.getDid(), deltaFile2.getDid(), deltaFile3.getDid()), deltaFiles.stream().map(DeltaFile::getDid).collect(Collectors.toList()));
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
		assertEquals(List.of(deltaFile1.getDid(), deltaFile2.getDid()), deltaFiles.stream().map(DeltaFile::getDid).collect(Collectors.toList()));
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
		deltaFile1.setDomains(List.of(new Domain("domain1", null, null)));
		deltaFile1.addIndexedMetadata(Map.of("a.1", "first", "common", "value"));
		deltaFile1.setEnrichment(List.of(new Enrichment("enrichment1", null, null)));
		deltaFile1.setContentDeleted(MONGO_NOW);
		deltaFile1.setSourceInfo(new SourceInfo("filename1", "flow1", List.of(new KeyValue("key1", "value1"), new KeyValue("key2", "value2"))));
		deltaFile1.setActions(List.of(Action.newBuilder().name("action1").build()));
		deltaFile1.setFormattedData(List.of(FormattedData.newBuilder().filename("formattedFilename1").formatAction("formatAction1").metadata(List.of(new KeyValue("formattedKey1", "formattedValue1"), new KeyValue("formattedKey2", "formattedValue2"))).egressActions(List.of("EgressAction1", "EgressAction2")).build()));
		deltaFile1.setErrorAcknowledged(MONGO_NOW);
		deltaFileRepo.save(deltaFile1);

		DeltaFile deltaFile2 = buildDeltaFile("2", null, DeltaFileStage.ERROR, MONGO_NOW.plusSeconds(2), MONGO_NOW.minusSeconds(2));
		deltaFile2.setIngressBytes(200L);
		deltaFile2.setDomains(List.of(new Domain("domain1", null, null), new Domain("domain2", null, null)));
		deltaFile2.addIndexedMetadata(Map.of("a.2", "first", "common", "value"));
		deltaFile2.setEnrichment(List.of(new Enrichment("enrichment1", null, null), new Enrichment("enrichment2", null, null)));
		deltaFile2.setSourceInfo(new SourceInfo("filename2", "flow2", List.of()));
		deltaFile2.setActions(List.of(Action.newBuilder().name("action1").state(ActionState.ERROR).errorCause("Cause").build(), Action.newBuilder().name("action2").build()));
		deltaFile2.setFormattedData(List.of(FormattedData.newBuilder().filename("formattedFilename2").formatAction("formatAction2").egressActions(List.of("EgressAction1")).build()));
		deltaFile2.setEgressed(true);
		deltaFile2.setFiltered(true);
		deltaFileRepo.save(deltaFile2);

		testFilter(DeltaFilesFilter.newBuilder().createdAfter(MONGO_NOW).build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().createdBefore(MONGO_NOW).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().domains(Collections.emptyList()).build(), deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().domains(List.of("domain1")).build(), deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().domains(List.of("domain1", "domain2")).build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().enrichment(Collections.emptyList()).build(), deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().enrichment(List.of("enrichment1")).build(), deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().enrichment(List.of("enrichment1", "enrichment2")).build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().contentDeleted(true).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().contentDeleted(false).build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().modifiedAfter(MONGO_NOW).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().modifiedBefore(MONGO_NOW).build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().ingressBytesMin(50L).build(), deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().ingressBytesMin(150L).build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().ingressBytesMax(250L).build(), deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().ingressBytesMax(150L).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().ingressBytesMax(100L).ingressBytesMin(100L).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().stage(DeltaFileStage.COMPLETE).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().sourceInfo(SourceInfoFilter.newBuilder().filename("filename1").build()).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().sourceInfo(SourceInfoFilter.newBuilder().flow("flow2").build()).build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().sourceInfo(SourceInfoFilter.newBuilder().metadata(List.of(new KeyValue("key1", "value1"))).build()).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().sourceInfo(SourceInfoFilter.newBuilder().metadata(List.of(new KeyValue("key1", "value1"), new KeyValue("key2", "value2"))).build()).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().sourceInfo(SourceInfoFilter.newBuilder().metadata(List.of(new KeyValue("key1", "value1"), new KeyValue("key2", "value1"))).build()).build());
		testFilter(DeltaFilesFilter.newBuilder().actions(Collections.emptyList()).build(), deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().actions(List.of("action1")).build(), deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().actions(List.of("action1", "action2")).build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().errorCause("^Cause$").build(), deltaFile2);
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
		testFilter(DeltaFilesFilter.newBuilder().egressed(false).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().egressed(true).build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().filtered(false).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().filtered(true).build(), deltaFile2);
		testFilter(DeltaFilesFilter.newBuilder().indexedMetadata(keyValuePairs("common", "value")).build(), deltaFile2, deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().indexedMetadata(keyValuePairs("a.1", "first")).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().indexedMetadata(keyValuePairs("a.1", "first", "common", "value")).build(), deltaFile1);
		testFilter(DeltaFilesFilter.newBuilder().indexedMetadata(keyValuePairs("a.1", "first", "common", "value", "extra", "missing")).build());
		testFilter(DeltaFilesFilter.newBuilder().indexedMetadata(keyValuePairs("a.1", "first", "common", "miss")).build());
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
	void getIds() {
		Set<String> ids = propertyRepository.getIds();
		assertThat(ids).hasSizeGreaterThanOrEqualTo(3)
				.contains(DELTAFI_PROPERTY_SET, PropertyConstants.ACTION_KIT_PROPERTY_SET, TEST_PLUGIN);
	}

	@Test
	void updateProperties() {
		PropertyUpdate commonUpdate = PropertyUpdate.builder()
				.propertySetId(DELTAFI_PROPERTY_SET).key(EDITABLE).value("new value").build();
		PropertyUpdate pluginUpdate = PropertyUpdate.builder()
				.propertySetId(TEST_PLUGIN).key(EDITABLE).value("new value").build();

		int propertySetsUpdated = propertyRepository.updateProperties(List.of(commonUpdate, pluginUpdate));
		assertThat(propertySetsUpdated).isEqualTo(2);

		assertThat(getValue(DELTAFI_PROPERTY_SET)).isEqualTo("new value");
		assertThat(getValue(TEST_PLUGIN)).isEqualTo("new value");
	}

	@Test
	void unsetProperties() {
		PropertyId commonUpdate = PropertyId.builder()
				.propertySetId(DELTAFI_PROPERTY_SET).key(EDITABLE).build();
		PropertyId pluginUpdate = PropertyId.builder()
				.propertySetId(TEST_PLUGIN).key(EDITABLE).build();

		int propertySetsUpdated = propertyRepository.unsetProperties(List.of(commonUpdate, pluginUpdate));
		assertThat(propertySetsUpdated).isEqualTo(2);

		assertThat(getValue(DELTAFI_PROPERTY_SET)).isNull();
		assertThat(getValue(TEST_PLUGIN)).isNull();
	}

	@Test
	void unsetProperties_notEditable() {
		PropertyId notEditable = PropertyId.builder()
				.propertySetId(DELTAFI_PROPERTY_SET).key(NOT_EDITABLE).build();

		int propertySetsUpdated = propertyRepository.unsetProperties(List.of(notEditable));
		assertThat(propertySetsUpdated).isZero();

		assertThat(getValue(DELTAFI_PROPERTY_SET, NOT_EDITABLE)).isEqualTo(ORIGINAL_VALUE);
	}

	@Test
	void updateProperties_notEditable() {
		PropertyUpdate notEditable = PropertyUpdate.builder()
				.propertySetId(DELTAFI_PROPERTY_SET).key(NOT_EDITABLE).value("new value").build();

		int propertySetsUpdated = propertyRepository.updateProperties(List.of(notEditable));
		assertThat(propertySetsUpdated).isZero();

		assertThat(getValue(DELTAFI_PROPERTY_SET, NOT_EDITABLE)).isEqualTo(ORIGINAL_VALUE);
	}

	@Test
	void updateProperties_notExists() {
		PropertyUpdate notExists = PropertyUpdate.builder()
				.propertySetId(DELTAFI_PROPERTY_SET).key("missing").value("abc").build();

		int propertySetsUpdated = propertyRepository.updateProperties(List.of(notExists));
		assertThat(propertySetsUpdated).isZero();
	}

	@Test
	void testActionRegisteredQuery() {
		actionSchemaRepo.deleteAll();
		OffsetDateTime threshold = OffsetDateTime.now();

		// name will match and time matches
		TransformActionSchema transformActionSchema = new TransformActionSchema();
		transformActionSchema.setId("transformAction");
		transformActionSchema.setLastHeard(threshold.plusSeconds(1));

		// name will match but time is too old
		LoadActionSchema loadActionSchema = new LoadActionSchema();
		loadActionSchema.setId("loadAction");
		loadActionSchema.setLastHeard(threshold.minusSeconds(1));

		// time matches but name does not match
		FormatActionSchema formatActionSchema = new FormatActionSchema();
		formatActionSchema.setId("otherFormatAction");
		formatActionSchema.setLastHeard(threshold.plusMinutes(1));

		actionSchemaRepo.saveAll(List.of(transformActionSchema, loadActionSchema, formatActionSchema));

		assertThat(actionSchemaRepo.countAllByIdInAndLastHeardGreaterThanEqual(List.of("transformAction", "loadAction", "formatAction"), threshold)).isEqualTo(1);
		assertThat(actionSchemaRepo.count()).isEqualTo(3);
	}

	String getValue(String propertySet) {
		return getValue(propertySet, EDITABLE);
	}

	String getValue(String propertySet, String key) {
		return propertyRepository.findById(propertySet)
				.map(PropertySet::getProperties).orElse(Collections.emptyList()).stream()
				.filter(p -> key.equals(p.getKey())).findFirst().map(Property::getValue).orElse(null);
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
		org.deltafi.core.configuration.LoadActionConfiguration lc = new org.deltafi.core.configuration.LoadActionConfiguration();
		lc.setName("SampleLoadAction");
		org.deltafi.core.configuration.TransformActionConfiguration tc = new org.deltafi.core.configuration.TransformActionConfiguration();
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
		ingressFlow.setLoadAction(loadActionConfiguration);
		ingressFlow.setTransformActions(transforms);
		return ingressFlow;
	}

	private EgressFlow buildEgressFlow(FlowState flowState) {
		org.deltafi.core.configuration.FormatActionConfiguration sampleFormat = new org.deltafi.core.configuration.FormatActionConfiguration();
		sampleFormat.setName("sample.SampleFormatAction");
		sampleFormat.setRequiresDomains(List.of("sample"));
		sampleFormat.setRequiresEnrichment(List.of("sampleEnrichment"));

		org.deltafi.core.configuration.EgressActionConfiguration sampleEgress = new org.deltafi.core.configuration.EgressActionConfiguration();
		sampleEgress.setName("SampleEgressAction");

		return buildFlow("egressFlow", sampleFormat, sampleEgress, flowState);
	}

	private EnrichFlow buildEnrichFlow(FlowState flowState) {
		EnrichFlow enrichFlow = new EnrichFlow();
		enrichFlow.setName("enrich");

		org.deltafi.core.configuration.DomainActionConfiguration sampleDomain = new org.deltafi.core.configuration.DomainActionConfiguration();
		sampleDomain.setName("SampleDomainAction");
		sampleDomain.setRequiresDomains(List.of("sample"));

		org.deltafi.core.configuration.EnrichActionConfiguration sampleEnrich = new org.deltafi.core.configuration.EnrichActionConfiguration();
		sampleEnrich.setName("SampleEnrichAction");
		sampleEnrich.setRequiresDomains(List.of("sample"));
		sampleEnrich.setRequiresMetadataKeyValues(List.of(new KeyValue("loadSampleType", "load-sample-type")));

		enrichFlow.setDomainActions(List.of(sampleDomain));
		enrichFlow.setEnrichActions(List.of(sampleEnrich));
		enrichFlow.getFlowStatus().setState(flowState);
		return enrichFlow;
	}

	private EgressFlow buildRunningFlow(String name, FormatActionConfiguration formatAction, EgressActionConfiguration egressAction) {
		return buildFlow(name, formatAction, egressAction, FlowState.RUNNING);
	}

	private EgressFlow buildFlow(String name, FormatActionConfiguration formatAction, EgressActionConfiguration egressAction, FlowState flowState) {
		EgressFlow egressFlow = new EgressFlow();
		egressFlow.setName(name);
		egressFlow.setFormatAction(formatAction);
		egressFlow.setEgressAction(egressAction);
		egressFlow.setIncludeIngressFlows(null);
		egressFlow.getFlowStatus().setState(flowState);
		return egressFlow;
	}

	PropertySet buildPropertySet(String name) {
		PropertySet propertySet = Util.getPropertySet(name);
		propertySet.getProperties().add(Util.getProperty(EDITABLE, ORIGINAL_VALUE, true));
		propertySet.getProperties().add(Util.getProperty(NOT_EDITABLE, ORIGINAL_VALUE, false));
		return propertySet;
	}

	void clearForFlowTests() {
		ingressFlowRepo.deleteAll();
		ingressFlowPlanRepo.deleteAll();
		enrichFlowPlanRepo.deleteAll();
		enrichFlowRepo.deleteAll();
		egressFlowRepo.deleteAll();
		egressFlowPlanRepo.deleteAll();
		pluginVariableRepo.deleteAll();
	}

	private List<KeyValue> keyValuePairs(String ... pairs) {
		List<KeyValue> keyValues = new ArrayList<>();
		for (int i = 0; i < pairs.length; i++) {
			keyValues.add(new KeyValue(pairs[i], pairs[++i]));
		}
		return keyValues;
	}

	@Test
	void domains() {
		deltaFileRepo.insert(DeltaFile.newBuilder()
				.domains(List.of(Domain.newBuilder().name("a").build(), Domain.newBuilder().name("b").build()))
				.build());
		deltaFileRepo.insert(DeltaFile.newBuilder()
				.domains(List.of(Domain.newBuilder().name("b").build(), Domain.newBuilder().name("c").build()))
				.build());

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(new DomainsGraphQLQuery());

		List<String> actual = dgsQueryExecutor.executeAndExtractJsonPathAsObject(
				graphQLQueryRequest.serialize(),
				"data." + DgsConstants.QUERY.Domains,
				new TypeRef<>() {}
		);

		assertEquals(List.of("a", "b", "c"), actual);
	}

	@Test
	void domainsEmpty() {;

		GraphQLQueryRequest graphQLQueryRequest = new GraphQLQueryRequest(new DomainsGraphQLQuery());

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
}
