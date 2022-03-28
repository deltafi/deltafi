package org.deltafi.actionkit.config;

import io.minio.MinioClient;
import org.deltafi.common.properties.MinioProperties;
import org.deltafi.common.storage.s3.ObjectStorageService;
import org.deltafi.common.storage.s3.minio.MinioObjectStorageService;

import javax.enterprise.inject.Produces;

public class MinioConfig {
    @Produces
    public MinioClient minioClient(MinioProperties minioProperties) {
        return MinioClient.builder()
                .endpoint(minioProperties.getUrl())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey()).build();
    }

    @Produces
    public ObjectStorageService minioObjectStorageService(MinioClient minioClient, MinioProperties minioProperties) {
        return new MinioObjectStorageService(minioClient, minioProperties);
    }
}