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
package org.deltafi.core.services;

import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.types.*;
import org.deltafi.common.uuid.UUIDGenerator;
import org.deltafi.core.MockDeltaFiPropertiesService;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.collect.CollectEntryService;
import org.deltafi.core.collect.ScheduledCollectService;
import org.deltafi.core.configuration.ClockConfiguration;
import org.deltafi.core.metrics.MetricService;
import org.deltafi.core.repo.DeltaFileRepo;
import org.deltafi.core.repo.QueuedAnnotationRepo;
import org.deltafi.core.services.analytics.AnalyticEventService;
import org.deltafi.core.services.pubsub.PublisherService;
import org.deltafi.core.types.DeltaFile;
import org.deltafi.core.util.Util;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

@ExtendWith(SpringExtension.class)
@Import({DeltaFilesService.class, ClockConfiguration.class})
@MockBean({TransformFlowService.class,EgressFlowService.class,
        StateMachine.class, DeltaFileRepo.class, CoreEventQueue.class, ContentStorageService.class, DeltaFilesService.class,
        ResumePolicyService.class, MetricService.class, AnalyticEventService.class, CoreAuditLogger.class, DeltaFileCacheService.class,
        PublisherService.class, QueueManagementService.class, QueuedAnnotationRepo.class, Environment.class,
        CollectEntryService.class, ScheduledCollectService.class, UUIDGenerator.class})
@EnableRetry
class MongoRetryTest {

    @Autowired
    private DeltaFilesService deltaFilesService;

    @MockBean
    private DeltaFileCacheService deltaFileCacheService;

    @TestConfiguration
    public static class TestConfig {
        @Bean
        public DeltaFiPropertiesService deltaFiPropertiesService() {
            return new MockDeltaFiPropertiesService();
        }

        @Bean
        public DidMutexService didMutexService() {
            return new DidMutexService();
        }
    }

    @Test
    void testRetryOnOptimisticLockingFailure() {
        UUID did = UUID.randomUUID();
        String fromAction = "egressAction";

        DeltaFile deltaFile = Util.emptyDeltaFile(did, "flow");
        deltaFile.getFlows().getFirst().setActions(new ArrayList<>(Collections.singletonList(Action.builder().name(fromAction).state(ActionState.QUEUED).build())));
        // contrived, but it won't try to save if it's not complete, and the state machine flows aren't populated
        deltaFile.setStage(DeltaFileStage.COMPLETE);

        Mockito.when(deltaFileCacheService.get(did)).thenReturn(deltaFile);
        Mockito.doThrow(new OptimisticLockingFailureException("failed")).when(deltaFileCacheService).save(Mockito.any());

        Assertions.assertDoesNotThrow(() -> deltaFilesService.processResult(ActionEvent.builder()
                .type(ActionEventType.EGRESS).did(did).flowName("flow").actionName(fromAction).start(OffsetDateTime.now()).build()));
    }

}
