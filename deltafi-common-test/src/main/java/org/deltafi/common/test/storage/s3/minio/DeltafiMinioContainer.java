/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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

import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketLifecycleArgs;
import io.minio.messages.*;

import java.util.List;

public class DeltafiMinioContainer extends MinioContainer {
    public DeltafiMinioContainer(String accessKey, String secretKey) {
        super(accessKey, secretKey);
    }

    public MinioClient start(String defaultBucket) {
        super.start();

        MinioClient minioClient = MinioClient.builder()
                .endpoint(getHost(), getMappedPort(), false)
                .credentials(accessKey, secretKey)
                .build();

        try {
            // Create the default bucket (created by Helm in production - see MinIO config in charts/deltafi/values.yaml).
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(defaultBucket).build());
            minioClient.setBucketLifecycle(SetBucketLifecycleArgs.builder().bucket(defaultBucket).config(
                    new LifecycleConfiguration(List.of(new LifecycleRule(Status.ENABLED, null,
                            new Expiration((ResponseDate) null, 1, null), new RuleFilter(""), "AgeOff", null, null, null)))).build());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return minioClient;
    }
}
