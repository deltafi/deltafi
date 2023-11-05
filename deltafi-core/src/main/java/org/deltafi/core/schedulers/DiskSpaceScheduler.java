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

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.deltafi.core.services.DiskSpaceService;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Conditional(OnDiskSpaceOrMaintenanceCondition.class)
@Service
@EnableScheduling
@RequiredArgsConstructor
public class DiskSpaceScheduler {

    private final DiskSpaceService diskSpaceService;
    private final TaskScheduler taskScheduler;

    @PostConstruct
    public void schedule() {
        taskScheduler.scheduleAtFixedRate(diskSpaceService::getContentStorageDiskMetrics, Instant.now().plusSeconds(5), Duration.ofSeconds(5));
    }
}
