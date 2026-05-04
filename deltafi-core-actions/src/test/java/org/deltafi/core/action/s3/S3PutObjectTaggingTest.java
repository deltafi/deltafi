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
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URISyntaxException;

class S3PutObjectTaggingTest {
    S3ClientProvider s3ClientProvider = Mockito.mock(S3ClientProvider.class);

    S3PutObjectTagging s3PutObjectTagging = new S3PutObjectTagging(s3ClientProvider);

    DeltaFiTestRunner runner = DeltaFiTestRunner.setup();

    @Test
    void putsObjectTagging() throws URISyntaxException {
        S3ClientParams s3ClientParams = S3TestUtil.createS3ClientParams();

        S3Client s3Client = Mockito.mock(S3Client.class);
        Mockito.when(s3ClientProvider.getS3Client(Mockito.eq(s3ClientParams))).thenReturn(s3Client);

        S3PutObjectTagging.Parameters parameters = new S3PutObjectTagging.Parameters();
        parameters.setS3ClientParams(s3ClientParams);
        parameters.setBucket("test-bucket");
        parameters.setKey("test-key");
        parameters.setTag(new S3PutObjectTagging.KeyValue("test-tag-key", "test-tag-value"));

        TransformResultType transformResult = s3PutObjectTagging.transform(runner.actionContext(), parameters,
                TransformInput.builder().build());

        ArgumentCaptor<PutObjectTaggingRequest> captor = ArgumentCaptor.forClass(PutObjectTaggingRequest.class);
        Mockito.verify(s3Client).putObjectTagging(captor.capture());
        Assertions.assertEquals("test-bucket", captor.getValue().bucket());
        Assertions.assertEquals("test-key", captor.getValue().key());
        Assertions.assertEquals("test-tag-key", captor.getValue().tagging().tagSet().getFirst().key());
        Assertions.assertEquals("test-tag-value", captor.getValue().tagging().tagSet().getFirst().value());

        TransformResultAssert.assertThat(transformResult);
    }

    @Test
    void throwsExceptionOnPutObjectTaggingFailure() throws URISyntaxException {
        S3ClientParams s3ClientParams = S3TestUtil.createS3ClientParams();

        S3Client s3Client = Mockito.mock(S3Client.class);
        Mockito.when(s3Client.putObjectTagging(Mockito.any(PutObjectTaggingRequest.class)))
                .thenThrow(S3Exception.create("Test error message", new Exception()));
        Mockito.when(s3ClientProvider.getS3Client(Mockito.eq(s3ClientParams))).thenReturn(s3Client);

        S3PutObjectTagging.Parameters parameters = new S3PutObjectTagging.Parameters();
        parameters.setS3ClientParams(s3ClientParams);
        parameters.setBucket("test-bucket");
        parameters.setKey("test-key");
        parameters.setTag(new S3PutObjectTagging.KeyValue("test-tag-key", "test-tag-value"));

        ErrorResultException errorResultException = Assertions.assertThrows(ErrorResultException.class, () ->
                s3PutObjectTagging.transform(runner.actionContext(), parameters, TransformInput.builder().build()));
        Assertions.assertEquals("Failed to put object tagging in the S3 store", errorResultException.getMessage());
    }
}
