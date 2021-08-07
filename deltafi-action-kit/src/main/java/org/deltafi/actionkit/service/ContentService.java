package org.deltafi.actionkit.service;

import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import io.quarkus.runtime.StartupEvent;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.exception.ContentServiceConnectException;
import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.dgs.generated.types.ObjectReference;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.deltafi.common.constant.DeltaFiConstants.MINIO_BUCKET;

@Slf4j
@ApplicationScoped
public class ContentService {

    @Inject
    MinioClient minioClient;

    // Guarantee instantiation if not injected...
    @SuppressWarnings("EmptyMethod")
    void startup(@Observes StartupEvent event) {}

    static ContentService instance;

    public static ContentService instance() { return instance; }

    private boolean healthy = true;

    ContentService() {
        log.info(this.getClass().getSimpleName() + " instantiated");
        instance = this;
    }

    public interface InputProcessor {
        void operation(InputStream stream);
    }

    private void connectionAlert(Exception e) {
        if (healthy) {
            healthy = false;
            log.error("Unable to communicate with Content Service: " + e.getMessage());
            log.debug("Content Service request exception", e);
        }
    }

    public void get(ObjectReference ref, InputProcessor proc) throws ContentServiceConnectException, IllegalStateException{

        try {
            // Avoid minio call if data length is 0
            if (ref.getSize() == 0) {
                InputStream stream = InputStream.nullInputStream();
                proc.operation(stream);
            } else {
                try (InputStream stream = minioClient.getObject(buildArgs(ref))) {
                    proc.operation(stream);
                }
            }
            healthy = true;
        } catch (IOException e) {
            connectionAlert(e);
            throw new ContentServiceConnectException(e);
        } catch (ErrorResponseException e) {
            if(e.errorResponse().code().equals("XMinioServerNotInitialized")) {
                connectionAlert(e);
                throw new ContentServiceConnectException(e);
            } else {
                throw new IllegalStateException(e);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unused")
    public byte[] retrieveContent(ObjectReference objectReference) {
        try (InputStream stream = minioClient.getObject(buildArgs(objectReference))) {
            byte[] data = stream.readAllBytes();
            healthy = true;
            return data;
        } catch (IOException e) {
            connectionAlert(e);
            throw new ContentServiceConnectException(e);
        } catch (ErrorResponseException e) {
            if(e.errorResponse().code().equals("XMinioServerNotInitialized")) {
                connectionAlert(e);
                throw new ContentServiceConnectException(e);
            } else {
                throw new IllegalStateException(e);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unused")
    public ObjectReference putObject(String data, DeltaFile deltaFile, String actionName) {
        String objectName = deltaFile.getDid() + "/" + actionName;
        return putObject(data, objectName);
    }

    private ObjectReference putObject(String data, String objectName) {
        try {
            byte[] rawData = data.getBytes();
            InputStream dataInputStream = new ByteArrayInputStream(rawData);
            ObjectWriteResponse objectWriteResponse = minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(MINIO_BUCKET)
                            .object(objectName)
                            .stream(dataInputStream, rawData.length, -1)
                            .build()
            );

            return fromObjectWriteResponse(objectWriteResponse, rawData.length);
        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException |
                InvalidResponseException | IOException | NoSuchAlgorithmException | ServerException | XmlParserException e) {
            throw new RuntimeException("Failed to write transformed data to minio " + e.getMessage());
        }
    }

    /**
     * Remove stored data for the given DeltaFile
     * @param deltaFile - deltaFile that content needs to be removed for
     * @return - true if all objects were successfully removed, otherwise false
     */
    public boolean deleteObjectsForDeltaFile(DeltaFile deltaFile) {
        List<DeleteObject> objectsInStorage = getObjectNamesForDid(deltaFile.getDid()).stream().map(DeleteObject::new).collect(Collectors.toList());

        RemoveObjectsArgs removeArgs = RemoveObjectsArgs.builder()
                .bucket(MINIO_BUCKET).objects(objectsInStorage).build();

        Iterable<Result<DeleteError>> removeResults = minioClient.removeObjects(removeArgs);

        boolean success = true;
        for (Result<DeleteError> removeResult : removeResults) {
            try {
                DeleteError error = removeResult.get();
                log.error("Failed to delete object {} with an error of {}", error.objectName(), error.message());
            } catch (ErrorResponseException | ServerException | InsufficientDataException | InternalException | InvalidKeyException | InvalidResponseException | IOException | NoSuchAlgorithmException | XmlParserException e) {
                log.error("Failed to delete minio object for did {}", deltaFile.getDid(), e);
            }
            success = false;
        }
        return success;
    }

    public List<String> getObjectNamesForDid(String did) {
        List<String> didObjects = new ArrayList<>();
        Iterable<Result<Item>> objects = minioClient.listObjects(ListObjectsArgs.builder().bucket(MINIO_BUCKET).prefix(did).recursive(true).build());
        for (Result<Item> item : objects) {
            try {
                Item itemObj = item.get();
                if (!itemObj.isDir()) {
                    didObjects.add(itemObj.objectName());
                }
            } catch (ErrorResponseException | ServerException | InsufficientDataException | InternalException | InvalidKeyException | InvalidResponseException | IOException | NoSuchAlgorithmException | XmlParserException e) {
                log.error("Failed to retrieve minio object for did {}", did, e);
            }
        }
        return didObjects;
    }

    private ObjectReference fromObjectWriteResponse(ObjectWriteResponse response, long size) {
        return ObjectReference.newBuilder()
                .bucket(response.bucket())
                .name(response.object())
                .offset(0)
                .size(size).build();
    }

    private GetObjectArgs buildArgs(ObjectReference objectReference) {
        return GetObjectArgs.builder()
                .bucket(objectReference.getBucket())
                .offset(objectReference.getOffset())
                .length(objectReference.getSize())
                .object(objectReference.getName())
                .build();
    }

}