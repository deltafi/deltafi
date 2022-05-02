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
import io.minio.messages.Item;
import lombok.AllArgsConstructor;
import okhttp3.Headers;
import org.deltafi.common.properties.MinioProperties;
import org.deltafi.common.storage.s3.ObjectReference;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MinioObjectStorageServiceTest {
    private static final String BUCKET = "bucket";

    @AllArgsConstructor
    private static class TestItem extends Item {
        private String objectName;
        private ZonedDateTime lastModified;
        private boolean isDir;

        public String objectName() {
            return objectName;
        }

        public ZonedDateTime lastModified() {
            return lastModified;
        }

        public boolean isDir() {
            return isDir;
        }
    }

    @Test
    void testGetObjectNames() throws ServerException, InsufficientDataException, ErrorResponseException, IOException,
            NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException,
            InternalException {
        MinioClient minioClient = Mockito.mock(MinioClient.class);

        ZonedDateTime testTime = ZonedDateTime.now();

        ArrayList<Result<Item>> objects = new ArrayList<>();
        objects.add(new Result<>(new TestItem("objectName1", testTime, false)));
        objects.add(new Result<>(new TestItem("objectName2", testTime.minusSeconds(10), false)));
        objects.add(new Result<>(new TestItem("objectName3", testTime, true)));
        @SuppressWarnings("unchecked")
        Result<Item> mockResult = Mockito.mock(Result.class);
        Mockito.when(mockResult.get()).thenThrow(IOException.class);
        objects.add(mockResult);
        Mockito.when(minioClient.listObjects(
                Mockito.eq(ListObjectsArgs.builder().bucket(BUCKET).prefix("did-1").recursive(true).build())))
                .thenReturn(objects);

        MinioObjectStorageService minioObjectStorageService = new MinioObjectStorageService(minioClient,
                new MinioProperties());

        List<String> objectNames = minioObjectStorageService.getObjectNames(BUCKET, "did-1");
        assertEquals(2, objectNames.size());
        assertEquals("objectName1", objectNames.get(0));
        assertEquals("objectName2", objectNames.get(1));

        objectNames = minioObjectStorageService.getObjectNames(BUCKET, "did-1", testTime.minusSeconds(5));
        assertEquals(1, objectNames.size());
        assertEquals("objectName2", objectNames.get(0));

        objectNames = minioObjectStorageService.getObjectNames(BUCKET, "did-2");
        assertTrue(objectNames.isEmpty());

        objectNames = minioObjectStorageService.getObjectNames("unused", "did-1");
        assertTrue(objectNames.isEmpty());
    }

    @Test
    void testGetObject() throws ObjectStorageException, IOException, ServerException, InsufficientDataException,
            ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException,
            XmlParserException, InternalException {
        MinioClient minioClient = Mockito.mock(MinioClient.class);

        MinioObjectStorageService minioObjectStorageService = new MinioObjectStorageService(minioClient,
                new MinioProperties());

        GetObjectResponse getObjectResponse = new GetObjectResponse(null, null, null, null, null);
        Mockito.when(minioClient.getObject(
                Mockito.eq(GetObjectArgs.builder().bucket(BUCKET).object("objectName").offset(0L).length(10L).build())))
                .thenReturn(getObjectResponse)
                .thenThrow(IOException.class);

        assertEquals(getObjectResponse, minioObjectStorageService.getObject(new ObjectReference(BUCKET, "objectName", 0, 10)));
        assertThrows(ObjectStorageException.class,
                () -> minioObjectStorageService.getObject(new ObjectReference(BUCKET, "objectName", 0, 10)));
    }

    @AllArgsConstructor
    private static class PutObjectArgsMatcher implements ArgumentMatcher<PutObjectArgs> {
        private String bucket;
        private String object;

        @Override
        public boolean matches(PutObjectArgs putObjectArgs) {
            return putObjectArgs.bucket().equals(bucket) && putObjectArgs.object().equals(object);
        }
    }

    @Test
    void testPutObject() throws ServerException, InsufficientDataException, ErrorResponseException, IOException,
            NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException,
            InternalException, ObjectStorageException {
        MinioClient minioClient = Mockito.mock(MinioClient.class);

        MinioProperties minioProperties = new MinioProperties();
        minioProperties.setPartSize(-1);

        MinioObjectStorageService minioObjectStorageService = new MinioObjectStorageService(minioClient,
                minioProperties);

        ObjectWriteResponse objectWriteResponse =
                new ObjectWriteResponse(null, BUCKET, null, "objectName", null, null);
        // Use arg matcher since eq doesn't match for PutObjectArgs (tags are constructed and compared by reference)
        Mockito.when(minioClient.putObject(Mockito.argThat(new PutObjectArgsMatcher(BUCKET, "objectName"))))
                .thenReturn(objectWriteResponse)
                .thenThrow(IOException.class);

        BufferedInputStream bufferedInputStream = new BufferedInputStream(new ByteArrayInputStream("".getBytes()));

        assertEquals(new ObjectReference(BUCKET, "objectName", 0, 0),
                minioObjectStorageService.putObject(new ObjectReference(BUCKET, "objectName", 0, 0), bufferedInputStream));

        assertThrows(ObjectStorageException.class,
                () -> minioObjectStorageService.putObject(new ObjectReference(BUCKET, "objectName", 0, 0), bufferedInputStream));
    }

    @Test
    void testRemoveObject() throws ServerException, InsufficientDataException, ErrorResponseException, IOException,
            NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException,
            InternalException {
        MinioClient minioClient = Mockito.mock(MinioClient.class);

        MinioObjectStorageService minioObjectStorageService = new MinioObjectStorageService(minioClient,
                new MinioProperties());

        minioObjectStorageService.removeObject(new ObjectReference(BUCKET, "objectName"));

        Mockito.verify(minioClient).removeObject(
                Mockito.eq(RemoveObjectArgs.builder().bucket(BUCKET).object("objectName").build()));
    }

    @AllArgsConstructor
    private static class TestDeleteError extends DeleteError {
        private String objectName;
        private String message;

        @Override
        public String objectName() {
            return objectName;
        }

        @Override
        public String message() {
            return message;
        }
    }

    @Test
    void testRemoveObjects() {
        MinioClient minioClient = Mockito.mock(MinioClient.class);

        ZonedDateTime testTime = ZonedDateTime.now();

        ArrayList<Result<Item>> objects = new ArrayList<>();
        objects.add(new Result<>(new TestItem("objectName1", testTime, false)));
        objects.add(new Result<>(new TestItem("objectName2", testTime, false)));
        Mockito.when(minioClient.listObjects(
                Mockito.eq(ListObjectsArgs.builder().bucket(BUCKET).prefix("did-1").recursive(true).build())))
                .thenReturn(objects);

        MinioObjectStorageService minioObjectStorageService = new MinioObjectStorageService(minioClient,
                new MinioProperties());

        ArrayList<Result<DeleteError>> results = new ArrayList<>();
        results.add(new Result<>(new TestDeleteError("objectName1", "message1")));
        results.add(new Result<>(new TestDeleteError("objectName2", "message2")));

        Mockito.when(minioClient.removeObjects(Mockito.any()))
                        .thenReturn(new ArrayList<>())
                        .thenReturn(results);

        assertTrue(minioObjectStorageService.removeObjects(BUCKET, "did-1"));

        assertFalse(minioObjectStorageService.removeObjects(BUCKET, "did-1"));
    }

    @Test
    void testGetObjectSize() throws ServerException, InsufficientDataException, ErrorResponseException, IOException,
            NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException,
            InternalException {
        MinioClient minioClient = Mockito.mock(MinioClient.class);

        MinioObjectStorageService minioObjectStorageService = new MinioObjectStorageService(minioClient, new MinioProperties());

        StatObjectResponse statObjectResponse = new StatObjectResponse(
                Headers.of("Content-Length", "123", "Last-Modified", "Mon, 06 Dec 2021 00:00:00 GMT"), BUCKET,
                null, "objectName");
        Mockito.when(minioClient.statObject(
                Mockito.eq(StatObjectArgs.builder().bucket(BUCKET).object("objectName").build())))
                .thenReturn(statObjectResponse)
                .thenThrow(IOException.class);

        assertEquals(123, minioObjectStorageService.getObjectSize(BUCKET, "objectName"));

        assertEquals(0, minioObjectStorageService.getObjectSize(BUCKET, "objectName"));
    }
}
