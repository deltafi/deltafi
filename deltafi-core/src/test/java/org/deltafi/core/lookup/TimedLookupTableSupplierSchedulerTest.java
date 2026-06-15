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
package org.deltafi.core.lookup;

import org.deltafi.common.lookup.LookupTable;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

class TimedLookupTableSupplierSchedulerTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-09T12:00:00Z"), ZoneOffset.UTC);
    private final LookupTableService lookupTableService = Mockito.mock(LookupTableService.class);
    private final TimedLookupTableSupplierScheduler scheduler =
            new TimedLookupTableSupplierScheduler(clock, lookupTableService);

    private static LookupTable.LookupTableBuilder table(String name) {
        return LookupTable.builder().name(name).columns(List.of("a")).keyColumns(List.of("a"))
                .refreshDuration("PT1H"); // due immediately when lastRefresh is null
    }

    @Test
    void pollsSupplierBackedTable() throws Exception {
        Mockito.when(lookupTableService.getLookupTables()).thenReturn(List.of(
                table("supplier").serviceBacked(true).backingServiceActive(true).build()));

        scheduler.triggerTimedLookupTableSuppliers();

        Mockito.verify(lookupTableService).refresh("supplier");
    }

    @Test
    void skipsOperatorOwnedTableWithRefreshDuration() throws Exception {
        // serviceBacked=false: refreshDuration is a client-cache TTL only, there is no supplier to poll.
        Mockito.when(lookupTableService.getLookupTables()).thenReturn(List.of(
                table("operator-owned").serviceBacked(false).backingServiceActive(true).build()));

        scheduler.triggerTimedLookupTableSuppliers();

        Mockito.verify(lookupTableService, Mockito.never()).refresh(Mockito.anyString());
    }

    @Test
    void skipsTableWithInactiveBackingService() throws Exception {
        Mockito.when(lookupTableService.getLookupTables()).thenReturn(List.of(
                table("disabled").serviceBacked(true).backingServiceActive(false).build()));

        scheduler.triggerTimedLookupTableSuppliers();

        Mockito.verify(lookupTableService, Mockito.never()).refresh(Mockito.anyString());
    }
}
