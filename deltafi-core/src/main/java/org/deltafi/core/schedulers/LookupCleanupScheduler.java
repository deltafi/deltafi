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
import org.deltafi.core.repo.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@ConditionalOnProperty(value = "schedule.maintenance", havingValue = "true", matchIfMissing = true)
@Service
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class LookupCleanupScheduler {

    private static final int HIGHER_STARTUP_LIMIT = 1000;
    private static final int LOWER_RUN_LIMIT = 50;

    private final ActionNameRepo actionNameRepo;
    private final AnnotationKeyRepo annotationKeyRepo;
    private final AnnotationValueRepo annotationValueRepo;
    private final ErrorCauseRepo errorCauseRepo;
    private final EventGroupRepo eventGroupRepo;
    private final EventAnnotationsRepo eventAnnotationsRepo;
    private int currentLimit = HIGHER_STARTUP_LIMIT;

    // run every 6 hours, which is half the frequency at which analytics chunks are dropped
    @Scheduled(fixedDelayString = "PT6H")
    public void cleanup() {
        log.info("Analytic lookup tables cleanup started");
        actionNameRepo.deleteUnusedActionNames();
        log.info("Cleaned up unused action name lookups");
        annotationKeyRepo.deleteUnusedAnnotationKeys();
        log.info("Cleaned up unused annotation key lookups");
        annotationValueRepo.deleteUnusedAnnotationValues();
        log.info("Cleaned up unused annotation value lookups");
        errorCauseRepo.deleteUnusedErrorCauses();
        log.info("Cleaned up unused error cause lookups");
        eventGroupRepo.deleteUnusedEventGroups();
        log.info("Cleaned up unused event group lookups");
        Integer cleaned = currentLimit;
        while (cleaned != null && cleaned == currentLimit) {
            cleaned = eventAnnotationsRepo.deleteUnusedEventAnnotations(currentLimit);
        }
        log.info("Cleaned up unused event annotation lookups, batch size: " + currentLimit);
        currentLimit = LOWER_RUN_LIMIT;
        log.info("Analytic lookup tables cleanup complete");
    }
}
