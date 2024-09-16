/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.services;

import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketLifecycleArgs;
import io.minio.errors.*;
import io.minio.messages.LifecycleRule;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.core.MockDeltaFiPropertiesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class StorageConfigurationServiceTest {

    private static final String BUCKET = "testbucket";

    @InjectMocks
    StorageConfigurationService storageConfigurationService;
    
    @Mock
    MinioClient minioClient;

    @Spy
    DeltaFiPropertiesService deltafiProperties = new MockDeltaFiPropertiesService();
    
    @Test
    void testBucketExists() throws ObjectStorageException, ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        Mockito.when(minioClient.bucketExists(Mockito.any())).thenReturn(true);
        assertTrue(storageConfigurationService.bucketExists(BUCKET));
    }

    @Test
    void testBucketExistsThrows() throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        Mockito.when(minioClient.bucketExists(Mockito.any())).thenThrow(ServerException.class);
        assertThrows(ObjectStorageException.class, () ->
                storageConfigurationService.bucketExists(BUCKET));
    }

    @Test
    void testCreateBucket() throws ObjectStorageException, ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        ArgumentCaptor<MakeBucketArgs> captureArgs = ArgumentCaptor.forClass(MakeBucketArgs.class);

        Mockito.when(minioClient.bucketExists(Mockito.any())).thenReturn(false);

        storageConfigurationService.createBucket(BUCKET);

        Mockito.verify(minioClient).makeBucket(captureArgs.capture());
        assertThat(captureArgs.getValue().bucket()).isEqualTo(BUCKET);
    }

    @Test
    void testSetExpiration() throws ObjectStorageException, ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        ArgumentCaptor<SetBucketLifecycleArgs> captureArgs = ArgumentCaptor.forClass(SetBucketLifecycleArgs.class);

        storageConfigurationService.setExpiration(BUCKET);
        Mockito.verify(minioClient).setBucketLifecycle(captureArgs.capture());

        SetBucketLifecycleArgs args = captureArgs.getValue();
        assertThat(args.bucket()).isEqualTo(BUCKET);

        assertThat(args.config().rules()).asList().hasSize(1);
        LifecycleRule rule = args.config().rules().getFirst();
        assertThat(rule.expiration().days()).isEqualTo(13);
    }

}