/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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

import org.assertj.core.api.Assertions;
import org.deltafi.common.types.KeyValue;
import org.deltafi.common.types.VariableDataType;
import org.deltafi.core.configuration.LocalStorageProperties;
import org.deltafi.core.repo.DeltaFiPropertiesRepo;
import org.deltafi.core.types.Property;
import org.deltafi.core.types.snapshot.Snapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.deltafi.core.services.DeltaFiPropertiesService.DISABLED_FOR_EXTERNAL_STORAGE;
import static org.junit.Assert.assertEquals;

@ExtendWith(MockitoExtension.class)
class DeltaFiPropertiesServiceTest {

    DeltaFiPropertiesService deltaFiPropertiesService;

    @Mock
    DeltaFiPropertiesRepo deltaFiPropertiesRepo;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Spy
    LocalStorageProperties localStorageProperties = new LocalStorageProperties(true, true);

    @BeforeEach
    void clearRepo() {
        Mockito.reset(deltaFiPropertiesRepo);
        MockitoAnnotations.openMocks(this);
        deltaFiPropertiesService = new DeltaFiPropertiesService(
                deltaFiPropertiesRepo, eventPublisher, localStorageProperties, true);
    }

    @Test
    void startupFailsWithWrongType() {
        // mock up a property that was a long but now a duration
        Property property = Property.builder().key("cacheSyncDuration").customValue("30_000").build();
        Mockito.when(deltaFiPropertiesRepo.findAll()).thenReturn(List.of(property));

        // An invalid property will prevent startup -- this can only happen if a migration was missed or a value is hand-jammed in the DB
        assertThatThrownBy(() -> new DeltaFiPropertiesService(deltaFiPropertiesRepo, eventPublisher, new LocalStorageProperties(true, true), true));
    }

    @Test
    void testUpdateWithBadDataType() {
        Mockito.reset(deltaFiPropertiesRepo);
        // updates that cannot be bound or fail validation are not applied
        deltaFiPropertiesService.updateProperties(List.of(new KeyValue("cacheSyncDuration", "30_000")));
        Mockito.verifyNoInteractions(deltaFiPropertiesRepo);
    }

    @Test
    void testUpdateWithUnrecognizedProperty() {
        Mockito.reset(deltaFiPropertiesRepo);
        // updates with an unrecognized property name are note applied
        deltaFiPropertiesService.updateProperties(List.of(new KeyValue("badKey", "30_000")));
        Mockito.verifyNoInteractions(deltaFiPropertiesRepo);
    }

    @Test
    void testModifyForExternalStorage() {
        Mockito.reset(deltaFiPropertiesRepo);
        DeltaFiPropertiesService propsWithExternalStorage = new DeltaFiPropertiesService(deltaFiPropertiesRepo, eventPublisher, new LocalStorageProperties(false, true), true);
        ArgumentCaptor<Boolean[]> captor = ArgumentCaptor.forClass(Boolean[].class);
        Mockito.verify(deltaFiPropertiesRepo)
                .batchUpsertAndDeleteProperties(
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.any(),
                        captor.capture(),
                        Mockito.any(),
                        Mockito.any());

        Boolean[] editable = captor.getValue();
        assertEquals(56, editable.length);
        int notEditable = 0;
        for (int i = 0; i < editable.length; ++i) {
            if (editable[i] == false) {
                ++notEditable;
            }
        }
        assertEquals(DISABLED_FOR_EXTERNAL_STORAGE.size(), notEditable);
    }

    @Test
    void testUpdateWithDisabledProperty() {
        Property property = Property.builder().key("checkDeleteLagErrorThreshold").editable(false).build();
        Mockito.when(deltaFiPropertiesRepo.findAll()).thenReturn(List.of(property));

        DeltaFiPropertiesService propsWithExternalStorage = new DeltaFiPropertiesService(deltaFiPropertiesRepo, eventPublisher, new LocalStorageProperties(false, true), true);
        List<KeyValue> updates = List.of(new KeyValue("checkDeleteLagErrorThreshold", "30_000"));
        Assertions.assertThatThrownBy(() -> propsWithExternalStorage.updateProperties(updates, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Property checkDeleteLagErrorThreshold is disabled and cannot be changed");
    }

    @Test
    void testSnapshotDisabledPropertyIgnored() {
        DeltaFiPropertiesService propsWithExternalStorage = new DeltaFiPropertiesService(deltaFiPropertiesRepo, eventPublisher, new LocalStorageProperties(false, true), true);
        List<KeyValue> updates = List.of(new KeyValue("checkDeleteLagErrorThreshold", "30_000"));
        Assertions.assertThatNoException().isThrownBy(() -> propsWithExternalStorage.updateProperties(updates, true));
        Mockito.verify(deltaFiPropertiesRepo, Mockito.never()).updateProperty(Mockito.any(), Mockito.any());
    }

    @Test
    void testSnapshotSetValuesNormalized() {
        // A SET property with values in non-sorted order should be normalized in the snapshot
        Property setProperty = Property.builder()
                .key("allowedAnalyticsAnnotations")
                .customValue("c, a, b")
                .defaultValue(null)
                .dataType(VariableDataType.SET)
                .build();
        Mockito.when(deltaFiPropertiesRepo.findAll()).thenReturn(List.of(setProperty));

        Snapshot snapshot = new Snapshot();
        deltaFiPropertiesService.updateSnapshot(snapshot);

        List<KeyValue> props = snapshot.getDeltaFiProperties();
        Assertions.assertThat(props).hasSize(1);
        Assertions.assertThat(props.get(0).getValue()).isEqualTo("a, b, c");
    }

    @Test
    void testSortedSetValueDeduplicates() {
        Assertions.assertThat(DeltaFiPropertiesService.sortedSetValue("a, b, a, c, b"))
                .isEqualTo("a, b, c");
    }

    @Test
    void testSnapshotSetValuesEqualRegardlessOfOrder() {
        // A SET property whose custom value matches the default (just different order) should be excluded from snapshot
        Property setProperty = Property.builder()
                .key("allowedAnalyticsAnnotations")
                .customValue("c, b, a")
                .defaultValue("a, b, c")
                .dataType(VariableDataType.SET)
                .build();
        Mockito.when(deltaFiPropertiesRepo.findAll()).thenReturn(List.of(setProperty));

        Snapshot snapshot = new Snapshot();
        deltaFiPropertiesService.updateSnapshot(snapshot);

        Assertions.assertThat(snapshot.getDeltaFiProperties()).isEmpty();
    }

    @Test
    void testResetFromSnapshotProperties() {
        KeyValue unrecognizedKey = new KeyValue("badKey", "1");
        // cannot be bound to a duration
        KeyValue invalidDataType = new KeyValue("cacheSyncDuration", "30_000");
        // ignored due to the min value check in the setter
        KeyValue valueOutOfRange = new KeyValue("coreServiceThreads", "0");
        KeyValue validValue = new KeyValue("ageOffDays", "1");

        Snapshot snapshot = new Snapshot();
        snapshot.setDeltaFiProperties(List.of(unrecognizedKey, invalidDataType, valueOutOfRange, validValue));

        deltaFiPropertiesService.resetFromSnapshot(snapshot, true);
        Mockito.verify(deltaFiPropertiesRepo).updateProperty(validValue.getKey(), validValue.getValue());
    }

}