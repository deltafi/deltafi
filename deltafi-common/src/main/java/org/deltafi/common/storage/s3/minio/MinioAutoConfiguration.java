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
package org.deltafi.common.storage.s3.minio;

import io.minio.MinioClient;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

/** Auto-configures the MinIO client and object storage service beans. */
@AutoConfiguration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioAutoConfiguration {

    /**
     * Create a MinIO client configured from the application properties.
     * @param minioProperties the MinIO connection properties
     * @return a configured MinIO client
     */
    @Bean
    public MinioClient minioClient(MinioProperties minioProperties) {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectionPool(new ConnectionPool(32, 5, TimeUnit.MINUTES))
                .build();

        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(minioProperties.getUrl())
                .httpClient(okHttpClient);

        if (!StringUtils.isAllBlank(minioProperties.getAccessKey(), minioProperties.getSecretKey())) {
            builder.credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey());
        }

        return builder.build();
    }

    /**
     * Create the MinIO-backed object storage service if no other implementation is present.
     * @param minioClient the MinIO client
     * @param minioProperties the MinIO connection properties
     * @return a MinIO object storage service
     */
    @Bean
    @ConditionalOnMissingBean
    public MinioObjectStorageService minioObjectStorageService(MinioClient minioClient,
                                                               MinioProperties minioProperties) {
        return new MinioObjectStorageService(minioClient, minioProperties);
    }
}
