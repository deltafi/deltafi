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

import org.deltafi.common.ssl.SslCredentials;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class S3ClientProviderTest {

    private final AtomicInteger buildCount = new AtomicInteger();
    private final AtomicInteger closeCount = new AtomicInteger();

    /** Provider whose buildS3Client returns counting mocks instead of real AWS clients. */
    private S3ClientProvider provider() {
        return new S3ClientProvider(mock(SslCredentials.class)) {
            @Override
            S3Client buildS3Client(S3ClientParams params) {
                buildCount.incrementAndGet();
                S3Client client = mock(S3Client.class);
                doAnswer(invocation -> closeCount.incrementAndGet()).when(client).close();
                return client;
            }
        };
    }

    private S3ClientParams params(String accessKey) {
        S3ClientParams params = new S3ClientParams();
        params.setEnableSSL(false);
        params.setRegion("us-east-1");
        params.setAccessKey(accessKey);
        params.setSecretKey("secret");
        return params;
    }

    @Test
    void reusesClientForEqualParams() {
        S3ClientProvider provider = provider();

        // distinct instances with equal values must hit the same cache entry
        S3Client first = provider.getS3Client(params("a"));
        S3Client second = provider.getS3Client(params("a"));

        assertThat(second).isSameAs(first);
        assertThat(buildCount).hasValue(1);
    }

    @Test
    void buildsDistinctClientsForDifferentParams() {
        S3ClientProvider provider = provider();

        S3Client a = provider.getS3Client(params("a"));
        S3Client b = provider.getS3Client(params("b"));

        assertThat(a).isNotSameAs(b);
        assertThat(buildCount).hasValue(2);
    }

    @Test
    void closeClosesEveryHeldClient() {
        S3ClientProvider provider = provider();
        provider.getS3Client(params("a"));
        provider.getS3Client(params("b"));

        provider.close();

        assertThat(closeCount).hasValue(2);
    }

    @Test
    void boundsTheNumberOfCachedClients() {
        S3ClientProvider provider = provider();

        // far exceed the backstop; eviction must keep the map bounded
        for (int i = 0; i < 150; i++) {
            provider.getS3Client(params("key-" + i));
        }
        provider.close(); // closes only the clients still held

        assertThat(closeCount.get()).isLessThanOrEqualTo(100);
    }
}
