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
import org.deltafi.actionkit.action.ingress.*;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionOptions;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

@Component
public class S3Ingress extends TimedIngressAction<S3Ingress.Parameters> {
    @Data
    @EqualsAndHashCode(callSuper = true)
    @ToString(callSuper = true)
    public static class Parameters extends ActionParameters {
        @JsonProperty(required = true)
        @JsonPropertyDescription("The S3 client parameters")
        private S3ClientParams s3ClientParams;

        @JsonProperty(required = true)
        @JsonPropertyDescription("The bucket to poll")
        private String bucket;

        @JsonPropertyDescription("Optional - prefix that keys must begin with")
        private String prefix;

        @JsonProperty(defaultValue = "5000")
        @JsonPropertyDescription("Maximum number of objects to ingress per poll")
        private int maxObjects = 5000;

        @JsonProperty(defaultValue = "1000")
        @JsonPropertyDescription("Maximum number of keys to return per S3 request. Must be <= 1000")
        private int batchSize = 1000;
    }

    // Seconds since object was last modified before it's ingressed. Accounts for clock drift and lastModifiedTime
    // having only seconds resolution.
    static final int SETTLING_SECONDS = 5;

    private final S3ClientProvider s3ClientProvider;
    private final Clock clock;

    public S3Ingress(S3ClientProvider s3ClientProvider, Clock clock) {
        super(ActionOptions.builder()
                .description("Polls an S3 bucket for objects to ingress")
                .notes("""
                        Under normal circumstances, this action will only ingress objects that were added or modified
                        since its last poll. It is recommended, however, that an S3DeleteObject action be used in the
                        subscribing transform to prevent duplicates when system failures occur.""")
                .build());
        this.s3ClientProvider = s3ClientProvider;
        this.clock = clock;
    }

    @Override
    public IngressResultType ingress(@NotNull ActionContext context, @NotNull Parameters params) {
        S3Client s3Client = s3ClientProvider.getS3Client(params.getS3ClientParams());

        IngressResult ingressResult = new IngressResult(context);

        Instant lastPollTime = context.getMemo() != null ? Instant.parse(context.getMemo()) : Instant.ofEpochMilli(0);
        Instant currentPollTime = Instant.now(clock);

        ListObjectsV2Response listObjectsV2Response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(params.getBucket()).prefix(params.getPrefix()).maxKeys(params.getBatchSize()).build());

        outerLoop:
        while (ingressResult.getIngressResultItems().size() < params.getMaxObjects()) {
            for (S3Object s3Object : listObjectsV2Response.contents()) {
                if (s3Object.lastModified().isBefore(lastPollTime.minusSeconds(SETTLING_SECONDS)) ||
                        !s3Object.lastModified().isBefore(currentPollTime.minusSeconds(SETTLING_SECONDS))) {
                    continue;
                }

                IngressResultItem resultItem = new IngressResultItem(context, s3Object.key());
                try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(
                        GetObjectRequest.builder().bucket(params.getBucket()).key(s3Object.key()).build())) {
                    resultItem.saveContent(response, s3Object.key(), response.response().contentType());
                    resultItem.addMetadata(Map.of("bucket", params.getBucket(), "key", s3Object.key(),
                            "size", s3Object.size().toString()));
                } catch (IOException e) {
                    return IngressResult.unhealthy(context, "Unable to close S3 response: " + e.getMessage());
                }
                ingressResult.addItem(resultItem);

                if (ingressResult.getIngressResultItems().size() == params.getMaxObjects()) {
                    break outerLoop;
                }
            }

            if (!listObjectsV2Response.isTruncated()) {
                break;
            }

            listObjectsV2Response = s3Client.listObjectsV2(ListObjectsV2Request.builder().bucket(params.getBucket())
                    .prefix(params.getPrefix()).maxKeys(params.getBatchSize())
                    .continuationToken(listObjectsV2Response.nextContinuationToken()).build());
        }

        ingressResult.setMemo(currentPollTime.toString());

        return ingressResult;
    }
}
