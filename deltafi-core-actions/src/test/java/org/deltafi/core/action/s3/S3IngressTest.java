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

import org.deltafi.actionkit.action.ingress.IngressResult;
import org.deltafi.common.test.time.TestClock;
import org.deltafi.common.types.ActionContext;
import org.deltafi.test.asserters.IngressResultAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import javax.ws.rs.core.MediaType;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class S3IngressTest {
    private static final String TEST_DATE = "2002-09-21T00:00:00Z";

    S3ClientProvider s3ClientProvider = Mockito.mock(S3ClientProvider.class);

    TestClock testClock = new TestClock();
    S3Ingress s3Ingress = new S3Ingress(s3ClientProvider, testClock);

    DeltaFiTestRunner runner = DeltaFiTestRunner.setup();

    @Test
    void ingresses() throws URISyntaxException {
        S3ClientParams s3ClientParams = S3TestUtil.createS3ClientParams();

        S3Client s3Client = Mockito.mock(S3Client.class);
        Mockito.when(s3ClientProvider.getS3Client(Mockito.eq(s3ClientParams))).thenReturn(s3Client);

        String bucketName = UUID.randomUUID().toString();

        S3Ingress.Parameters parameters = new S3Ingress.Parameters();
        parameters.setS3ClientParams(s3ClientParams);
        parameters.setBucket(bucketName);

        testClock.setInstant(Instant.parse(TEST_DATE));

        ListObjectsV2Response response1 = ListObjectsV2Response.builder().isTruncated(false).build();
        ListObjectsV2Response response2a = ListObjectsV2Response.builder().contents(
                S3Object.builder().key("key-1").lastModified(testClock.getInstant()).size((long) "Test object 1".length()).build(),
                S3Object.builder().key("key-2").lastModified(testClock.getInstant()).size((long) "Test object 2".length()).build())
                .isTruncated(true).build();
        ListObjectsV2Response response2b = ListObjectsV2Response.builder().contents(
                S3Object.builder().key("key-3").lastModified(testClock.getInstant()).size((long) "Test object 3".length()).build())
                .isTruncated(false).build();
        ListObjectsV2Response response3 = ListObjectsV2Response.builder().contents(
                S3Object.builder().key("key-1/a").lastModified(testClock.getInstant().plusSeconds(10)).size((long) "Test object 1a".length()).build(),
                S3Object.builder().key("key-1/b").lastModified(testClock.getInstant().plusSeconds(10)).size((long) "Test object 1b".length()).build())
                .isTruncated(false).build();
        Mockito.when(s3Client.listObjectsV2(Mockito.any(ListObjectsV2Request.class)))
                .thenReturn(response1, response2a, response2b, response3, response3);

        Mockito.when(s3Client.getObject(Mockito.eq(GetObjectRequest.builder().bucket(bucketName).key("key-1").build())))
                .thenReturn(S3TestUtil.buildResponseInputStream("Test object 1"));
        Mockito.when(s3Client.getObject(Mockito.eq(GetObjectRequest.builder().bucket(bucketName).key("key-2").build())))
                .thenReturn(S3TestUtil.buildResponseInputStream("Test object 2"));
        Mockito.when(s3Client.getObject(Mockito.eq(GetObjectRequest.builder().bucket(bucketName).key("key-3").build())))
                .thenReturn(S3TestUtil.buildResponseInputStream("Test object 3"));
        Mockito.when(s3Client.getObject(Mockito.eq(GetObjectRequest.builder().bucket(bucketName).key("key-1/a").build())))
                .thenReturn(S3TestUtil.buildResponseInputStream("Test object 1a"));
        Mockito.when(s3Client.getObject(Mockito.eq(GetObjectRequest.builder().bucket(bucketName).key("key-1/b").build())))
                .thenReturn(S3TestUtil.buildResponseInputStream("Test object 1b"));

        // No objects available yet
        IngressResult ingressResult = (IngressResult) s3Ingress.ingress(runner.actionContext(), parameters);
        assertEquals(0, ingressResult.getIngressResultItems().size());
        assertEquals(TEST_DATE, ingressResult.getMemo());

        // Added three objects
        testClock.tickSeconds(10);
        ingressResult = (IngressResult) s3Ingress.ingress(runner.actionContext(), parameters);
        IngressResultAssert.assertThat(ingressResult)
                .hasChildrenSize(3)
                .hasChildResultAt(0, 0, "key-1", MediaType.TEXT_PLAIN, "Test object 1",
                        Map.of("bucket", bucketName, "key", "key-1", "size", Integer.toString("Test object 1".length())),
                        Collections.emptyMap(), "Child 0 matches")
                .hasChildResultAt(1, 0, "key-2", MediaType.TEXT_PLAIN, "Test object 2",
                        Map.of("bucket", bucketName, "key", "key-2", "size", Integer.toString("Test object 2".length())),
                        Collections.emptyMap(), "Child 1 matches")
                .hasChildResultAt(2, 0, "key-3", MediaType.TEXT_PLAIN, "Test object 3",
                        Map.of("bucket", bucketName, "key", "key-3", "size", Integer.toString("Test object 3".length())),
                        Collections.emptyMap(), "Child 2 matches");
        assertEquals(testClock.getInstant().toString(), ingressResult.getMemo());

        // Added two sub-objects to key-1
        testClock.tickSeconds(10);
        parameters.setPrefix("key-1/");
        ingressResult = (IngressResult) s3Ingress.ingress(runner.actionContext(), parameters);
        IngressResultAssert.assertThat(ingressResult).hasChildrenSize(2);
        assertEquals(testClock.getInstant().toString(), ingressResult.getMemo());

        // lastPollTime is not before any objects were modified
        testClock.setInstant(Instant.ofEpochMilli(Long.MAX_VALUE));
        ActionContext actionContext = runner.actionContext();
        actionContext.setMemo(testClock.getInstant().toString());
        ingressResult = (IngressResult) s3Ingress.ingress(actionContext, parameters);
        IngressResultAssert.assertThat(ingressResult).hasChildrenSize(0);
        assertEquals(testClock.getInstant().toString(), ingressResult.getMemo());
    }

    @Test
    void ingressesWithoutDuplicates() throws URISyntaxException {
        S3ClientParams s3ClientParams = S3TestUtil.createS3ClientParams();

        S3Client s3Client = Mockito.mock(S3Client.class);
        Mockito.when(s3ClientProvider.getS3Client(Mockito.eq(s3ClientParams))).thenReturn(s3Client);

        String bucketName = UUID.randomUUID().toString();

        S3Ingress.Parameters parameters = new S3Ingress.Parameters();
        parameters.setS3ClientParams(s3ClientParams);
        parameters.setBucket(bucketName);

        testClock.setInstant(Instant.parse(TEST_DATE));

        ListObjectsV2Response response1 = ListObjectsV2Response.builder().contents(
                        S3Object.builder().key("key-1").lastModified(testClock.getInstant().plusSeconds(4)).size((long) "Test object 1".length()).build(),
                        S3Object.builder().key("key-2").lastModified(testClock.getInstant().plusSeconds(5)).size((long) "Test object 2".length()).build(),
                        S3Object.builder().key("key-3").lastModified(testClock.getInstant().plusSeconds(6)).size((long) "Test object 3".length()).build())
                .isTruncated(false).build();
        ListObjectsV2Response response2 = ListObjectsV2Response.builder().contents(
                        S3Object.builder().key("key-1").lastModified(testClock.getInstant().plusSeconds(4)).size((long) "Test object 1".length()).build(),
                        S3Object.builder().key("key-2").lastModified(testClock.getInstant().plusSeconds(5)).size((long) "Test object 2".length()).build(),
                        S3Object.builder().key("key-3").lastModified(testClock.getInstant().plusSeconds(6)).size((long) "Test object 3".length()).build())
                .isTruncated(false).build();
        Mockito.when(s3Client.listObjectsV2(Mockito.any(ListObjectsV2Request.class)))
                .thenReturn(response1, response2);

        Mockito.when(s3Client.getObject(Mockito.eq(GetObjectRequest.builder().bucket(bucketName).key("key-1").build())))
                .thenReturn(S3TestUtil.buildResponseInputStream("Test object 1"));
        Mockito.when(s3Client.getObject(Mockito.eq(GetObjectRequest.builder().bucket(bucketName).key("key-2").build())))
                .thenReturn(S3TestUtil.buildResponseInputStream("Test object 2"));
        Mockito.when(s3Client.getObject(Mockito.eq(GetObjectRequest.builder().bucket(bucketName).key("key-3").build())))
                .thenReturn(S3TestUtil.buildResponseInputStream("Test object 3"));

        ActionContext actionContext = runner.actionContext();
        actionContext.setMemo(testClock.getInstant().toString());
        testClock.tickSeconds(10);
        IngressResult ingressResult = (IngressResult) s3Ingress.ingress(actionContext, parameters);
        IngressResultAssert.assertThat(ingressResult)
                .hasChildrenSize(1)
                .hasChildResultAt(0, 0, "key-1", MediaType.TEXT_PLAIN, "Test object 1",
                        Map.of("bucket", bucketName, "key", "key-1", "size", Integer.toString("Test object 1".length())),
                        Collections.emptyMap(), "Child 0 matches");
        assertEquals(testClock.getInstant().toString(), ingressResult.getMemo());

        actionContext = runner.actionContext();
        actionContext.setMemo(testClock.getInstant().toString());
        testClock.tickSeconds(10);
        ingressResult = (IngressResult) s3Ingress.ingress(actionContext, parameters);
        IngressResultAssert.assertThat(ingressResult)
                .hasChildrenSize(2)
                .hasChildResultAt(0, 0, "key-2", MediaType.TEXT_PLAIN, "Test object 2",
                        Map.of("bucket", bucketName, "key", "key-2", "size", Integer.toString("Test object 2".length())),
                        Collections.emptyMap(), "Child 1 matches")
                .hasChildResultAt(1, 0, "key-3", MediaType.TEXT_PLAIN, "Test object 3",
                        Map.of("bucket", bucketName, "key", "key-3", "size", Integer.toString("Test object 3".length())),
                        Collections.emptyMap(), "Child 2 matches");
        assertEquals(testClock.getInstant().toString(), ingressResult.getMemo());
    }
}
