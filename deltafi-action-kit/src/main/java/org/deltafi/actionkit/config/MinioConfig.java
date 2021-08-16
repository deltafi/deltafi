package org.deltafi.actionkit.config;

import io.minio.MinioClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

@Singleton
public class MinioConfig {

    @ConfigProperty(name = "minio.url")
    String minioUrl;

    @ConfigProperty(name = "accesskey")
    String accessKey;

    @ConfigProperty(name = "secretkey")
    String secretKey;

    @Produces
    @Dependent
    public MinioClient produceMinioClient() {
        return MinioClient.builder()
                .endpoint(minioUrl)
                .credentials(accessKey, secretKey)
                .build();
    }

}
