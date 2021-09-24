package org.deltafi.common.storage.s3.minio;

import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.storage.s3.ObjectStorageService;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
public class MinioObjectStorageService implements ObjectStorageService {
    protected final MinioClient minioClient;
    protected final long partSize;

    @Override
    public List<String> getObjectNames(String bucket, String prefix, ZonedDateTime lastModifiedBefore) {
        List<String> names = new ArrayList<>();
        
        Iterable<Result<Item>> objects = minioClient.listObjects(ListObjectsArgs.builder().bucket(bucket).prefix(prefix).recursive(true).build());
        for (Result<Item> item : objects) {
            Item itemObj = null;
            try {
                itemObj = item.get();
            } catch (ErrorResponseException | ServerException | InsufficientDataException | InternalException | InvalidKeyException | InvalidResponseException | IOException | NoSuchAlgorithmException | XmlParserException e) {
                log.error("Failed to retrieve minio object for did {}", prefix, e);
                continue;
            }

            if (itemObj.isDir()) {
                continue;
            }

            if ((lastModifiedBefore == null) || (itemObj.lastModified().isBefore(lastModifiedBefore))) {
                names.add(itemObj.objectName());
            }
        }
        return names;
    }

    @Override
    public InputStream getObjectAsInputStream(String bucket, String name, long offset, long length) throws ObjectStorageException {
        try {
            return minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(name).offset(offset).length(length).build());
        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException | InvalidResponseException | IOException | NoSuchAlgorithmException | ServerException | XmlParserException e) {
            throw new ObjectStorageException("Failed to get object from minio", e);
        }
    }

    @Override
    public byte[] getObject(String bucket, String name, long offset, long length) throws ObjectStorageException {
        try (InputStream stream = getObjectAsInputStream(bucket, name, offset, length)) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new ObjectStorageException("Failed to close minio input stream", e);
        }
    }

    @Override
    public ObjectWriteResponse putObject(String bucket, String name, InputStream inputStream, long size) throws ObjectStorageException {
        try {
            return minioClient.putObject(PutObjectArgs.builder().bucket(bucket)
                    .object(name)
                    .stream(inputStream, size, partSize)
                    .build());
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
    public void removeObject(String bucket, String name) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(name)
                    .build());
        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException |
                InvalidResponseException | IOException | NoSuchAlgorithmException | ServerException | XmlParserException e) {
            log.error("Failed to remove object {} from bucket {}", name, bucket);
        }
    }

    @Override
    public boolean removeObjects(String bucket, String prefix) {
        List<DeleteObject> objectsInStorage = getObjectNames(bucket, prefix).stream().map(DeleteObject::new).collect(Collectors.toList());

        RemoveObjectsArgs removeArgs = RemoveObjectsArgs.builder()
                .bucket(bucket).objects(objectsInStorage).build();

        Iterable<Result<DeleteError>> removeResults = minioClient.removeObjects(removeArgs);

        for (Result<DeleteError> removeResult : removeResults) {
            try {
                DeleteError error = removeResult.get();
                log.error("Failed to remove object {} with an error of {}", error.objectName(), error.message());
            } catch (ErrorResponseException | ServerException | InsufficientDataException | InternalException | InvalidKeyException | InvalidResponseException | IOException | NoSuchAlgorithmException | XmlParserException e) {
                log.error("Failed to remove object from bucket {} with prefix {}: {}", bucket, prefix, e.getMessage());
            }
            return false;
        }
        return true;
    }

    @Override
    public long getObjectSize(ObjectWriteResponse writeResponse) {
        try {
            return minioClient.statObject(StatObjectArgs.builder()
                    .bucket(writeResponse.bucket())
                    .object(writeResponse.object())
                    .build()).size();
        } catch (ErrorResponseException | InsufficientDataException | InternalException |
                InvalidKeyException | InvalidResponseException | IOException | NoSuchAlgorithmException |
                ServerException | XmlParserException e) {
            log.error("Failed to retrieve object stats for {}/{}", writeResponse.bucket(), writeResponse.object(), e);
        }
        return 0L;
    }
}
