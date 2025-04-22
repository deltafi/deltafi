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

    private final ActionNameRepo actionNameRepo;
    private final AnnotationKeyRepo annotationKeyRepo;
    private final AnnotationValueRepo annotationValueRepo;
    private final ErrorCauseRepo errorCauseRepo;
    private final EventGroupRepo eventGroupRepo;
    private final EventAnnotationsRepo eventAnnotationsRepo;

    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        actionNameRepo.deleteUnusedActionNames();
        annotationKeyRepo.deleteUnusedAnnotationKeys();
        annotationValueRepo.deleteUnusedAnnotationValues();
        errorCauseRepo.deleteUnusedErrorCauses();
        eventGroupRepo.deleteUnusedEventGroups();
        eventAnnotationsRepo.deleteUnusedEventAnnotations();
    }
}