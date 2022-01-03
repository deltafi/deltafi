package org.deltafi.core.domain.configuration;

import io.minio.MinioClient;
import org.deltafi.common.properties.MinioProperties;
import org.deltafi.common.storage.s3.ObjectStorageService;
import org.deltafi.common.storage.s3.minio.MinioObjectStorageService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfiguration {
    @Bean
    public MinioClient minioClient(MinioProperties minioProperties) {
        return MinioClient.builder()
                .endpoint(minioProperties.getUrl())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey()).build();
    }

    @Bean
    public ObjectStorageService objectStorageService(MinioClient minioClient, MinioProperties minioProperties) {
        return new MinioObjectStorageService(minioClient, minioProperties);
    }
}
