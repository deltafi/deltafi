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
                .endpoint(getContainerIpAddress(), getMappedPort(), false)
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
