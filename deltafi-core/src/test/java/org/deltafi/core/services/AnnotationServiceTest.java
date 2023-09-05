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

import org.deltafi.common.types.FlowType;
import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.snapshot.types.EgressFlowSnapshot;
import org.deltafi.core.snapshot.types.TransformFlowSnapshot;
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
    TransformFlowService transformFlowService;

    @Mock
    EgressFlowService egressFlowService;

    @Mock
    DeltaFilesService deltaFilesService;

    @Test
    void testSetExpectedAnnotations_transform() {
        annotationService.setExpectedAnnotations(FlowType.TRANSFORM, FLOW, ANNOTATION_KEYS);
        Mockito.verify(transformFlowService).setExpectedAnnotations(FLOW, ANNOTATION_KEYS);
    }

    @Test
    void testSetExpectedAnnotations_egress() {
        annotationService.setExpectedAnnotations(FlowType.EGRESS, FLOW, ANNOTATION_KEYS);
        Mockito.verify(egressFlowService).setExpectedAnnotations(FLOW, ANNOTATION_KEYS);
    }

    @Test
    void testSetExpectedAnnotations_invalidFlowType() {
        assertThatThrownBy(() -> annotationService.setExpectedAnnotations(FlowType.NORMALIZE, FLOW, ANNOTATION_KEYS)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testClearRemovedAnnotations() {
        Mockito.when(transformFlowService.setExpectedAnnotations(FLOW, null)).thenReturn(true);
        annotationService.setExpectedAnnotations(FlowType.TRANSFORM, FLOW, Set.of());
        Mockito.verify(deltaFilesService).asyncUpdatePendingAnnotationsForFlows(FLOW, null);
    }

    @Test
    void testClearRemovedAnnotations_skipWhenNoChange() {
        Mockito.when(transformFlowService.setExpectedAnnotations(FLOW, null)).thenReturn(false);
        annotationService.setExpectedAnnotations(FlowType.TRANSFORM, FLOW, Set.of());
        Mockito.verifyNoInteractions(deltaFilesService);
    }

    @Test
    void resetFromSnapshot() {
        SystemSnapshot systemSnapshot = new SystemSnapshot();
        systemSnapshot.setTransformFlows(List.of(transformFlowSnapshot("transformNoChange", ANNOTATION_KEYS),
                transformFlowSnapshot("transformChanged", ANNOTATION_KEYS), transformFlowSnapshot("emptyset", Set.of())));

        systemSnapshot.setEgressFlows(List.of(egressFlowSnapshot("egressNoChange", ANNOTATION_KEYS),
                egressFlowSnapshot("egressChanged", ANNOTATION_KEYS), egressFlowSnapshot("nullset", null)));

        Mockito.when(transformFlowService.setExpectedAnnotations("transformNoChange", ANNOTATION_KEYS)).thenReturn(false);
        Mockito.when(transformFlowService.setExpectedAnnotations("transformChanged", ANNOTATION_KEYS)).thenReturn(true);
        Mockito.when(transformFlowService.setExpectedAnnotations("emptyset", null)).thenReturn(false);

        Mockito.when(egressFlowService.setExpectedAnnotations("egressNoChange", ANNOTATION_KEYS)).thenReturn(false);
        Mockito.when(egressFlowService.setExpectedAnnotations("egressChanged", ANNOTATION_KEYS)).thenReturn(true);
        Mockito.when(egressFlowService.setExpectedAnnotations("nullset", null)).thenReturn(true);

        annotationService.resetFromSnapshot(systemSnapshot, true);

        Mockito.verify(transformFlowService, Mockito.times(3)).setExpectedAnnotations(Mockito.any(), Mockito.any());
        Mockito.verify(egressFlowService, Mockito.times(3)).setExpectedAnnotations(Mockito.any(), Mockito.any());
        Mockito.verify(deltaFilesService, Mockito.times(2)).asyncUpdatePendingAnnotationsForFlows(Mockito.any(), Mockito.anySet());
        Mockito.verify(deltaFilesService).asyncUpdatePendingAnnotationsForFlows("transformChanged", ANNOTATION_KEYS);
        Mockito.verify(deltaFilesService).asyncUpdatePendingAnnotationsForFlows("egressChanged", ANNOTATION_KEYS);
    }

    TransformFlowSnapshot transformFlowSnapshot(String name, Set<String> expectedAnnotations) {
        TransformFlowSnapshot flowSnapshot = new TransformFlowSnapshot(name);
        flowSnapshot.setExpectedAnnotations(expectedAnnotations);
        return flowSnapshot;
    }

    EgressFlowSnapshot egressFlowSnapshot(String name, Set<String> expectedAnnotations) {
        EgressFlowSnapshot flowSnapshot = new EgressFlowSnapshot(name);
        flowSnapshot.setExpectedAnnotations(expectedAnnotations);
        return flowSnapshot;
    }
}