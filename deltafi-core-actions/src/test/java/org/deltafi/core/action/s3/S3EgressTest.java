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

import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.egress.EgressInput;
import org.deltafi.actionkit.action.error.ErrorResultException;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class S3EgressTest {
    S3ClientProvider s3ClientProvider = Mockito.mock(S3ClientProvider.class);

    S3Egress s3Egress = new S3Egress(s3ClientProvider);

    DeltaFiTestRunner runner = DeltaFiTestRunner.setup();

    @Test
    void egresses() throws URISyntaxException, IOException {
        S3ClientParams s3ClientParams = S3TestUtil.createS3ClientParams();

        S3Client s3Client = Mockito.mock(S3Client.class);
        Mockito.when(s3Client.putObject(Mockito.any(PutObjectRequest.class), Mockito.any(RequestBody.class))).thenReturn(null);
        Mockito.when(s3ClientProvider.getS3Client(Mockito.eq(s3ClientParams))).thenReturn(s3Client);

        String bucketName = UUID.randomUUID().toString();

        S3Egress.Parameters parameters = new S3Egress.Parameters();
        parameters.setS3ClientParams(s3ClientParams);
        parameters.setBucket(bucketName);

        ActionContent actionContent = runner.saveContent("This is the content", "test-content-name",
                MediaType.APPLICATION_JSON);
        s3Egress.egress(runner.actionContext(), parameters, EgressInput.builder().content(actionContent).build());

        ArgumentCaptor<PutObjectRequest> putObjectRequestArgumentCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> requestBodyArgumentCaptor = ArgumentCaptor.forClass(RequestBody.class);
        Mockito.verify(s3Client).putObject(putObjectRequestArgumentCaptor.capture(), requestBodyArgumentCaptor.capture());
        Assertions.assertEquals(bucketName, putObjectRequestArgumentCaptor.getValue().bucket());
        Assertions.assertEquals("test-content-name", putObjectRequestArgumentCaptor.getValue().key());
        Assertions.assertEquals(MediaType.APPLICATION_JSON, putObjectRequestArgumentCaptor.getValue().contentType());
        Assertions.assertEquals("This is the content".length(), putObjectRequestArgumentCaptor.getValue().contentLength());
        Assertions.assertEquals("This is the content", new String(requestBodyArgumentCaptor.getValue().contentStreamProvider().newStream().readAllBytes()));
    }

    @Test
    void egressesWithNameAndContentType() throws IOException, URISyntaxException {
        S3ClientParams s3ClientParams = S3TestUtil.createS3ClientParams();

        S3Client s3Client = Mockito.mock(S3Client.class);
        Mockito.when(s3Client.putObject(Mockito.any(PutObjectRequest.class), Mockito.any(RequestBody.class))).thenReturn(null);
        Mockito.when(s3ClientProvider.getS3Client(Mockito.eq(s3ClientParams))).thenReturn(s3Client);

        String bucketName = UUID.randomUUID().toString();

        S3Egress.Parameters parameters = new S3Egress.Parameters();
        parameters.setS3ClientParams(s3ClientParams);
        parameters.setBucket(bucketName);
        parameters.setKey("key-from-parameters");
        parameters.setContentType(MediaType.APPLICATION_XML);

        ActionContent actionContent = runner.saveContent("This is the content", "test-content-name",
                MediaType.APPLICATION_JSON);
        s3Egress.egress(runner.actionContext(), parameters, EgressInput.builder().content(actionContent).build());

        ArgumentCaptor<PutObjectRequest> putObjectRequestArgumentCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> requestBodyArgumentCaptor = ArgumentCaptor.forClass(RequestBody.class);
        Mockito.verify(s3Client).putObject(putObjectRequestArgumentCaptor.capture(), requestBodyArgumentCaptor.capture());
        Assertions.assertEquals(bucketName, putObjectRequestArgumentCaptor.getValue().bucket());
        Assertions.assertEquals("key-from-parameters", putObjectRequestArgumentCaptor.getValue().key());
        Assertions.assertEquals(MediaType.APPLICATION_XML, putObjectRequestArgumentCaptor.getValue().contentType());
        Assertions.assertEquals("This is the content".length(), putObjectRequestArgumentCaptor.getValue().contentLength());
        Assertions.assertEquals("This is the content", new String(requestBodyArgumentCaptor.getValue().contentStreamProvider().newStream().readAllBytes()));
    }

    @Test
    void exceptionOnNoContent() {
        ErrorResultException errorResultException = assertThrows(ErrorResultException.class, () ->
                s3Egress.egress(runner.actionContext(), new S3Egress.Parameters(), EgressInput.builder().build()));
        assertEquals("No content was provided", errorResultException.getMessage());
    }

    @Test
    void exceptionOnPutObjectFailure() throws URISyntaxException {
        S3ClientParams s3ClientParams = S3TestUtil.createS3ClientParams();

        S3Client s3Client = Mockito.mock(S3Client.class);
        Mockito.when(s3Client.putObject(Mockito.any(PutObjectRequest.class), Mockito.any(RequestBody.class)))
                .thenThrow(S3Exception.create("Test error message", new Exception()));
        Mockito.when(s3ClientProvider.getS3Client(Mockito.eq(s3ClientParams))).thenReturn(s3Client);

        ErrorResultException errorResultException = assertThrows(ErrorResultException.class, () ->
                s3Egress.egress(runner.actionContext(), new S3Egress.Parameters(), EgressInput.builder()
                        .content(runner.saveEmptyContent("test-content-name", MediaType.TEXT_PLAIN)).build()));
        assertEquals("Failed to put the content in S3", errorResultException.getMessage());
    }
}
