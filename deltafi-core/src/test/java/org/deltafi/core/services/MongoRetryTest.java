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

import org.deltafi.common.action.ActionEventQueue;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.types.*;
import org.deltafi.core.MockDeltaFiPropertiesService;
import org.deltafi.core.util.Util;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.configuration.ClockConfiguration;
import org.deltafi.core.join.JoinRepo;
import org.deltafi.core.metrics.MetricService;
import org.deltafi.core.repo.DeltaFiPropertiesRepo;
import org.deltafi.core.repo.DeltaFileRepo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;

@ExtendWith(SpringExtension.class)
@Import({DeltaFilesService.class, ClockConfiguration.class})
@ImportAutoConfiguration(RefreshAutoConfiguration.class)
@MockBean({StateMachine.class, IngressFlowService.class, EnrichFlowService.class, EgressFlowService.class, TransformFlowService.class,
        ActionEventQueue.class, ContentStorageService.class, FlowAssignmentService.class, CoreAuditLogger.class,
        MetricService.class, DeltaFiPropertiesRepo.class, JoinRepo.class, ResumePolicyService.class,
        IdentityService.class, DeltaFileRepo.class})
@EnableRetry
class MongoRetryTest {

    @Autowired
    private DeltaFilesService deltaFilesService;

    @MockBean
    private DeltaFileCacheService deltaFileCacheService;

    @MockBean
    @SuppressWarnings("unused")
    private JoinRepo joinRepo;

    @TestConfiguration
    public static class TestConfig {
        @Bean
        public DeltaFiPropertiesService deltaFiPropertiesService() {
            return new MockDeltaFiPropertiesService();
        }

        @Bean
        public DidMutexService didMutexService() { return new DidMutexService(); }
    }

    @Test
    void testRetryOnOptimisticLockingFailure() {
        String did = "abc";
        String fromAction = "egressAction";

        DeltaFile deltaFile = Util.emptyDeltaFile(did, "flow");
        deltaFile.setActions(new ArrayList<>(Collections.singletonList(Action.newBuilder().name(fromAction).state(ActionState.QUEUED).build())));
        // contrived, but it won't try to save if it's not complete, and the state machine flows aren't populated
        deltaFile.setStage(DeltaFileStage.COMPLETE);

        Mockito.when(deltaFileCacheService.get(did)).thenReturn(deltaFile);

        Mockito.doThrow(new OptimisticLockingFailureException("failed")).doNothing().when(deltaFileCacheService).save(Mockito.any());

        Assertions.assertDoesNotThrow(() -> deltaFilesService.processResult(ActionEventInput.newBuilder().type(ActionEventType.EGRESS).did(did).action(fromAction).time(OffsetDateTime.now()).build()));
    }

}
