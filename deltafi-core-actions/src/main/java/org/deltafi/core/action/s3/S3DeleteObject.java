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
import org.deltafi.common.types.ActionOptions;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

@Component
public class S3DeleteObject extends TransformAction<S3DeleteObject.Parameters> {
    @Data
    @EqualsAndHashCode(callSuper = true)
    @ToString(callSuper = true)
    public static class Parameters extends ActionParameters {
        @JsonProperty(required = true)
        @JsonPropertyDescription("The S3 client parameters")
        private S3ClientParams s3ClientParams;

        @JsonProperty(required = true)
        @JsonPropertyDescription("The bucket")
        private String bucket;

        @JsonProperty(required = true)
        @JsonPropertyDescription("The key")
        private String key;
    }

    private final S3ClientProvider s3ClientProvider;

    public S3DeleteObject(S3ClientProvider s3ClientProvider) {
        super(ActionOptions.builder()
                .description("Deletes objects from an S3 store.")
                .outputSpec(ActionOptions.OutputSpec.builder()
                        .passthrough(true)
                        .build())
                .errors("On failure to delete the object from the S3 store")
                .notes("""
                        This action may be used after data is ingressed with S3Ingress to prevent duplicates on
                        system failures.""")
                .build());
        this.s3ClientProvider = s3ClientProvider;
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull S3DeleteObject.Parameters params,
            @NotNull TransformInput transformInput) {
        S3Client s3Client = s3ClientProvider.getS3Client(params.getS3ClientParams());

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(params.getBucket()).key(params.getKey())
                    .build());
            return new TransformResult(context, transformInput.content());
        } catch (Exception e) {
            throw new ErrorResultException("Failed to delete S3 object from the S3 store", e.getMessage(), e);
        }
    }
}
