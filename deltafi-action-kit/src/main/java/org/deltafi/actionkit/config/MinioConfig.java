package org.deltafi.actionkit.config;

import io.minio.MinioClient;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import org.deltafi.common.storage.s3.ObjectStorageService;
import org.deltafi.common.storage.s3.minio.MinioObjectStorageService;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.inject.Produces;

public class MinioConfig {
    @ConfigMapping(prefix = "minio")
    public interface Config {
        String url();

        @WithDefault("100000000")
        long partSize();
    }

    @ConfigProperty(name = "accesskey") // not under minio config mapping since it's pulled from minio-keys secret by quarkus-kubernetes-config extension
    String accessKey;

    @ConfigProperty(name = "secretkey") // not under minio config mapping since it's pulled from minio-keys secret by quarkus-kubernetes-config extension
    String secretKey;

    @Produces
    public ObjectStorageService objectStorageService(Config config) {
        MinioClient minioClient = MinioClient.builder().endpoint(config.url()).credentials(accessKey, secretKey).build();

        return new MinioObjectStorageService(minioClient, config.partSize());
    }
}
