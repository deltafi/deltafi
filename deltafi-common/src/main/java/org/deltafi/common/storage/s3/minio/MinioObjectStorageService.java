/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.CountingInputStream;
import org.deltafi.common.properties.MinioProperties;
import org.deltafi.common.storage.s3.ObjectReference;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.storage.s3.ObjectStorageService;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
public class MinioObjectStorageService implements ObjectStorageService {
    protected final MinioClient minioClient;
    protected final MinioProperties minioProperties;

    @Override
    public InputStream getObject(ObjectReference objectReference) throws ObjectStorageException {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(objectReference.getBucket())
                    .object(objectReference.getName())
                    .offset(objectReference.getOffset())
                    .length(objectReference.getSize())
                    .build());
        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException | InvalidResponseException | IOException | NoSuchAlgorithmException | ServerException | XmlParserException e) {
            throw new ObjectStorageException("Failed to get object from minio", e);
        }
    }

    @Override
    public ObjectReference putObject(ObjectReference objectReference, InputStream inputStream) throws ObjectStorageException {
        CountingInputStream countingInputStream = new CountingInputStream(inputStream);

        try {
            ObjectWriteResponse objectWriteResponse = minioClient.putObject(PutObjectArgs.builder()
                    .bucket(objectReference.getBucket())
                    .object(objectReference.getName())
                    .stream(countingInputStream, objectReference.getSize(), minioProperties.getPartSize())
                    .build());
            return new ObjectReference(objectWriteResponse.bucket(), objectWriteResponse.object(), 0,
                    countingInputStream.getByteCount());
        } catch (ErrorResponseException | InsufficientDataException | InternalException |
                InvalidKeyException | InvalidResponseException | IOException | NoSuchAlgorithmException |
                ServerException | XmlParserException e) {
            log.error("Failed to save incoming object", e);
            throw new ObjectStorageException("Failed to send incoming data to minio", e);
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                log.error("Failed to close input stream: {}", e.getMessage());
            }
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
        List<DeleteObject> objectsInStorage = objectNames.stream().map(DeleteObject::new).collect(Collectors.toList());

        log.info("Sending command to delete " + objectsInStorage.size() + " objects in storage from minio");
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
}
