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

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.deltafi.core.schedulers.trigger.JoinEntryLockCheckTrigger;
import org.deltafi.core.services.ScheduledJoinService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@ConditionalOnProperty(value = "schedule.maintenance", havingValue = "true", matchIfMissing = true)
@Service
@EnableScheduling
@RequiredArgsConstructor
public class JoinScheduler {
    private final TaskScheduler taskScheduler;
    private final ScheduledJoinService joinEntryService;
    private final JoinEntryLockCheckTrigger joinEntryLockCheckTrigger;

    @PostConstruct
    public void init() {
        taskScheduler.schedule(joinEntryService::unlockTimedOutJoinEntryLocks, joinEntryLockCheckTrigger);
    }

    @Scheduled(fixedDelay = 1L, timeUnit = TimeUnit.SECONDS)
    public void processOverdueJoins() {
        joinEntryService.handleTimedOutJoins();
    }

    @Scheduled(fixedDelay = 5L, timeUnit = TimeUnit.SECONDS)
    public void processOrphanedJoins() {
        joinEntryService.handleOrphanedJoins();
    }
}