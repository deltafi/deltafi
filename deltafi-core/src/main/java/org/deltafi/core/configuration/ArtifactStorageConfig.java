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
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.storage.s3.ObjectStorageService;
import org.deltafi.common.storage.s3.minio.MinioObjectStorageService;
import org.deltafi.common.storage.s3.minio.MinioProperties;
import org.deltafi.core.artifact.ArtifactRegistryBucketInitializer;
import org.deltafi.core.artifact.ArtifactRegistryStorageService;
import org.deltafi.core.artifact.ArtifactStorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configures artifact registry storage, optionally pointing at a separate S3-compatible endpoint.
 *
 * <p>When {@code artifact.storage.url} is blank (the default), artifact storage reuses the primary
 * {@link ObjectStorageService}. When set, a dedicated {@link MinioClient} is built for artifact storage.
 */
@Configuration
@EnableConfigurationProperties(ArtifactStorageProperties.class)
public class ArtifactStorageConfig {

    @Bean
    public ArtifactRegistryStorageService artifactRegistryStorageService(
            ObjectStorageService primaryObjectStorageService,
            ArtifactStorageProperties props) {
        ObjectStorageService oss = StringUtils.isBlank(props.getUrl())
                ? primaryObjectStorageService
                : new MinioObjectStorageService(buildMinioClient(props), adaptMinioProperties(props));
        return new ArtifactRegistryStorageService(oss, props.getBucketName());
    }

    @Bean
    @ConditionalOnProperty(name = "local.storage.artifacts", havingValue = "true", matchIfMissing = true)
    public ArtifactRegistryBucketInitializer artifactRegistryBucketInitializer(
            MinioClient primaryMinioClient,
            ArtifactStorageProperties props) {
        MinioClient client = StringUtils.isBlank(props.getUrl()) ? primaryMinioClient : buildMinioClient(props);
        return new ArtifactRegistryBucketInitializer(client, props.getBucketName());
    }

    private MinioClient buildMinioClient(ArtifactStorageProperties props) {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectionPool(new ConnectionPool(32, 5, TimeUnit.MINUTES))
                .build();
        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(props.getUrl())
                .httpClient(okHttpClient);
        if (!StringUtils.isAllBlank(props.getAccessKey(), props.getSecretKey())) {
            builder.credentials(props.getAccessKey(), props.getSecretKey());
        }
        return builder.build();
    }

    private MinioProperties adaptMinioProperties(ArtifactStorageProperties props) {
        MinioProperties mp = new MinioProperties();
        mp.setUrl(props.getUrl());
        mp.setAccessKey(props.getAccessKey());
        mp.setSecretKey(props.getSecretKey());
        mp.setPartSize(props.getPartSize());
        return mp;
    }
}
