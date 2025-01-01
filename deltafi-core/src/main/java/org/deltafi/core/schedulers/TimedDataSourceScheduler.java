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
package org.deltafi.core.schedulers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.FlowType;
import org.deltafi.core.exceptions.IngressUnavailableException;
import org.deltafi.core.services.*;
import org.deltafi.core.types.TimedDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;

@Slf4j
@ConditionalOnProperty(value = "schedule.maintenance", havingValue = "true", matchIfMissing = true)
@Service
@EnableScheduling
@RequiredArgsConstructor
public class TimedDataSourceScheduler {

    private final DeltaFilesService deltaFilesService;
    private final TimedDataSourceService timedDataSourceService;
    private final CoreEventQueue coreEventQueue;
    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final DiskSpaceService diskSpaceService;
    private final ErrorCountService errorCountService;
    private final Clock clock;

    @Scheduled(fixedDelay = 1000)
    public void triggerTimedDataSources() {
        for (TimedDataSource dataSource : timedDataSourceService.getRunningTimedDataSources()) {
            if (dataSource.due(coreEventQueue, OffsetDateTime.now(clock)) &&
                    deltaFiPropertiesService.getDeltaFiProperties().isIngressEnabled() &&
                    !diskSpaceService.isContentStorageDepleted()) {
                try {
                    errorCountService.checkErrorsExceeded(FlowType.TIMED_DATA_SOURCE, dataSource.getName());
                } catch (IngressUnavailableException e) {
                    log.error(e.getMessage());
                    continue;
                }
                deltaFilesService.taskTimedDataSource(dataSource);
            }
        }
    }
}
