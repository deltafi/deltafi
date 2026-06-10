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
// ABOUTME: Periodically verifies the storage bucket exists and recreates it if missing.
// ABOUTME: Only active in in-memory storage mode where s3proxy loses buckets on restart.
package org.deltafi.core.schedulers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.services.FastDeleteService;
import org.deltafi.core.services.StorageConfigurationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@ConditionalOnProperty(value = "schedule.maintenance", havingValue = "true", matchIfMissing = true)
@ConditionalOnMissingBean(FastDeleteService.class)
@ConditionalOnBean(StorageConfigurationService.class)
@Service
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class StorageBucketScheduler {

    private final StorageConfigurationService storageConfigurationService;

    @Scheduled(fixedDelay = 5000)
    public void ensureBucketExists() {
        try {
            storageConfigurationService.ensureBucketExists();
        } catch (Exception e) {
            log.warn("Unable to verify storage bucket exists: {}", e.getMessage());
        }
    }
}
