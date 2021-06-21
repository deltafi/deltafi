package org.deltafi.ingress.service;

import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.errors.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@ExtendWith(MockitoExtension.class)
class MinioServiceTest {

    @InjectMocks
    MinioService minioService;

    @Mock
    MinioClient minioClient;

    @Test
    void removeObject() throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        minioService.removeObject("bucket", "object");
        RemoveObjectArgs args = RemoveObjectArgs.builder().bucket("bucket").object("object").build();
        Mockito.verify(minioClient).removeObject(args);
    }

}