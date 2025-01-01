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
package org.deltafi.core.services;

import org.deltafi.core.types.snapshot.Snapshot;
import org.deltafi.core.types.snapshot.DataSinkSnapshot;
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
    public static final String FLOW = "dataSource";

    @InjectMocks
    AnnotationService annotationService;

    @Mock
    DataSinkService dataSinkService;

    @Mock
    DeltaFilesService deltaFilesService;

    @Test
    void testSetExpectedAnnotations_egress() {
        annotationService.setExpectedAnnotations(FLOW, ANNOTATION_KEYS);
        Mockito.verify(dataSinkService).setExpectedAnnotations(FLOW, ANNOTATION_KEYS);
    }

    @Test
    void testSetExpectedAnnotations_invalidFlow() {
        Mockito.when(dataSinkService.setExpectedAnnotations(Mockito.any(), Mockito.any())).thenThrow(new IllegalArgumentException());
        assertThatThrownBy(() -> annotationService.setExpectedAnnotations("invalidFlow", ANNOTATION_KEYS)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testClearRemovedAnnotations() {
        Mockito.when(dataSinkService.setExpectedAnnotations(FLOW, null)).thenReturn(true);
        annotationService.setExpectedAnnotations(FLOW, Set.of());
        Mockito.verify(deltaFilesService).updatePendingAnnotationsForFlows(FLOW, null);
    }

    @Test
    void testClearRemovedAnnotations_skipWhenNoChange() {
        Mockito.when(dataSinkService.setExpectedAnnotations(FLOW, null)).thenReturn(false);
        annotationService.setExpectedAnnotations(FLOW, Set.of());
        Mockito.verifyNoInteractions(deltaFilesService);
    }

    @Test
    void resetFromSnapshot() {
        Snapshot snapshot = new Snapshot();
        snapshot.setDataSinks(List.of(dataSinkSnapshot("egressNoChange", ANNOTATION_KEYS),
                dataSinkSnapshot("egressChanged", ANNOTATION_KEYS), dataSinkSnapshot("nullset", null)));

        Mockito.when(dataSinkService.setExpectedAnnotations("egressNoChange", ANNOTATION_KEYS)).thenReturn(false);
        Mockito.when(dataSinkService.setExpectedAnnotations("egressChanged", ANNOTATION_KEYS)).thenReturn(true);
        Mockito.when(dataSinkService.setExpectedAnnotations("nullset", null)).thenReturn(true);

        annotationService.resetFromSnapshot(snapshot, true);

        Mockito.verify(dataSinkService, Mockito.times(3)).setExpectedAnnotations(Mockito.any(), Mockito.any());
        Mockito.verify(deltaFilesService).updatePendingAnnotationsForFlows("egressChanged", ANNOTATION_KEYS);
    }

    DataSinkSnapshot dataSinkSnapshot(String name, Set<String> expectedAnnotations) {
        DataSinkSnapshot flowSnapshot = new DataSinkSnapshot(name);
        flowSnapshot.setExpectedAnnotations(expectedAnnotations);
        return flowSnapshot;
    }
}