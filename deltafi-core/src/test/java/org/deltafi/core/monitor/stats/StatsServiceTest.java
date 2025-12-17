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
package org.deltafi.core.monitor.stats;

import org.deltafi.common.queue.valkey.ValkeyKeyedBlockingQueue;
import org.deltafi.core.generated.types.DeltaFileStats;
import org.deltafi.core.metrics.MetricService;
import org.deltafi.core.services.DeltaFilesService;
import org.deltafi.core.services.ErrorCountService;
import org.deltafi.core.services.EventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock
    private DeltaFilesService deltaFilesService;

    @Mock
    private ErrorCountService errorCountService;

    @Mock
    private EventService eventService;

    @Mock
    private ValkeyKeyedBlockingQueue valkeyQueue;

    @Mock
    private MetricService metricService;

    @InjectMocks
    private StatsService statsService;

    @Test
    void deltaFileStats_serializesOffsetDateTimeCorrectly() {
        OffsetDateTime oldest = OffsetDateTime.parse("2025-01-15T10:30:00Z");
        DeltaFileStats stats = new DeltaFileStats(1000L, 50L, 5000L, 100L, 25L, 5L, oldest, java.util.UUID.randomUUID());

        when(deltaFilesService.deltaFileStats()).thenReturn(stats);

        statsService.deltaFileStats();

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valkeyQueue).set(anyString(), jsonCaptor.capture());

        String json = jsonCaptor.getValue();
        assertNotNull(json);
        assertTrue(json.contains("\"oldestInFlightCreated\""));
        assertTrue(json.contains("2025-01-15"));
        assertFalse(json.contains("InvalidDefinitionException"));
    }

    @Test
    void deltaFileStats_handlesNullOldestInFlightCreated() {
        DeltaFileStats stats = new DeltaFileStats(1000L, 50L, 5000L, 100L, 25L, 5L, null, null);

        when(deltaFilesService.deltaFileStats()).thenReturn(stats);

        statsService.deltaFileStats();

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valkeyQueue).set(anyString(), jsonCaptor.capture());

        String json = jsonCaptor.getValue();
        assertNotNull(json);
        assertTrue(json.contains("\"oldestInFlightCreated\":null"));
    }

    @Test
    void errorCount_publishesCorrectly() {
        when(errorCountService.getTotalUnacknowledgedErrors()).thenReturn(42L);

        statsService.errorCount();

        verify(valkeyQueue).set(anyString(), eq("42"));
    }

    @Test
    void notificationCount_publishesCorrectly() {
        when(eventService.notificationCount()).thenReturn(7L);

        statsService.notificationCount();

        verify(valkeyQueue).set(anyString(), eq("7"));
    }
}
