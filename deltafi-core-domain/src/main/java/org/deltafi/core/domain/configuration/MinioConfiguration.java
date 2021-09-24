package org.deltafi.core.domain.configuration;

import io.minio.MinioClient;
import lombok.Data;
import org.deltafi.common.storage.s3.ObjectStorageService;
import org.deltafi.common.storage.s3.minio.MinioObjectStorageService;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URISyntaxException;

@Configuration
@ConfigurationProperties(prefix = "minio")
@Data
public class MinioConfiguration {
    String url;
    String accessKey;
    String secretKey;
    long partSize;

    @Bean
    public ObjectStorageService objectStorageService() throws URISyntaxException {
        MinioClient minioClient = MinioClient.builder().endpoint(url).credentials(accessKey, secretKey).build();

        return new MinioObjectStorageService(minioClient, partSize);
    }
}
