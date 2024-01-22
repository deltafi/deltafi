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

import lombok.RequiredArgsConstructor;
import org.deltafi.common.action.ActionEventQueue;
import org.deltafi.core.services.*;
import org.deltafi.core.types.TimedDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;

@ConditionalOnProperty(value = "schedule.maintenance", havingValue = "true", matchIfMissing = true)
@Service
@EnableScheduling
@RequiredArgsConstructor
public class DataSourceScheduler {

    private final DeltaFilesService deltaFilesService;
    private final DataSourceService dataSourceService;
    private final ActionEventQueue actionEventQueue;
    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final DiskSpaceService diskSpaceService;
    private final Clock clock;

    @Scheduled(fixedDelay = 1000)
    public void triggerTimedIngressFlows() {
        dataSourceService.refreshCache();
        for (TimedDataSource dataSource : dataSourceService.getRunningTimedIngresses()) {
            if (dataSource.due(actionEventQueue, OffsetDateTime.now(clock)) &&
                    deltaFiPropertiesService.getDeltaFiProperties().getIngress().isEnabled() &&
                    !diskSpaceService.isContentStorageDepleted()) {
                deltaFilesService.taskTimedIngress(dataSource);
            }
        }
    }
}
