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
package org.deltafi.core.artifact;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Ensures the artifact registry MinIO bucket exists at startup.
 * Registered as a bean only when local artifact storage is active (see {@code ArtifactStorageConfig}).
 */
@RequiredArgsConstructor
@Slf4j
public class ArtifactRegistryBucketInitializer {

    private final MinioClient minioClient;
    private final String bucketName;

    @PostConstruct
    public void ensureBucket() {
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("Created artifact registry bucket: {}", bucketName);
            }
        } catch (Exception e) {
            throw new ArtifactStorageException(
                    "Failed to ensure artifact registry bucket: " + bucketName, e);
        }
    }
}
