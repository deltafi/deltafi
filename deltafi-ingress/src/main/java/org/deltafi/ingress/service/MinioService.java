package org.deltafi.ingress.service;

import io.minio.*;
import io.minio.errors.*;
import io.quarkus.runtime.configuration.MemorySize;
import org.deltafi.ingress.exceptions.DeltafiMinioException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

import static org.deltafi.common.constant.DeltaFiConstants.MINIO_BUCKET;

@ApplicationScoped
public class MinioService {

    private static final Logger log = LoggerFactory.getLogger(MinioService.class);

    @ConfigProperty(name = "minio.part-size", defaultValue = "100M")
    MemorySize partSize;

    final MinioClient minioClient;

    public MinioService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    public void removeObject(String bucketName, String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().object(objectName).bucket(bucketName).build());
        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException |
                InvalidResponseException | IOException | NoSuchAlgorithmException | ServerException | XmlParserException e) {
            log.error("Failed to remove object {} from bucket {}", objectName, bucketName);
        }
    }

    public ObjectWriteResponse putObject(String did, String objectName, InputStream inputStream, long size) throws DeltafiMinioException {
        PutObjectArgs args = new PutObjectArgs.Builder().bucket(MINIO_BUCKET).object(objectName(did, objectName)).stream(inputStream, size, partSize.asLongValue()).build();
        try {
            return minioClient.putObject(args);
        } catch (ErrorResponseException | InsufficientDataException | InternalException |
                InvalidKeyException | InvalidResponseException | IOException | NoSuchAlgorithmException |
                ServerException | XmlParserException e) {
            log.error("Failed to save incoming object", e);
            throw new DeltafiMinioException("Failed to send incoming data to minio", e);
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                log.error("Failed to close input stream: {}", e.getMessage());
            }
        }
    }

    public long getObjectSize(ObjectWriteResponse writeResponse) {
        try {
            return minioClient.statObject(StatObjectArgs.builder().bucket(writeResponse.bucket()).object(writeResponse.object()).build()).size();
        } catch (ErrorResponseException | InsufficientDataException | InternalException |
                InvalidKeyException | InvalidResponseException | IOException | NoSuchAlgorithmException |
                ServerException | XmlParserException e) {
            log.error("Failed to retrieve object stats for {}/{}", writeResponse.bucket(), writeResponse.object(), e);
        }
        return 0L;
    }

    public static String objectName(String did, String incomingName) {
        String fileName = Objects.isNull(incomingName) ? "ingress-unknown-incomingName" : "ingress-" + incomingName;
        return did + "/" + fileName;
    }

}
