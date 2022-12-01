/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.core.delete.DeleteRunner;
import org.deltafi.core.schedulers.trigger.DeleteTrigger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@ConditionalOnProperty(value = "schedule.maintenance", havingValue = "true", matchIfMissing = true)
@Service
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class DeleteScheduler {

    private final DeleteRunner deleteRunner;
    private final TaskScheduler taskScheduler;
    private final DeleteTrigger deleteTrigger;

    @PostConstruct
    public void scheduleTask() {
        taskScheduler.schedule(this::runDeletes, deleteTrigger);
    }

    public void runDeletes() {
        try {
            deleteRunner.runDeletes();
        } catch (Throwable t) {
            log.error("Unexpected exception while executing scheduled deletes", t);
        }
    }
}