/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.assertj.core.api.Assertions;
import org.deltafi.common.storage.s3.ObjectReference;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

class MinioObjectStorageServiceTest {
    private static final String BUCKET = "bucket";
    private final MinioClient minioClient = Mockito.mock(MinioClient.class);
    private final MinioObjectStorageService minioObjectStorageService = new MinioObjectStorageService(minioClient, new MinioProperties(), true);
    private final MinioObjectStorageService serviceWithoutSnowball = new MinioObjectStorageService(minioClient, new MinioProperties(), false);
    private final AtomicInteger putManyCount = new AtomicInteger(0);

    @Test
    void testGetObject() throws ObjectStorageException, IOException, ServerException, InsufficientDataException,
            ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException,
            XmlParserException, InternalException {
        GetObjectResponse getObjectResponse = new GetObjectResponse(null, null, null, null, null);
        Mockito.when(minioClient.getObject(
                GetObjectArgs.builder().bucket(BUCKET).object("objectName").offset(0L).length(10L).build()))
                .thenReturn(getObjectResponse)
                .thenThrow(IOException.class);

        assertEquals(getObjectResponse, minioObjectStorageService.getObject(new ObjectReference(BUCKET, "objectName", 0, 10)));
        assertThrows(ObjectStorageException.class,
                () -> minioObjectStorageService.getObject(new ObjectReference(BUCKET, "objectName", 0, 10)));
    }

    @Test
    void testPutObject() throws ServerException, InsufficientDataException, ErrorResponseException, IOException,
            NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException,
            InternalException, ObjectStorageException {
        ObjectWriteResponse objectWriteResponse = objectWriteResponse("objectName");
        // Use arg matcher since eq doesn't match for PutObjectArgs (tags are constructed and compared by reference)
        Mockito.when(minioClient.putObject(Mockito.argThat(this::putObjectMatch)))
                .thenReturn(objectWriteResponse)
                .thenThrow(IOException.class);

        BufferedInputStream bufferedInputStream = new BufferedInputStream(new ByteArrayInputStream("".getBytes()));

        assertEquals(new ObjectReference(BUCKET, "objectName", 0, 0),
                minioObjectStorageService.putObject(new ObjectReference(BUCKET, "objectName", 0, 0), bufferedInputStream));

        assertThrows(ObjectStorageException.class,
                () -> minioObjectStorageService.putObject(new ObjectReference(BUCKET, "objectName", 0, 0), bufferedInputStream));
    }

    private boolean putObjectMatch(PutObjectArgs putObjectArgs) {
        return putObjectArgs.bucket().equals(BUCKET) && putObjectArgs.object().equals("objectName");
    }

    @Test
    void testRemoveObject() throws ServerException, InsufficientDataException, ErrorResponseException, IOException,
            NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException,
            InternalException {
        minioObjectStorageService.removeObject(new ObjectReference(BUCKET, "objectName"));

        Mockito.verify(minioClient).removeObject(
                RemoveObjectArgs.builder().bucket(BUCKET).object("objectName").build());
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
        ArrayList<Result<DeleteError>> results = new ArrayList<>();
        results.add(new Result<>(new TestDeleteError("objectName1", "message1")));
        results.add(new Result<>(new TestDeleteError("objectName2", "message2")));

        Mockito.when(minioClient.removeObjects(Mockito.any()))
                        .thenReturn(new ArrayList<>())
                        .thenReturn(results);

        assertTrue(minioObjectStorageService.removeObjects(BUCKET, Collections.singletonList("did-1")));
        assertFalse(minioObjectStorageService.removeObjects(BUCKET, Collections.singletonList("did-1")));
    }

    @Test
    @SneakyThrows
    void putManySnowballOn() {
        minioObjectStorageService.putObjects(BUCKET, putManyInput());
        Mockito.verify(minioClient).uploadSnowballObjects(Mockito.assertArg(this::assertSnowball));
    }

    private void assertSnowball(UploadSnowballObjectsArgs snowballObjectsArgs) {
        Assertions.assertThat(snowballObjectsArgs.objects()).hasSize(3);
        Assertions.assertThat(snowballObjectsArgs.bucket()).isEqualTo(BUCKET);
        Set<String> uploadedNames = StreamSupport.stream(snowballObjectsArgs.objects().spliterator(), false)
                .filter(o -> o.size() == 5)
                .map(SnowballObject::name).collect(Collectors.toSet());
        Assertions.assertThat(uploadedNames).hasSize(3).contains("objectName0", "objectName1", "objectName2");
    }

    @Test
    @SneakyThrows
    void putManySnowballOff() {
        Mockito.when(minioClient.putObject(Mockito.any())).thenReturn(objectWriteResponse("objectName"));
        serviceWithoutSnowball.putObjects(BUCKET, putManyInput());
        Mockito.verify(minioClient, Mockito.never()).uploadSnowballObjects(Mockito.any());
        Mockito.verify(minioClient, Mockito.times(3)).putObject(Mockito.any());
    }

    @Test
    @SneakyThrows
    void putManySnowballOff_partialFailure() {
        Mockito.when(minioClient.putObject(Mockito.any())).thenAnswer(this::answerPut);
        Map<ObjectReference, InputStream> input = putManyInput();

        Assertions.assertThatThrownBy(() -> serviceWithoutSnowball.putObjects(BUCKET, input))
                .isInstanceOf(ObjectStorageException.class)
                .hasMessage("Failed to send incoming data to minio");
        Mockito.verify(minioClient, Mockito.never()).uploadSnowballObjects(Mockito.any());
        // putObject will be called twice, where the second call throws an exception
        Mockito.verify(minioClient, Mockito.times(2)).putObject(Mockito.any());
        // there will be an attempt to remove the first object to avoid an orphaned object
        Mockito.verify(minioClient, Mockito.times(1)).removeObject(Mockito.any());
    }

    private ObjectWriteResponse answerPut(InvocationOnMock invocations) {
        int count = putManyCount.incrementAndGet();
        if (count == 2) {
            throw new RuntimeException("Mock partial failure");
        }
        return objectWriteResponse(invocations.getArgument(0, PutObjectArgs.class).object());
    }

    private Map<ObjectReference, InputStream> putManyInput() {
        Map<ObjectReference, InputStream> objects = new HashMap<>();
        IntStream.range(0, 3)
                .mapToObj(this::objectReference)
                .forEach(objectReference -> objects.put(objectReference, new ByteArrayInputStream("input".getBytes())));
        return objects;
    }

    private ObjectReference objectReference(int i) {
        return new ObjectReference(BUCKET, "objectName" + i, 0, 5);
    }

    private ObjectWriteResponse objectWriteResponse(String objectName) {
        return new ObjectWriteResponse(null, BUCKET, null, objectName, null, null);
    }
}