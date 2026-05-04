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
package org.deltafi.core.action.s3;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.*;
import org.deltafi.actionkit.action.error.ErrorResultException;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.transform.*;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.ArrayList;

@Component
public class S3CopyObject extends TransformAction<S3CopyObject.Parameters> {
    @Data
    @EqualsAndHashCode(callSuper = true)
    @ToString(callSuper = true)
    public static class Parameters extends ActionParameters {
        @JsonProperty(required = true)
        @JsonPropertyDescription("The S3 client parameters")
        private S3ClientParams s3ClientParams;

        @JsonProperty(required = true)
        @JsonPropertyDescription("The source bucket")
        private String sourceBucket;

        @JsonProperty(required = true)
        @JsonPropertyDescription("The source key")
        private String sourceKey;

        @JsonProperty(required = true)
        @JsonPropertyDescription("The destination bucket")
        private String destinationBucket;

        @JsonProperty(required = true)
        @JsonPropertyDescription("The destination key")
        private String destinationKey;

        @JsonPropertyDescription("Enable multi-upload (slower, but required for objects > 5 GB)")
        private boolean enableMultiUpload;

        @JsonProperty(defaultValue = "5368709120")
        @JsonPropertyDescription("Objects this size or greater use multi-upload")
        private long multiUploadSize = 5368709120L; // 5 GB

        @JsonProperty(defaultValue = "104857600")
        @JsonPropertyDescription("Size of each part for multi-upload")
        private long multiUploadPartSize = 104857600L; // 100 MB
    }

    private final S3ClientProvider s3ClientProvider;

    public S3CopyObject(S3ClientProvider s3ClientProvider) {
        super("Copies objects within an S3 store. Content is passed through this action.");
        this.s3ClientProvider = s3ClientProvider;
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull S3CopyObject.Parameters params,
            @NotNull TransformInput transformInput) {
        if (params.getSourceBucket().equals(params.getDestinationBucket()) &&
                params.getSourceKey().equals(params.getDestinationKey())) {
            throw new ErrorResultException(
                    "Failed to copy object in the S3 store. Source and destination are the same.");
        }

        S3Client s3Client = s3ClientProvider.getS3Client(params.getS3ClientParams());

        long objectSize = -1;
        if (params.isEnableMultiUpload()) {
            try {
                HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                        .bucket(params.getSourceBucket()).key(params.getSourceKey()).build());
                objectSize = response.contentLength();
            } catch (Exception e) {
                throw new ErrorResultException("Failed to copy object in the S3 store. Unable to get content length.",
                        e.getMessage(), e);
            }
        }

        try {
            if (objectSize < params.getMultiUploadSize()) {
                s3Client.copyObject(CopyObjectRequest.builder().sourceBucket(params.getSourceBucket())
                        .sourceKey(params.getSourceKey()).destinationBucket(params.getDestinationBucket())
                        .destinationKey(params.getDestinationKey()).build());
            } else {
                copyObjectWithMultiPart(s3Client, params.getSourceBucket(), params.getSourceKey(),
                        params.getDestinationBucket(), params.getDestinationKey(), objectSize,
                        params.getMultiUploadPartSize());
            }
        } catch (Exception e) {
            throw new ErrorResultException("Failed to copy object in the S3 store", e.getMessage(), e);
        }

        return new TransformResult(context, transformInput.content());
    }

    private void copyObjectWithMultiPart(S3Client s3Client, String sourceBucket, String sourceKey,
            String destinationBucket, String destinationKey, long objectSize, long partSize) {
        CreateMultipartUploadResponse response = s3Client.createMultipartUpload(
                CreateMultipartUploadRequest.builder().bucket(destinationBucket).key(destinationKey).build());

        try {
            ArrayList<CompletedPart> parts = new ArrayList<>();
            for (long sourceIndex = 0; sourceIndex < objectSize; sourceIndex += partSize) {
                UploadPartCopyResponse uploadPartCopyResponse = s3Client.uploadPartCopy(UploadPartCopyRequest.builder()
                        .uploadId(response.uploadId())
                        .sourceBucket(sourceBucket)
                        .sourceKey(sourceKey)
                        .destinationBucket(destinationBucket)
                        .destinationKey(destinationKey)
                        .partNumber(parts.size() + 1)
                        .copySourceRange(String.format("bytes=%d-%d", sourceIndex,
                                Math.min(sourceIndex + partSize, objectSize) - 1))
                        .build());

                parts.add(CompletedPart.builder().partNumber(parts.size() + 1)
                        .eTag(uploadPartCopyResponse.copyPartResult().eTag()).build());
            }

            s3Client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                    .uploadId(response.uploadId())
                    .bucket(destinationBucket)
                    .key(destinationKey)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build())
                    .build());
        } catch (SdkException e) {
            s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder().bucket(destinationBucket)
                    .key(destinationKey).uploadId(response.uploadId()).build());
            throw e;
        }
    }
}
