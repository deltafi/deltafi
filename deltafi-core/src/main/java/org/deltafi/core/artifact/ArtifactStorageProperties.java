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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for artifact registry storage.
 *
 * <p>When {@code url} is blank (the default), artifact storage shares the primary
 * MinIO/S3-compatible endpoint configured via {@code minio.*} properties.
 * Setting {@code url} points artifact storage at a separate S3-compatible endpoint.
 *
 * <p>Spring relaxed-binding maps environment variables:
 * <ul>
 *   <li>{@code ARTIFACT_STORAGE_URL} → {@code artifact.storage.url}
 *   <li>{@code ARTIFACT_STORAGE_ACCESS_KEY} → {@code artifact.storage.access-key}
 *   <li>{@code ARTIFACT_STORAGE_SECRET_KEY} → {@code artifact.storage.secret-key}
 *   <li>{@code ARTIFACT_STORAGE_BUCKET_NAME} → {@code artifact.storage.bucket-name}
 * </ul>
 */
@ConfigurationProperties(prefix = "artifact.storage")
@Data
public class ArtifactStorageProperties {
    /** Endpoint URL. Empty/null = fall back to the primary MinioClient. */
    private String url = "";
    private String accessKey = "";
    private String secretKey = "";
    /** S3 bucket for artifact storage. */
    private String bucketName = "deltafi-artifacts";
    private long partSize = 5_242_880L;
}
