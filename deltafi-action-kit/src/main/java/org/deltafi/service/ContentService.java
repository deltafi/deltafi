package org.deltafi.service;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.errors.*;
import io.quarkus.runtime.StartupEvent;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.dgs.generated.types.ObjectReference;
import org.deltafi.exception.ContentServiceConnectException;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Slf4j
@ApplicationScoped
public class ContentService {

    public static final String STORAGE = "storage";

    @Inject
    MinioClient minioClient;

    // Guarantee instantiation if not injected...
    @SuppressWarnings("EmptyMethod")
    void startup(@Observes StartupEvent event) {}

    static ContentService instance;

    static public ContentService instance() { return instance; }

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

    public ObjectReference putObject(String data, String objectName) {
        try {
            byte[] rawData = data.getBytes();
            InputStream dataInputStream = new ByteArrayInputStream(rawData);
            ObjectWriteResponse objectWriteResponse = minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(STORAGE)
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

    private ObjectReference fromObjectWriteResponse(ObjectWriteResponse response, int size) {
        return ObjectReference.newBuilder()
                .bucket(response.bucket())
                .name(response.object())
                .offset(0)
                .size(size).build();
    }

    private GetObjectArgs buildArgs(ObjectReference objectReference) {
        return GetObjectArgs.builder()
                .bucket(objectReference.getBucket())
                .offset((long) objectReference.getOffset())
                .length((long) objectReference.getSize())
                .object(objectReference.getName())
                .build();
    }
}