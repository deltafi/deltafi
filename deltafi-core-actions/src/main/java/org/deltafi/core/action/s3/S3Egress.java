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
import io.micrometer.common.util.StringUtils;
import lombok.*;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.egress.EgressAction;
import org.deltafi.actionkit.action.egress.EgressInput;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.egress.EgressResultType;
import org.deltafi.actionkit.action.error.ErrorResultException;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
public class S3Egress extends EgressAction<S3Egress.Parameters> {
    @Data
    @EqualsAndHashCode(callSuper = true)
    @ToString(callSuper = true)
    public static class Parameters extends ActionParameters {
        @JsonProperty(required = true)
        @JsonPropertyDescription("The S3 client parameters")
        private S3ClientParams s3ClientParams;

        @JsonProperty(required = true)
        @JsonPropertyDescription("Name of the bucket where content should be put")
        private String bucket;

        @JsonProperty
        @JsonPropertyDescription("Optional - name of the key to use, by default this will be the content name")
        private String key;

        @JsonProperty
        @JsonPropertyDescription("Optional - content type used in the Put Object Request, by default this will be the media type of the content")
        private String contentType;
    }

    private final S3ClientProvider clientProvider;

    public S3Egress(S3ClientProvider clientProvider) {
        super("Writes the content to an S3 store");
        this.clientProvider = clientProvider;
    }

    @Override
    public EgressResultType egress(@NotNull ActionContext context, @NotNull S3Egress.Parameters params,
            @NotNull EgressInput egressInput) {
        if (egressInput.getContent() == null) {
            throw new ErrorResultException("No content was provided");
        }

        ActionContent content = egressInput.getContent();
        String key = StringUtils.isNotBlank(params.getKey()) ? params.getKey() : content.getName();
        String contentType = StringUtils.isNotBlank(params.getContentType()) ? params.getContentType() : content.getMediaType();

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(params.getBucket())
                .key(key)
                .contentType(contentType)
                .contentLength(content.getSize())
                .build();

        S3Client s3Client = clientProvider.getS3Client(params.getS3ClientParams());

        try {
            RequestBody body = RequestBody.fromContentProvider(
                    ContentStreamProvider.fromInputStream(content.loadInputStream()), content.getMediaType());
            s3Client.putObject(putObjectRequest, body);
        } catch (Exception e) {
            String message = String.format("Failed to put the content to S3 bucket %s with key %s", params.getBucket(), key);
            throw new ErrorResultException("Failed to put the content in S3", message, e);
        }

        return new EgressResult(context);
    }
}
