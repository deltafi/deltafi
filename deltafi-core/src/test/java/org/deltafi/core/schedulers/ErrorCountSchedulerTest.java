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
package org.deltafi.core.schedulers;

import org.deltafi.core.services.ErrorCountService;
import org.deltafi.core.services.NormalizeFlowService;
import org.deltafi.core.services.TransformFlowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
class ErrorCountSchedulerTest {

    @InjectMocks
    private ErrorCountScheduler errorCountScheduler;

    @Mock
    private NormalizeFlowService normalizeFlowService;
    @Mock
    private TransformFlowService transformFlowService;

    @Mock
    private ErrorCountService errorCountService;

    @Test
    void populateErrorCounts() {
        Mockito.when(normalizeFlowService.maxErrorsPerFlow()).thenReturn(Map.of("a", 1));
        Mockito.when(transformFlowService.maxErrorsPerFlow()).thenReturn(Map.of("b", 1));
        errorCountScheduler.populateErrorCounts();
        Mockito.verify(errorCountService).populateErrorCounts(Set.of("a", "b"));
    }
}