package org.deltafi.ingress.config;

import io.minio.MinioClient;
import org.deltafi.common.properties.MinioProperties;
import org.deltafi.common.storage.s3.ObjectStorageService;
import org.deltafi.common.storage.s3.minio.MinioObjectStorageService;

import javax.enterprise.inject.Produces;

public class MinioConfig {

    @Produces
    public ObjectStorageService objectStorageService(MinioProperties minioProperties) {
        MinioClient minioClient = MinioClient.builder()
                .endpoint(minioProperties.getUrl()).credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey()).build();

        return new MinioObjectStorageService(minioClient, minioProperties.getPartSize());
    }
}
