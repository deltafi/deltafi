/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.common.test.storage.s3.minio;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;

import java.time.Duration;

public class MinioContainer extends GenericContainer<MinioContainer> {
    private static final int DEFAULT_PORT = 9000;

    final protected String accessKey;
    final protected String secretKey;

    public MinioContainer(String accessKey, String secretKey) {
        super("minio/minio:RELEASE.2022-08-25T07-17-05Z");

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
