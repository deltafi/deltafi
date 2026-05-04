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
package org.deltafi.core.action.s3;

import org.deltafi.actionkit.action.error.ErrorResultException;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.test.asserters.TransformResultAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.stubbing.OngoingStubbing;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URISyntaxException;

class S3CopyObjectTest {
    S3ClientProvider s3ClientProvider = Mockito.mock(S3ClientProvider.class);

    S3CopyObject s3CopyObject = new S3CopyObject(s3ClientProvider);

    DeltaFiTestRunner runner = DeltaFiTestRunner.setup();

    @Test
    void copiesObject() throws URISyntaxException {
        S3ClientParams s3ClientParams = S3TestUtil.createS3ClientParams();

        S3Client s3Client = Mockito.mock(S3Client.class);
        Mockito.when(s3ClientProvider.getS3Client(Mockito.eq(s3ClientParams))).thenReturn(s3Client);

        S3CopyObject.Parameters parameters = new S3CopyObject.Parameters();
        parameters.setS3ClientParams(s3ClientParams);
        parameters.setSourceBucket("test-bucket");
        parameters.setSourceKey("test-key");
        parameters.setDestinationBucket("destination-bucket");
        parameters.setDestinationKey("destination-key-prefix/test-key");

        TransformResultType transformResult = s3CopyObject.transform(runner.actionContext(), parameters,
                TransformInput.builder().build());

        ArgumentCaptor<CopyObjectRequest> captor = ArgumentCaptor.forClass(CopyObjectRequest.class);
        Mockito.verify(s3Client).copyObject(captor.capture());
        Assertions.assertEquals("test-bucket", captor.getValue().sourceBucket());
        Assertions.assertEquals("test-key", captor.getValue().sourceKey());
        Assertions.assertEquals("destination-bucket", captor.getValue().destinationBucket());
        Assertions.assertEquals("destination-key-prefix/test-key", captor.getValue().destinationKey());

        TransformResultAssert.assertThat(transformResult);
    }

    @Test
    void throwsExceptionOnCopyObjectFailure() throws URISyntaxException {
        S3ClientParams s3ClientParams = S3TestUtil.createS3ClientParams();

        S3Client s3Client = Mockito.mock(S3Client.class);
        Mockito.when(s3Client.copyObject(Mockito.any(CopyObjectRequest.class)))
                .thenThrow(S3Exception.create("Test error message", new Exception()));
        Mockito.when(s3ClientProvider.getS3Client(Mockito.eq(s3ClientParams))).thenReturn(s3Client);

        S3CopyObject.Parameters parameters = new S3CopyObject.Parameters();
        parameters.setS3ClientParams(s3ClientParams);
        parameters.setSourceBucket("test-bucket");
        parameters.setSourceKey("test-key");
        parameters.setDestinationBucket("destination-bucket");
        parameters.setDestinationKey("destination-key-prefix/test-key");

        ErrorResultException errorResultException = Assertions.assertThrows(ErrorResultException.class, () ->
                s3CopyObject.transform(runner.actionContext(), parameters, TransformInput.builder().build()));
        Assertions.assertEquals("Failed to copy object in the S3 store", errorResultException.getMessage());
    }

    @Test
    void copiesLargeObject() throws URISyntaxException {
        S3ClientParams s3ClientParams = S3TestUtil.createS3ClientParams();

        S3Client s3Client = Mockito.mock(S3Client.class);
        Mockito.when(s3ClientProvider.getS3Client(Mockito.eq(s3ClientParams))).thenReturn(s3Client);

        Mockito.when(s3Client.headObject(Mockito.any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(105L).build());
        Mockito.when(s3Client.createMultipartUpload(Mockito.any(CreateMultipartUploadRequest.class)))
                .thenReturn(CreateMultipartUploadResponse.builder().uploadId("test-upload-id").build());
        OngoingStubbing<UploadPartCopyResponse> ongoingStubbing = Mockito.when(s3Client.uploadPartCopy(Mockito.any(UploadPartCopyRequest.class)));
        for (int i = 0; i < 11; i++) {
            ongoingStubbing = ongoingStubbing.thenReturn(UploadPartCopyResponse.builder().copyPartResult(CopyPartResult.builder().eTag("etag-" + i).build()).build());
        }

        S3CopyObject.Parameters parameters = new S3CopyObject.Parameters();
        parameters.setS3ClientParams(s3ClientParams);
        parameters.setSourceBucket("test-bucket");
        parameters.setSourceKey("test-key");
        parameters.setDestinationBucket("destination-bucket");
        parameters.setDestinationKey("test-key");
        parameters.setEnableMultiUpload(true);
        parameters.setMultiUploadSize(100L);
        parameters.setMultiUploadPartSize(10L);

        TransformResultType transformResult = s3CopyObject.transform(runner.actionContext(), parameters,
                TransformInput.builder().build());

        ArgumentCaptor<CreateMultipartUploadRequest> captor = ArgumentCaptor.forClass(CreateMultipartUploadRequest.class);
        Mockito.verify(s3Client).createMultipartUpload(captor.capture());
        Assertions.assertEquals("destination-bucket", captor.getValue().bucket());
        Assertions.assertEquals("test-key", captor.getValue().key());

        ArgumentCaptor<UploadPartCopyRequest> captor2 = ArgumentCaptor.forClass(UploadPartCopyRequest.class);
        Mockito.verify(s3Client, Mockito.times(11)).uploadPartCopy(captor2.capture());
        UploadPartCopyRequest uploadPartCopyRequest = captor2.getAllValues().getFirst();
        Assertions.assertEquals("test-upload-id", uploadPartCopyRequest.uploadId());
        Assertions.assertEquals("test-bucket", uploadPartCopyRequest.sourceBucket());
        Assertions.assertEquals("test-key", uploadPartCopyRequest.sourceKey());
        Assertions.assertEquals("destination-bucket", uploadPartCopyRequest.destinationBucket());
        Assertions.assertEquals("test-key", uploadPartCopyRequest.destinationKey());
        Assertions.assertEquals(1, uploadPartCopyRequest.partNumber());
        Assertions.assertEquals("bytes=0-9", uploadPartCopyRequest.copySourceRange());
        uploadPartCopyRequest = captor2.getAllValues().getLast();
        Assertions.assertEquals("test-upload-id", uploadPartCopyRequest.uploadId());
        Assertions.assertEquals("test-bucket", uploadPartCopyRequest.sourceBucket());
        Assertions.assertEquals("test-key", uploadPartCopyRequest.sourceKey());
        Assertions.assertEquals("destination-bucket", uploadPartCopyRequest.destinationBucket());
        Assertions.assertEquals("test-key", uploadPartCopyRequest.destinationKey());
        Assertions.assertEquals(11, uploadPartCopyRequest.partNumber());
        Assertions.assertEquals("bytes=100-104", uploadPartCopyRequest.copySourceRange());

        ArgumentCaptor<CompleteMultipartUploadRequest> captor3 = ArgumentCaptor.forClass(CompleteMultipartUploadRequest.class);
        Mockito.verify(s3Client).completeMultipartUpload(captor3.capture());
        Assertions.assertEquals("test-upload-id", captor3.getValue().uploadId());
        Assertions.assertEquals("destination-bucket", captor3.getValue().bucket());
        Assertions.assertEquals("test-key", captor3.getValue().key());
        Assertions.assertEquals(11, captor3.getValue().multipartUpload().parts().size());
        Assertions.assertEquals("etag-0", captor3.getValue().multipartUpload().parts().getFirst().eTag());
        Assertions.assertEquals("etag-10", captor3.getValue().multipartUpload().parts().getLast().eTag());

        TransformResultAssert.assertThat(transformResult);
    }
}
