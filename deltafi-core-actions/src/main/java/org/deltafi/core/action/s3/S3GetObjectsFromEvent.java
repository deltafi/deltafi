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

import org.deltafi.actionkit.action.error.ErrorResultException;
import org.deltafi.actionkit.action.transform.*;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.eventnotifications.s3.model.S3EventNotification;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.Map;
import java.util.Set;

@Component
public class S3GetObjectsFromEvent extends TransformAction<S3ClientParams> {
    private static final Set<Event> CREATED_EVENTS = Set.of(Event.S3_OBJECT_CREATED_PUT, Event.S3_OBJECT_CREATED_COPY,
            Event.S3_OBJECT_CREATED_POST, Event.S3_OBJECT_CREATED_COMPLETE_MULTIPART_UPLOAD);

    private final S3ClientProvider s3ClientProvider;

    public S3GetObjectsFromEvent(S3ClientProvider s3ClientProvider) {
        super("Gets objects from an S3 store from S3 event notifications and saves them as content");
        this.s3ClientProvider = s3ClientProvider;
    }

    @Override
    public TransformResultType transform(@NotNull ActionContext context, @NotNull S3ClientParams params,
            @NotNull TransformInput transformInput) {
        S3EventNotification s3Event = S3EventNotification.fromJson(transformInput.content(0).loadInputStream());

        TransformResults results = new TransformResults(context);

        s3Event.getRecords().stream()
                .filter(record -> CREATED_EVENTS.contains(Event.fromValue(record.getEventName())))
                .forEach(record -> copyContent(params, record.getS3().getBucket().getName(),
                        record.getS3().getObject().getKey(), results));

        return results;
    }

    private void copyContent(S3ClientParams params, String bucket, String key, TransformResults results) {
        S3Client s3Client = s3ClientProvider.getS3Client(params);

        try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucket).key(key).build())) {
            ChildTransformResult childTransformResult = new ChildTransformResult(results.getContext());
            childTransformResult.saveContent(response, key, response.response().contentType());
            childTransformResult.addMetadata(Map.of("bucket", bucket, "key", key,
                    "size", response.response().contentLength().toString()));
            results.add(childTransformResult);
        } catch (Exception e) {
            throw new ErrorResultException("Failed to copy S3 object to local storage", e.getMessage(), e);
        }
    }
}
