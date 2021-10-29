package org.deltafi.core.domain.configuration;

import io.minio.MinioClient;
import org.deltafi.common.properties.MinioProperties;
import org.deltafi.common.storage.s3.ObjectStorageService;
import org.deltafi.common.storage.s3.minio.MinioObjectStorageService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URISyntaxException;

@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfiguration {

    @Bean
    public ObjectStorageService objectStorageService(MinioProperties minioProperties) throws URISyntaxException {
        MinioClient minioClient = MinioClient.builder()
                .endpoint(minioProperties.getUrl())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey()).build();

        return new MinioObjectStorageService(minioClient, minioProperties.getPartSize());
    }
}
