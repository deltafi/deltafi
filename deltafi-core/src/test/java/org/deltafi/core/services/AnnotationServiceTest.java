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

import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.snapshot.types.EgressFlowSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class AnnotationServiceTest {

    private static final Set<String> ANNOTATION_KEYS = Set.of("a", "b");
    public static final String FLOW = "flow";

    @InjectMocks
    AnnotationService annotationService;

    @Mock
    EgressFlowService egressFlowService;

    @Mock
    DeltaFilesService deltaFilesService;

    @Test
    void testSetExpectedAnnotations_egress() {
        annotationService.setExpectedAnnotations(FLOW, ANNOTATION_KEYS);
        Mockito.verify(egressFlowService).setExpectedAnnotations(FLOW, ANNOTATION_KEYS);
    }

    @Test
    void testSetExpectedAnnotations_invalidFlow() {
        Mockito.when(egressFlowService.setExpectedAnnotations(Mockito.any(), Mockito.any())).thenThrow(new IllegalArgumentException());
        assertThatThrownBy(() -> annotationService.setExpectedAnnotations("invalidFlow", ANNOTATION_KEYS)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testClearRemovedAnnotations() {
        Mockito.when(egressFlowService.setExpectedAnnotations(FLOW, null)).thenReturn(true);
        annotationService.setExpectedAnnotations(FLOW, Set.of());
        Mockito.verify(deltaFilesService).asyncUpdatePendingAnnotationsForFlows(FLOW, null);
    }

    @Test
    void testClearRemovedAnnotations_skipWhenNoChange() {
        Mockito.when(egressFlowService.setExpectedAnnotations(FLOW, null)).thenReturn(false);
        annotationService.setExpectedAnnotations(FLOW, Set.of());
        Mockito.verifyNoInteractions(deltaFilesService);
    }

    @Test
    void resetFromSnapshot() {
        SystemSnapshot systemSnapshot = new SystemSnapshot();
        systemSnapshot.setEgressFlows(List.of(egressFlowSnapshot("egressNoChange", ANNOTATION_KEYS),
                egressFlowSnapshot("egressChanged", ANNOTATION_KEYS), egressFlowSnapshot("nullset", null)));

        Mockito.when(egressFlowService.setExpectedAnnotations("egressNoChange", ANNOTATION_KEYS)).thenReturn(false);
        Mockito.when(egressFlowService.setExpectedAnnotations("egressChanged", ANNOTATION_KEYS)).thenReturn(true);
        Mockito.when(egressFlowService.setExpectedAnnotations("nullset", null)).thenReturn(true);

        annotationService.resetFromSnapshot(systemSnapshot, true);

        Mockito.verify(egressFlowService, Mockito.times(3)).setExpectedAnnotations(Mockito.any(), Mockito.any());
        Mockito.verify(deltaFilesService).asyncUpdatePendingAnnotationsForFlows("egressChanged", ANNOTATION_KEYS);
    }

    EgressFlowSnapshot egressFlowSnapshot(String name, Set<String> expectedAnnotations) {
        EgressFlowSnapshot flowSnapshot = new EgressFlowSnapshot(name);
        flowSnapshot.setExpectedAnnotations(expectedAnnotations);
        return flowSnapshot;
    }
}