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
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.deltafi.core.services.StorageConfigurationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Optional;

@ConditionalOnProperty(value = "schedule.propertySync", havingValue = "true", matchIfMissing = true)
@Service
@EnableScheduling
@RequiredArgsConstructor
public class PropertySyncScheduler {

    private final DeltaFiPropertiesService propertyService;

    private final Optional<StorageConfigurationService> storageConfigurationService;


    @Scheduled(fixedDelay = 5000)
    public void syncProperties() {
        propertyService.refreshProperties();
        storageConfigurationService.ifPresent(StorageConfigurationService::updateAgeOffIfChanged);
    }
}
