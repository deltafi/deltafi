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
package org.deltafi.core.lookup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.lookup.LookupTable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

@ConditionalOnProperty("lookup.enabled")
@Service
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class TimedLookupTableSupplierScheduler {
    private final Clock clock;
    private final LookupTableService lookupTableService;

    @Scheduled(fixedRate = 1L, timeUnit = TimeUnit.MINUTES)
    public void triggerTimedLookupTableSuppliers() {
        List<LookupTable> lookupTablesToRefresh = lookupTableService.getLookupTables().stream()
                .filter(LookupTable::isBackingServiceActive)
                .filter(lookupTable -> lookupTable.getRefreshDuration() != null)
                .filter(lookupTable -> (lookupTable.getLastRefresh() == null) ||
                        isRefreshDue(lookupTable.getRefreshDuration(), lookupTable.getLastRefresh()))
                .toList();

        for (LookupTable lookupTable : lookupTablesToRefresh) {
            try {
                lookupTableService.refresh(lookupTable.getName());
            } catch (LookupTableServiceException e) {
                log.warn("Unable to refresh lookup table {}", lookupTable.getName(), e);
            }
        }
    }

    private boolean isRefreshDue(String refreshDuration, OffsetDateTime lastRefresh) {
        return OffsetDateTime.now(clock).plusSeconds(1L).isAfter(lastRefresh.plus(Duration.parse(refreshDuration)));
    }
}
