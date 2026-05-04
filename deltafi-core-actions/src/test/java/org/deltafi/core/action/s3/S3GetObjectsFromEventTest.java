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
import org.deltafi.test.asserters.TransformResultsAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import javax.ws.rs.core.MediaType;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class S3GetObjectsFromEventTest {
    static final String S3_EVENT_NOTIFICATION_JSON = """
                {
                  "Records": [{
                    "eventVersion": "2.1",
                    "eventSource": "aws:s3",
                    "eventName": "s3:ObjectCreated:Put",
                    "s3": {
                      "bucket": {
                        "name": "test-bucket"
                      },
                      "object": {
                        "key": "key-1"
                      }
                    }
                  },{
                    "eventVersion": "2.1",
                    "eventSource": "aws:s3",
                    "eventName": "s3:ObjectCreated:Post",
                    "s3": {
                      "bucket": {
                        "name": "test-bucket"
                      },
                      "object": {
                        "key": "key-2"
                      }
                    }
                  },{
                    "eventVersion": "2.1",
                    "eventSource": "aws:s3",
                    "eventName": "s3:ObjectCreated:SomethingElse",
                    "s3": {
                      "bucket": {
                        "name": "test-bucket"
                      },
                      "object": {
                        "key": "key-3"
                      }
                    }
                  },{
                    "eventVersion": "2.1",
                    "eventSource": "aws:s3",
                    "eventName": "s3:ObjectCreated:CompleteMultipartUpload",
                    "s3": {
                      "bucket": {
                        "name": "test-bucket"
                      },
                      "object": {
                        "key": "key-4"
                      }
                    }
                  },{
                    "eventVersion": "2.1",
                    "eventSource": "aws:s3",
                    "eventName": "s3:ObjectCreated:Copy",
                    "s3": {
                      "bucket": {
                        "name": "test-bucket"
                      },
                      "object": {
                        "key": "key-5"
                      }
                    }
                  }]
                }
                """;

    S3ClientProvider s3ClientProvider = Mockito.mock(S3ClientProvider.class);

    S3GetObjectsFromEvent s3GetObjectsFromEvent = new S3GetObjectsFromEvent(s3ClientProvider);

    DeltaFiTestRunner runner = DeltaFiTestRunner.setup();

    @Test
    void getsObjectsFromEvent() throws URISyntaxException {
        S3ClientParams s3ClientParams = S3TestUtil.createS3ClientParams();

        S3Client s3Client = Mockito.mock(S3Client.class);
        Mockito.when(s3Client.getObject(Mockito.any(GetObjectRequest.class)))
                .thenReturn(S3TestUtil.buildResponseInputStream("Test object 1"))
                .thenReturn(S3TestUtil.buildResponseInputStream("Test object 2"))
                .thenReturn(S3TestUtil.buildResponseInputStream("Test object 4"))
                .thenReturn(S3TestUtil.buildResponseInputStream("Test object 5"));
        Mockito.when(s3ClientProvider.getS3Client(Mockito.eq(s3ClientParams))).thenReturn(s3Client);

        TransformResultType results = s3GetObjectsFromEvent.transform(runner.actionContext(), s3ClientParams,
                TransformInput.builder().content(List.of(runner.saveContent(S3_EVENT_NOTIFICATION_JSON,
                        "s3-event-notification", MediaType.APPLICATION_JSON))).build());

        TransformResultsAssert.assertThat(results)
                .hasChildrenSize(4)
                .hasChildResultAt(0, "key-1", MediaType.TEXT_PLAIN, "Test object 1", Map.of("bucket", "test-bucket", "key", "key-1", "size", "13"))
                .hasChildResultAt(1, "key-2", MediaType.TEXT_PLAIN, "Test object 2", Map.of("bucket", "test-bucket", "key", "key-2", "size", "13"))
                .hasChildResultAt(2, "key-4", MediaType.TEXT_PLAIN, "Test object 4", Map.of("bucket", "test-bucket", "key", "key-4", "size", "13"))
                .hasChildResultAt(3, "key-5", MediaType.TEXT_PLAIN, "Test object 5", Map.of("bucket", "test-bucket", "key", "key-5", "size", "13"));

        Mockito.verify(s3Client, Mockito.never()).deleteObject(Mockito.any(DeleteObjectRequest.class));
        Mockito.verify(s3Client, Mockito.never()).putObjectTagging(Mockito.any(PutObjectTaggingRequest.class));
    }

    @Test
    void getObjectFailureThrowsException() throws URISyntaxException {
        S3ClientParams s3ClientParams = S3TestUtil.createS3ClientParams();

        S3Client s3Client = Mockito.mock(S3Client.class);
        Mockito.when(s3Client.getObject(Mockito.any(GetObjectRequest.class)))
                .thenThrow(S3Exception.create("Test error message", new Exception()));
        Mockito.when(s3ClientProvider.getS3Client(Mockito.eq(s3ClientParams))).thenReturn(s3Client);

        ErrorResultException exception = Assertions.assertThrows(ErrorResultException.class,
                () -> s3GetObjectsFromEvent.transform(runner.actionContext(), s3ClientParams,
                        TransformInput.builder().content(List.of(runner.saveContent(S3_EVENT_NOTIFICATION_JSON,
                                "s3-event-notification", MediaType.APPLICATION_JSON))).build()));
        Assertions.assertEquals("Failed to copy S3 object to local storage", exception.getMessage());
    }

    @Test
    void exceptionOnGetObjectFailure() {
        ErrorResultException errorResultException = assertThrows(ErrorResultException.class, () ->
                s3GetObjectsFromEvent.transform(runner.actionContext(), new S3ClientParams(), TransformInput.builder()
                        .content(List.of(runner.saveContent(S3_EVENT_NOTIFICATION_JSON,
                                "s3-event-notification", MediaType.APPLICATION_JSON))).build()));
        assertEquals("Failed to copy S3 object to local storage", errorResultException.getMessage());
    }
}
