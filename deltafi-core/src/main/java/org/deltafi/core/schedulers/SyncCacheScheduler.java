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
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.repo.DeltaFileRepo;
import org.deltafi.core.services.DeltaFileCacheService;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@ConditionalOnProperty(value = "schedule.actionEvents", havingValue = "true", matchIfMissing = true)
@Service
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class SyncCacheScheduler {

    final DeltaFileCacheService deltaFileCacheService;
    final DeltaFiPropertiesService deltaFiPropertiesService;
    final DeltaFileRepo deltaFileRepo;

    @Scheduled(fixedDelay = 2000)
    public void syncCache() {
        deltaFileCacheService.removeOlderThan(deltaFiPropertiesService.getDeltaFiProperties().getDeltaFileCache().getSyncDuration());
    }
}