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

import org.deltafi.common.types.KeyValue;
import org.deltafi.core.types.Property;
import org.deltafi.core.repo.DeltaFiPropertiesRepo;
import org.deltafi.core.types.snapshot.Snapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class DeltaFiPropertiesServiceTest {

    @InjectMocks
    DeltaFiPropertiesService deltaFiPropertiesService;

    @Mock
    DeltaFiPropertiesRepo deltaFiPropertiesRepo;

    @BeforeEach
    public void clearRepo() {
        Mockito.reset(deltaFiPropertiesRepo);
    }

    @Test
    void startupFailsWithWrongType() {
        // mock up a property that was a long but now a duration
        Property property = Property.builder().key("cacheSyncDuration").customValue("30_000").build();
        Mockito.when(deltaFiPropertiesRepo.findAll()).thenReturn(List.of(property));

        // An invalid property will prevent startup -- this can only happen if a migration was missed or a value is hand-jammed in the DB
        assertThatThrownBy(() -> new DeltaFiPropertiesService(deltaFiPropertiesRepo));
    }

    @Test
    void testUpdateWithBadDataType() {
        // updates that cannot be bound or fail validation are not applied
        deltaFiPropertiesService.updateProperties(List.of(new KeyValue("cacheSyncDuration", "30_000")));
        Mockito.verifyNoInteractions(deltaFiPropertiesRepo);
    }

    @Test
    void testUpdateWithUnrecognizedProperty() {
        // updates with an unrecognized property name are note applied
        deltaFiPropertiesService.updateProperties(List.of(new KeyValue("badKey", "30_000")));
        Mockito.verifyNoInteractions(deltaFiPropertiesRepo);
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