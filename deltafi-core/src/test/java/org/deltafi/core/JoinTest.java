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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import org.deltafi.actionkit.ActionKitAutoConfiguration;
import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.join.JoinAction;
import org.deltafi.actionkit.action.join.JoinResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.parameters.ReinjectParameters;
import org.deltafi.actionkit.action.service.ActionRunner;
import org.deltafi.actionkit.exception.StartupException;
import org.deltafi.actionkit.properties.ActionsProperties;
import org.deltafi.actionkit.registration.PluginRegistrar;
import org.deltafi.actionkit.service.HostnameService;
import org.deltafi.common.action.ActionEventQueue;
import org.deltafi.common.action.ActionEventQueueProperties;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.content.Segment;
import org.deltafi.common.resource.Resource;
import org.deltafi.common.test.storage.s3.minio.DeltafiMinioContainer;
import org.deltafi.common.types.*;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.metrics.MetricRepository;
import org.deltafi.core.plugin.deployer.DeployerService;
import org.deltafi.core.plugin.deployer.credential.CredentialProvider;
import org.deltafi.core.repo.DeltaFileRepo;
import org.deltafi.core.repo.IngressFlowRepo;
import org.deltafi.core.services.DeltaFileCacheService;
import org.deltafi.core.services.DeltaFilesService;
import org.deltafi.core.services.IngressFlowService;
import org.deltafi.core.services.IngressService;
import org.deltafi.core.types.IngressFlow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.deltafi.common.test.TestConstants.MONGODB_CONTAINER;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "schedule.maintenance=false",
        "schedule.flowSync=false",
        "schedule.diskSpace=false",
        "schedule.errorCount=false",
        "schedule.propertySync=false"})
@EnableAutoConfiguration(exclude = { ActionKitAutoConfiguration.class })
public class JoinTest {
    public static final String FLOW = "join-ingress";
    public static final String ACTION = FLOW + ".SampleJoinAction";
    public static final String JOIN_FILE_NAME = "joined-content";
    public static final String REINJECT_FLOW = "reinject-ingress";
    public static final String REINJECT_ACTION = REINJECT_FLOW + ".SampleJoinReinjectAction";
    public static final Map<String, String> REINJECT_METADATA = Map.of("key-1", "value-1", "key-2", "value-2");

    public static class TestJoinAction extends JoinAction<ActionParameters> {
        public TestJoinAction() {
            super("Test join action");
        }

        @Override
        protected JoinResult join(DeltaFile deltaFile, List<DeltaFile> joinedDeltaFiles, ActionContext context,
                ActionParameters params) {
            deltaFile.getSourceInfo().setFilename(JOIN_FILE_NAME);
            return new JoinResult(context, deltaFile.getSourceInfo(),
                    new Content(JOIN_FILE_NAME, Collections.emptyMap(),
                            new ContentReference("content", new Segment(deltaFile.getDid()))));
        }
    }

    public static class TestJoinReinjectAction extends JoinAction<ReinjectParameters> {
        public TestJoinReinjectAction() {
            super("Test join reinject action");
        }

        @Override
        protected JoinResult join(DeltaFile deltaFile, List<DeltaFile> joinedDeltaFiles, ActionContext context,
                ReinjectParameters params) {
            SourceInfo sourceInfo = SourceInfo.builder()
                    .filename(JOIN_FILE_NAME)
                    .flow(params.getReinjectFlow())
                    .metadata(REINJECT_METADATA)
                    .build();
            return new JoinResult(context, sourceInfo, new Content(JOIN_FILE_NAME, Collections.emptyMap(),
                    new ContentReference("content", new Segment(deltaFile.getDid()))));
        }
    }

    public static class TestActionRunner extends ActionRunner {
        @PostConstruct
        @Override
        public void startActions() {
            try {
                super.startActions();
            } catch (StartupException e) {
                // ignore
            }
        }
    }

    @TestConfiguration
    @EnableConfigurationProperties({ ActionEventQueueProperties.class, ActionsProperties.class })
    public static class Configuration {
        @Bean
        public BuildProperties buildProperties() {
            Properties properties = new Properties();
            properties.put("group", "test.group");
            properties.put("artifact", "test-artifact");
            properties.put("version", "0.0.1");
            properties.put("actionKitVersion", "1.0.0");
            properties.put("name", "Test Plugin Name");
            properties.put("description", "Test Plugin Description");
            return new BuildProperties(properties);
        }

        @Bean
        public ActionEventQueue actionEventQueue(ActionEventQueueProperties actionEventQueueProperties,
                List<Action<?>> actions) throws URISyntaxException {
            return new ActionEventQueue(actionEventQueueProperties, 6);
        }

        @Bean
        public ActionRunner actionRunner() {
            return new TestActionRunner();
        }

        @Bean
        public HostnameService hostnameService(ActionsProperties actionsProperties) {
            return new HostnameService(actionsProperties);
        }

        @Bean
        public TestJoinAction joinAction() {
            return new TestJoinAction();
        }

        @Bean
        public TestJoinReinjectAction joinReinjectAction() {
            return new TestJoinReinjectAction();
        }
    }

    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer(MONGODB_CONTAINER);

    private static final DeltafiMinioContainer minioContainer =
            new DeltafiMinioContainer("minioadmin", "minioadmin", ContentStorageService.CONTENT_BUCKET);

    private static final GenericContainer<?> redisContainer =
            new GenericContainer<>(DockerImageName.parse("redis:7.0.5")).withExposedPorts(6379);

    static {
        mongoDBContainer.start();
        minioContainer.start();
        redisContainer.start();
    }

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        registry.add("minio.url", () -> "http://" + minioContainer.getHost() + ":" + minioContainer.getMappedPort());
        registry.add("redis.url", () -> "http://" + redisContainer.getHost() + ":" + redisContainer.getMappedPort(6379));
    }

    @Autowired
    DeltaFilesService deltaFilesService;

    @Autowired
    IngressFlowRepo ingressFlowRepo;

    @Autowired
    IngressFlowService ingressFlowService;

    @Autowired
    DeltaFileRepo deltaFileRepo;

    @Autowired
    DeltaFileCacheService deltaFileCacheService;

    @MockBean
    IngressService ingressService;
    @MockBean
    MetricRepository metricRepository;
    @MockBean
    DeployerService deployerService;
    @MockBean
    CredentialProvider credentialProvider;
    @MockBean
    PluginRegistrar pluginRegistrar;

    @BeforeEach
    public void beforeEach() {
        ingressFlowRepo.deleteAll();
        ingressFlowService.refreshCache();

        deltaFileRepo.deleteAll();
    }

    @Test
    public void joinsAfterTimeout() throws IOException {
        JoinActionConfiguration joinActionConfiguration = new JoinActionConfiguration(ACTION,
                TestJoinAction.class.getCanonicalName(), "PT5S");
        joinActionConfiguration.setMaxNum(3);

        IngressFlow ingressFlow = new IngressFlow();
        ingressFlow.setName(FLOW);
        ingressFlow.getFlowStatus().setState(FlowState.RUNNING);
        ingressFlow.setJoinAction(joinActionConfiguration);
        ingressFlowRepo.insert(ingressFlow);

        String did1 = UUID.randomUUID().toString();
        ingressFile("join-1.json", did1, FLOW);
        String did2 = UUID.randomUUID().toString();
        ingressFile("join-2.json", did2, FLOW);
        await().until(() -> deltaFileRepo.count() == 3); // 2 ingested + 1 joined

        DeltaFile deltaFile1 = deltaFileRepo.findById(did1).orElseThrow();
        DeltaFile deltaFile2 = deltaFileRepo.findById(did2).orElseThrow();
        await().until(() -> deltaFileHasActionWithState(deltaFile1.getChildDids().get(0), ACTION, ActionState.COMPLETE));

        DeltaFile joinedDeltaFile = deltaFileRepo.findById(deltaFile1.getChildDids().get(0)).orElseThrow();

        verifyDeltaFileThatWasJoined(deltaFile1, joinedDeltaFile.getDid());
        verifyDeltaFileThatWasJoined(deltaFile2, joinedDeltaFile.getDid());

        assertTrue(joinedDeltaFile.getParentDids().containsAll(List.of(did1, did2)));

        assertEquals(JOIN_FILE_NAME, joinedDeltaFile.getSourceInfo().getFilename());
        assertEquals(FLOW, joinedDeltaFile.getSourceInfo().getFlow());
        assertEquals(4, joinedDeltaFile.getSourceInfo().getMetadata().size());

        assertEquals(1, joinedDeltaFile.getProtocolStack().get(0).getContent().size());
    }

    private boolean deltaFileHasActionWithState(String did, String actionName, ActionState actionState) {
        DeltaFile deltaFile = deltaFileRepo.findById(did).orElse(null);
        if (deltaFile == null) {
            return false;
        }
        org.deltafi.common.types.Action action = deltaFile.actionNamed(actionName).orElse(null);
        if (action == null) {
            return false;
        }
        return action.getState() == actionState;
    }

    @Test
    public void joinsOnMaxNum() throws IOException {
        JoinActionConfiguration joinActionConfiguration = new JoinActionConfiguration(ACTION,
                TestJoinAction.class.getCanonicalName(), "PT1M");
        joinActionConfiguration.setMaxNum(2);

        IngressFlow ingressFlow = new IngressFlow();
        ingressFlow.setName(FLOW);
        ingressFlow.getFlowStatus().setState(FlowState.RUNNING);
        ingressFlow.setJoinAction(joinActionConfiguration);
        ingressFlowRepo.insert(ingressFlow);

        String did1 = UUID.randomUUID().toString();
        ingressFile("join-1.json", did1, FLOW);
        String did2 = UUID.randomUUID().toString();
        ingressFile("join-2.json", did2, FLOW);
        await().until(() -> deltaFileRepo.count() == 3); // 2 ingested + 1 joined

        DeltaFile deltaFile1 = deltaFileRepo.findById(did1).orElseThrow();
        DeltaFile deltaFile2 = deltaFileRepo.findById(did2).orElseThrow();
        await().until(() -> deltaFileHasActionWithState(deltaFile1.getChildDids().get(0), ACTION, ActionState.COMPLETE));

        DeltaFile joinedDeltaFile = deltaFileRepo.findById(deltaFile1.getChildDids().get(0)).orElseThrow();

        verifyDeltaFileThatWasJoined(deltaFile1, joinedDeltaFile.getDid());
        verifyDeltaFileThatWasJoined(deltaFile2, joinedDeltaFile.getDid());

        assertTrue(joinedDeltaFile.getParentDids().containsAll(List.of(did1, did2)));

        assertEquals(JOIN_FILE_NAME, joinedDeltaFile.getSourceInfo().getFilename());
        assertEquals(FLOW, joinedDeltaFile.getSourceInfo().getFlow());
        assertEquals(4, joinedDeltaFile.getSourceInfo().getMetadata().size());

        assertEquals(1, joinedDeltaFile.getProtocolStack().get(0).getContent().size());
    }

    @Test
    public void joinsByMetadata() throws IOException {
        JoinActionConfiguration joinActionConfiguration = new JoinActionConfiguration(ACTION,
                TestJoinAction.class.getCanonicalName(), "PT5S");
        joinActionConfiguration.setMaxNum(2);
        joinActionConfiguration.setMetadataKey("common-source-metadata");

        IngressFlow ingressFlow = new IngressFlow();
        ingressFlow.setName(FLOW);
        ingressFlow.getFlowStatus().setState(FlowState.RUNNING);
        ingressFlow.setJoinAction(joinActionConfiguration);
        ingressFlowRepo.insert(ingressFlow);

        String did1 = UUID.randomUUID().toString();
        ingressFile("join-1.json", did1, FLOW);
        String did2 = UUID.randomUUID().toString();
        ingressFile("join-2.json", did2, FLOW);
        String did3 = UUID.randomUUID().toString();
        ingressFile("join-3.json", did3, FLOW);
        await().until(() -> deltaFileRepo.count() == 5); // 3 ingested + 2 joined

        DeltaFile deltaFile1 = deltaFileRepo.findById(did1).orElseThrow();
        DeltaFile deltaFile2 = deltaFileRepo.findById(did2).orElseThrow();
        DeltaFile deltaFile3 = deltaFileRepo.findById(did3).orElseThrow();

        assertEquals(deltaFile1.getChildDids(), deltaFile3.getChildDids());
        assertNotEquals(deltaFile1.getChildDids(), deltaFile2.getChildDids());

        await().until(() -> deltaFileHasActionWithState(deltaFile1.getChildDids().get(0), ACTION, ActionState.COMPLETE));
        await().until(() -> deltaFileHasActionWithState(deltaFile2.getChildDids().get(0), ACTION, ActionState.COMPLETE));
    }

    @Test
    public void ordersByMetadataIndexKey() throws IOException {
        JoinActionConfiguration joinActionConfiguration = new JoinActionConfiguration(ACTION,
                TestJoinAction.class.getCanonicalName(), "PT1M");
        joinActionConfiguration.setMaxNum(3);
        joinActionConfiguration.setMetadataIndexKey("fragment-index");

        IngressFlow ingressFlow = new IngressFlow();
        ingressFlow.setName(FLOW);
        ingressFlow.getFlowStatus().setState(FlowState.RUNNING);
        ingressFlow.setJoinAction(joinActionConfiguration);
        ingressFlowRepo.insert(ingressFlow);

        String did1 = UUID.randomUUID().toString();
        ingressFile("join-1.json", did1, FLOW);
        String did2 = UUID.randomUUID().toString();
        ingressFile("join-2.json", did2, FLOW);
        String did3 = UUID.randomUUID().toString();
        ingressFile("join-3.json", did3, FLOW);
        await().until(() -> deltaFileRepo.count() == 4); // 3 ingested + 1 joined

        DeltaFile deltaFile1 = deltaFileRepo.findById(did1).orElseThrow();

        await().until(() -> deltaFileHasActionWithState(deltaFile1.getChildDids().get(0), ACTION, ActionState.COMPLETE));

        DeltaFile joinedDeltaFile = deltaFileRepo.findById(deltaFile1.getChildDids().get(0)).orElseThrow();
        assertEquals(List.of(did3, did1, did2), joinedDeltaFile.getParentDids());
    }

    @Test
    public void joinsAndReinjects() throws IOException {
        JoinActionConfiguration joinActionConfiguration = new JoinActionConfiguration(REINJECT_ACTION,
                TestJoinReinjectAction.class.getCanonicalName(), "PT1M");
        joinActionConfiguration.setMaxNum(2);
        joinActionConfiguration.setParameters(Map.of("reinjectFlow", REINJECT_FLOW));

        IngressFlow ingressFlow = new IngressFlow();
        ingressFlow.setName(FLOW);
        ingressFlow.getFlowStatus().setState(FlowState.RUNNING);
        ingressFlow.setJoinAction(joinActionConfiguration);
        ingressFlowRepo.insert(ingressFlow);

        IngressFlow reinjectFlow = new IngressFlow();
        reinjectFlow.setName(REINJECT_FLOW);
        reinjectFlow.getFlowStatus().setState(FlowState.RUNNING);
        ingressFlowRepo.insert(reinjectFlow);

        String did1 = UUID.randomUUID().toString();
        ingressFile("join-1.json", did1, FLOW);
        String did2 = UUID.randomUUID().toString();
        ingressFile("join-2.json", did2, FLOW);
        await().until(() -> deltaFileRepo.count() == 3); // 2 ingested + 1 joined

        DeltaFile deltaFile1 = deltaFileRepo.findById(did1).orElseThrow();
        await().until(() -> deltaFileHasActionWithState(deltaFile1.getChildDids().get(0), REINJECT_ACTION,
                ActionState.COMPLETE));

        DeltaFile joinedDeltaFile = deltaFileRepo.findById(deltaFile1.getChildDids().get(0)).orElseThrow();

        assertEquals(JOIN_FILE_NAME, joinedDeltaFile.getSourceInfo().getFilename());
        assertEquals(REINJECT_FLOW, joinedDeltaFile.getSourceInfo().getFlow());
        assertEquals(REINJECT_METADATA, joinedDeltaFile.getSourceInfo().getMetadata());
        assertEquals(1, joinedDeltaFile.getProtocolStack().size());
        assertEquals(1, joinedDeltaFile.getLastProtocolLayerContent().size());
    }

    private void verifyDeltaFileThatWasJoined(DeltaFile deltaFile, String joinedDeltaFileDid) {
        assertEquals(DeltaFileStage.JOINED, deltaFile.getStage());
        assertEquals(1, deltaFile.getChildDids().size());
        assertEquals(joinedDeltaFileDid, deltaFile.getChildDids().get(0));
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private void ingressFile(String fileName, String did, String flow) throws IOException {
        deltaFilesService.ingress(OBJECT_MAPPER.readValue(
                String.format(Resource.read("/full-flow/" + fileName), did, flow), IngressEvent.class));
    }
}
