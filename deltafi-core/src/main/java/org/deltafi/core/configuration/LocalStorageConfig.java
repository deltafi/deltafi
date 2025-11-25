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
package org.deltafi.core.configuration;

import io.minio.MinioClient;
import org.deltafi.common.content.StorageProperties;
import org.deltafi.core.repo.PendingDeleteRepo;
import org.deltafi.core.schedulers.PendingDeleteCleanupScheduler;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.deltafi.core.services.LocalContentStorageService;
import org.deltafi.core.services.StorageConfigurationService;
import org.deltafi.core.services.SystemService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "local.storage.content", havingValue = "true", matchIfMissing = true)
public class LocalStorageConfig {

    @Bean
    public LocalContentStorageService localContentStorageService(PendingDeleteRepo pendingDeleteRepo, SystemService systemService, StorageProperties storageProperties) {
        return new LocalContentStorageService(pendingDeleteRepo, systemService, storageProperties);
    }

    @Bean
    @ConditionalOnProperty(value = "schedule.maintenance", havingValue = "true", matchIfMissing = true)
    public PendingDeleteCleanupScheduler pendingDeleteCleanupScheduler(SystemService systemService, PendingDeleteRepo pendingDeleteRepo) {
        return new PendingDeleteCleanupScheduler(systemService, pendingDeleteRepo);
    }

    @Bean
    @ConditionalOnProperty(value = "schedule.maintenance", havingValue = "true", matchIfMissing = true)
    public StorageConfigurationService storageConfigurationService(MinioClient minioClient, DeltaFiPropertiesService deltaFiPropertiesService, StorageProperties storageProperties) {
        return new StorageConfigurationService(minioClient, deltaFiPropertiesService, storageProperties);
    }
}
