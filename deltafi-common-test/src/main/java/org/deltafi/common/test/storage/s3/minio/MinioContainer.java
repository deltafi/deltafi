package org.deltafi.common.test.storage.s3.minio;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;

import java.time.Duration;

public class MinioContainer extends GenericContainer<MinioContainer> {
    private static final int DEFAULT_PORT = 9000;

    protected String accessKey;
    protected String secretKey;

    public MinioContainer(String accessKey, String secretKey) {
        super("minio/minio:edge");

        this.accessKey = accessKey;
        this.secretKey = secretKey;

        addExposedPort(DEFAULT_PORT);

        withEnv("MINIO_ROOT_USER", accessKey);
        withEnv("MINIO_ROOT_PASSWORD", secretKey);
        withCommand("server", "/data");

        setWaitStrategy(new HttpWaitStrategy()
                .forPort(DEFAULT_PORT)
                .forPath("/minio/health/ready")
                .withStartupTimeout(Duration.ofMinutes(2)));
    }

    public Integer getMappedPort() {
        return getMappedPort(DEFAULT_PORT);
    }
}
