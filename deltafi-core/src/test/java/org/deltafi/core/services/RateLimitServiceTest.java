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
// ABOUTME: Unit tests for RateLimitService token bucket rate limiting.
// ABOUTME: Tests token consumption, oversized requests, bucket updates, and availability checking.
package org.deltafi.core.services;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private ProxyManager<String> proxyManager;

    @Mock
    private RemoteBucketBuilder<String> bucketBuilder;

    @Mock
    private BucketProxy bucket;

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService(proxyManager);
    }

    @Test
    void tryConsume_allowsConsumptionWhenTokensAvailable() {
        when(proxyManager.getProxy(eq("test-bucket"), any())).thenReturn(bucket);
        when(bucket.getAvailableTokens()).thenReturn(1000L);

        boolean result = rateLimitService.tryConsume("test-bucket", 500, 1000, Duration.ofSeconds(60));

        assertTrue(result);
        verify(bucket).consumeIgnoringRateLimits(500);
    }

    @Test
    void tryConsume_allowsOversizedConsumptionWhenBucketHasTokens() {
        // Bucket has 1000 capacity, but file is 1500 bytes - should still allow
        when(proxyManager.getProxy(eq("test-bucket"), any())).thenReturn(bucket);
        when(bucket.getAvailableTokens()).thenReturn(1000L);

        boolean result = rateLimitService.tryConsume("test-bucket", 1500, 1000, Duration.ofSeconds(60));

        assertTrue(result);
        verify(bucket).consumeIgnoringRateLimits(1500);
    }

    @Test
    void tryConsume_rejectsWhenNoTokensAvailable() {
        when(proxyManager.getProxy(eq("test-bucket"), any())).thenReturn(bucket);
        when(bucket.getAvailableTokens()).thenReturn(0L);

        boolean result = rateLimitService.tryConsume("test-bucket", 500, 1000, Duration.ofSeconds(60));

        assertFalse(result);
        verify(bucket, never()).consumeIgnoringRateLimits(anyLong());
    }

    @Test
    void tryConsume_rejectsWhenBucketIsNegative() {
        // Bucket is negative (previous oversized consumption)
        when(proxyManager.getProxy(eq("test-bucket"), any())).thenReturn(bucket);
        when(bucket.getAvailableTokens()).thenReturn(-500L);

        boolean result = rateLimitService.tryConsume("test-bucket", 500, 1000, Duration.ofSeconds(60));

        assertFalse(result);
        verify(bucket, never()).consumeIgnoringRateLimits(anyLong());
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateLimit_removesExistingBucketBeforeCreatingNew() {
        // New bucket (no previous available tokens)
        when(proxyManager.getProxy(eq("test-bucket"), any(Supplier.class))).thenReturn(null);
        when(proxyManager.builder()).thenReturn(bucketBuilder);
        when(bucketBuilder.build(eq("test-bucket"), any(Supplier.class))).thenReturn(bucket);

        rateLimitService.updateLimit("test-bucket", 1000, Duration.ofSeconds(60));

        // Verify bucket is removed first, then recreated
        var inOrder = inOrder(proxyManager, bucketBuilder);
        inOrder.verify(proxyManager).removeProxy("test-bucket");
        inOrder.verify(proxyManager).builder();
        inOrder.verify(bucketBuilder).build(eq("test-bucket"), any(Supplier.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateLimit_preservesConsumedTokensWhenBucketExists() {
        // Existing bucket had 750 of 1000 tokens available (250 consumed)
        when(proxyManager.getProxy(eq("test-bucket"), any(Supplier.class))).thenReturn(bucket);
        when(bucket.getAvailableTokens()).thenReturn(750L);
        when(proxyManager.builder()).thenReturn(bucketBuilder);
        when(bucketBuilder.build(eq("test-bucket"), any(Supplier.class))).thenReturn(bucket);

        // Update to new capacity of 2000
        rateLimitService.updateLimit("test-bucket", 2000, Duration.ofSeconds(60));

        // Should consume 1250 tokens to preserve the 750 available (2000 - 750 = 1250)
        verify(bucket).consumeIgnoringRateLimits(1250L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateLimit_doesNotConsumeWhenNewBucket() {
        // getAvailableTokens returns MAX_VALUE when bucket doesn't exist
        when(proxyManager.getProxy(eq("test-bucket"), any(Supplier.class))).thenReturn(null);
        when(proxyManager.builder()).thenReturn(bucketBuilder);
        when(bucketBuilder.build(eq("test-bucket"), any(Supplier.class))).thenReturn(bucket);

        rateLimitService.updateLimit("test-bucket", 1000, Duration.ofSeconds(60));

        // Should not consume any tokens for a new bucket
        verify(bucket, never()).consumeIgnoringRateLimits(anyLong());
    }

    @Test
    void removeBucket_removesProxy() {
        rateLimitService.removeBucket("test-bucket");

        verify(proxyManager).removeProxy("test-bucket");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAvailableTokens_returnsBucketTokens() {
        when(proxyManager.getProxy(eq("test-bucket"), any(Supplier.class))).thenReturn(bucket);
        when(bucket.getAvailableTokens()).thenReturn(750L);

        long tokens = rateLimitService.getAvailableTokens("test-bucket");

        assertEquals(750L, tokens);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAvailableTokens_returnsMaxValueWhenBucketLookupFails() {
        when(proxyManager.getProxy(eq("test-bucket"), any(Supplier.class)))
                .thenThrow(new RuntimeException("Connection failed"));

        long tokens = rateLimitService.getAvailableTokens("test-bucket");

        assertEquals(Long.MAX_VALUE, tokens);
    }
}
