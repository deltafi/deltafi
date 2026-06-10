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
package org.deltafi.core.configuration;

import io.minio.MinioClient;
import org.deltafi.common.content.StorageProperties;
import org.deltafi.core.repo.PendingDeleteRepo;
import org.deltafi.core.schedulers.StorageBucketScheduler;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.deltafi.core.services.FastDeleteService;
import org.deltafi.core.services.StorageConfigurationService;
import org.deltafi.core.services.SystemService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class StorageConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(StorageConfig.class, StorageBucketScheduler.class)
            .withBean(MinioClient.class, this::mockMinioClient)
            .withBean(DeltaFiPropertiesService.class, () -> Mockito.mock(DeltaFiPropertiesService.class))
            .withBean(StorageProperties.class, this::mockStorageProperties)
            .withBean(PendingDeleteRepo.class, () -> Mockito.mock(PendingDeleteRepo.class))
            .withBean(SystemService.class, () -> Mockito.mock(SystemService.class));

    private MinioClient mockMinioClient() {
        MinioClient client = Mockito.mock(MinioClient.class);
        try {
            Mockito.when(client.bucketExists(Mockito.any())).thenReturn(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return client;
    }

    private StorageProperties mockStorageProperties() {
        StorageProperties props = Mockito.mock(StorageProperties.class);
        Mockito.when(props.bucketName()).thenReturn("test-bucket");
        return props;
    }

    @Test
    void externalStorageMode_excludesSchedulerAndConfigService() {
        contextRunner.withPropertyValues(
                "local.storage.content=false",
                "schedule.maintenance=true"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(StorageConfigurationService.class);
            assertThat(context).doesNotHaveBean(FastDeleteService.class);
            assertThat(context).doesNotHaveBean(StorageBucketScheduler.class);
        });
    }

    @Test
    void inMemoryMode_activatesScheduler() {
        contextRunner.withPropertyValues(
                "local.storage.content=true",
                "storage.in-memory=true",
                "schedule.maintenance=true"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(StorageConfigurationService.class);
            assertThat(context).doesNotHaveBean(FastDeleteService.class);
            assertThat(context).hasSingleBean(StorageBucketScheduler.class);
        });
    }

    @Test
    void normalMode_excludesScheduler() {
        contextRunner.withPropertyValues(
                "local.storage.content=true",
                "storage.in-memory=false",
                "schedule.maintenance=true"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(StorageConfigurationService.class);
            assertThat(context).hasSingleBean(FastDeleteService.class);
            assertThat(context).doesNotHaveBean(StorageBucketScheduler.class);
        });
    }

    @Test
    void workerMode_excludesScheduler() {
        contextRunner.withPropertyValues(
                "local.storage.content=true",
                "storage.in-memory=true",
                "schedule.maintenance=false"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(StorageConfigurationService.class);
            assertThat(context).doesNotHaveBean(StorageBucketScheduler.class);
        });
    }
}
