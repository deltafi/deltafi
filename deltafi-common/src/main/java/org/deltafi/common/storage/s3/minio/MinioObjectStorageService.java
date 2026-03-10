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
package org.deltafi.common.storage.s3.minio;

import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.ErrorResponse;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.BoundedInputStream;
import org.deltafi.common.storage.s3.MissingContentException;
import org.deltafi.common.storage.s3.ObjectReference;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.storage.s3.ObjectStorageService;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** {@link ObjectStorageService} implementation backed by MinIO. */
@RequiredArgsConstructor
@Slf4j
public class MinioObjectStorageService implements ObjectStorageService {

    /** Log message used when a put operation fails. */
    public static final String FAILED_TO_SAVE_INCOMING_OBJECT = "Failed to save incoming object";
    /** The MinIO client used for all storage operations. */
    protected final MinioClient minioClient;
    /** Configuration properties for MinIO connections. */
    protected final MinioProperties minioProperties;

    @Override
    public InputStream getObject(ObjectReference objectReference) throws ObjectStorageException {
        try {
            GetObjectArgs.Builder builder = GetObjectArgs.builder()
                    .bucket(objectReference.getBucket())
                    .object(objectReference.getName())
                    .offset(objectReference.getOffset());

            if (objectReference.getSize() != ObjectReference.UNKNOWN_SIZE) {
                builder.length(objectReference.getSize());
            }

            return minioClient.getObject(builder.build());
        } catch (ErrorResponseException e) {
            ErrorResponse errorResponse = e.errorResponse();
            // MinioClient maps 404 responses to an ErrorResponse with NoSuchKey as the code
            if (errorResponse != null && "NoSuchKey".equals(errorResponse.code())) {
                throw new MissingContentException(errorResponse.message());
            }

            throw new ObjectStorageException("Failed to get object from minio", e);
        } catch (InsufficientDataException | InternalException | InvalidKeyException | InvalidResponseException | IOException | NoSuchAlgorithmException | ServerException | XmlParserException e) {
            throw new ObjectStorageException("Failed to get object from minio", e);
        }
    }

    @Override
    public ObjectReference putObject(ObjectReference objectReference, InputStream inputStream) throws ObjectStorageException {
        try {
            BoundedInputStream boundedInputStream = BoundedInputStream.builder().setInputStream(inputStream).get();
            ObjectWriteResponse objectWriteResponse = minioClient.putObject(PutObjectArgs.builder()
                    .bucket(objectReference.getBucket())
                    .object(objectReference.getName())
                    .stream(boundedInputStream, objectReference.getSize(), minioProperties.getPartSize())
                    .build());

            if (objectWriteResponse == null) {
                throw new ObjectStorageException("Failed to send the incoming data to minio");
            }

            return new ObjectReference(objectWriteResponse.bucket(), objectWriteResponse.object(), 0,
                    boundedInputStream.getCount());
        } catch (ErrorResponseException | InsufficientDataException | InternalException |
                InvalidKeyException | InvalidResponseException | IOException | NoSuchAlgorithmException |
                ServerException | XmlParserException e) {
            log.error(FAILED_TO_SAVE_INCOMING_OBJECT, e);
            throw new ObjectStorageException("Failed to send incoming data to minio", e);
        }
    }

    @Override
    public void putObjects(String bucket, Map<ObjectReference, InputStream> inputStreamMap) throws ObjectStorageException {
        List<ObjectReference> completed = new ArrayList<>();
        try {
            for (Map.Entry<ObjectReference, InputStream> entry : inputStreamMap.entrySet()) {
                this.putObject(entry.getKey(), entry.getValue());
                completed.add(entry.getKey());
            }
        } catch (Exception e) {
            log.error("Failed to upload many objects in bucket {}, removing {} of {} items that did complete",
                    bucket, completed.size(), inputStreamMap.size(), e);
            rollbackPartialFailure(completed);
            throw new ObjectStorageException("Failed to send incoming data to minio", e);
        }
    }

    @Override
    public void removeObject(ObjectReference objectReference) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(objectReference.getBucket()).object(objectReference.getName()).build());
        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException |
                InvalidResponseException | IOException | NoSuchAlgorithmException | ServerException | XmlParserException e) {
            log.error("Failed to remove object {} from bucket {}", objectReference.getName(), objectReference.getBucket());
        }
    }

    @Override
    public boolean removeObjects(String bucket, List<String> objectNames) {
        List<DeleteObject> objectsInStorage = objectNames.stream().map(DeleteObject::new).toList();

        log.info("Sending command to delete {} objects in storage from minio", objectsInStorage.size());
        Iterable<Result<DeleteError>> removeResults =
                minioClient.removeObjects(RemoveObjectsArgs.builder().bucket(bucket).objects(objectsInStorage).build());

        boolean hasError = false;
        for (Result<DeleteError> removeResult : removeResults) {
            hasError = true;
            try {
                DeleteError error = removeResult.get();
                log.error("Failed to remove object {} with an error of {}", error.objectName(), error.message());
            } catch (ErrorResponseException | ServerException | InsufficientDataException | InternalException | InvalidKeyException | InvalidResponseException | IOException | NoSuchAlgorithmException | XmlParserException e) {
                log.error("Failed to remove object: {}", e.getMessage());
            }
        }

        return !hasError;
    }

    @Override
    public List<String> listKeys(String bucket, String prefix) {
        List<String> keys = new ArrayList<>();
        Iterable<Result<Item>> results = minioClient.listObjects(ListObjectsArgs.builder()
                .bucket(bucket)
                .prefix(prefix)
                .recursive(true)
                .build());
        for (Result<Item> result : results) {
            try {
                keys.add(result.get().objectName());
            } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException |
                     InvalidResponseException | IOException | NoSuchAlgorithmException | ServerException |
                     XmlParserException e) {
                log.warn("Error reading list result under prefix {}/{}: {}", bucket, prefix, e.getMessage());
            }
        }
        return keys;
    }

    @Override
    public long getTotalSize(ObjectReference objectReference) {
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(objectReference.getBucket()).object(objectReference.getName()).build());
            return stat.size();
        } catch (ErrorResponseException | ServerException | InsufficientDataException | InternalException | InvalidKeyException | InvalidResponseException | IOException | NoSuchAlgorithmException | XmlParserException e) {
            log.error("Failed to stat object {} in bucket {}", objectReference.getName(), objectReference.getBucket());
            return -1;
        }
    }

    private void rollbackPartialFailure(List<ObjectReference> objectReferences) {
        for (ObjectReference objectReference : objectReferences) {
            try {
                this.removeObject(objectReference);
            } catch  (Exception e) {
                // ignore errors, try to remove them all
                log.warn("Failed to remove object {} from bucket {}", objectReference.getName(), objectReference.getBucket());
            }
        }
    }
}
