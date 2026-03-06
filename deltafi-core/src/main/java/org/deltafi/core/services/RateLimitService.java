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
package org.deltafi.core.services;

import io.github.bucket4j.*;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.valkey.JedisPool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Slf4j
public class RateLimitService {

    private final ProxyManager<String> proxyManager;

    @Autowired
    public RateLimitService(JedisPool jedisPool) {
        this(ValkeyBasedProxyManager.builderFor(jedisPool).build());
        log.info("Initializing RateLimitService with JedisPool: {}", jedisPool);
    }

    // Constructor for testing
    RateLimitService(ProxyManager<String> proxyManager) {
        this.proxyManager = proxyManager;
    }

    public void updateLimit(String bucketName, long capacity, Duration period) {
        // Preserve consumed tokens when updating limits.
        // Note: There's a small race window between reading available tokens and removing/recreating
        // the bucket where tokens could be consumed by another thread. This is acceptable since rate
        // limit updates are rare admin operations and the effect is just that the new limit may not
        // immediately reflect in-flight consumption - the bucket will self-correct on next refill.
        long previouslyConsumed = 0;
        long previousAvailable = getAvailableTokens(bucketName);
        if (previousAvailable < Long.MAX_VALUE) {
            previouslyConsumed = Math.max(0, capacity - previousAvailable);
        }

        proxyManager.removeProxy(bucketName);

        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder().capacity(capacity).refillGreedy(capacity, period).build())
                .build();

        Bucket bucket = proxyManager.builder().build(bucketName, () -> configuration);

        // Restore the consumed token count
        if (previouslyConsumed > 0 && previouslyConsumed < capacity) {
            bucket.consumeIgnoringRateLimits(previouslyConsumed);
        }

        log.debug("Updated rate limit for bucket '{}': {} tokens per {}", bucketName, capacity, period);
    }

    public boolean tryConsume(String bucketName, long tokens, long capacity, Duration period) {
        try {
            Bucket bucket = proxyManager.getProxy(bucketName, () -> {
                log.info("Creating new bucket '{}' with {} tokens per {}", bucketName, capacity, period);
                return BucketConfiguration.builder()
                        .addLimit(Bandwidth.builder().capacity(capacity).refillGreedy(capacity, period).build())
                        .build();
            });

            long available = bucket.getAvailableTokens();
            if (available <= 0) {
                log.debug("Rate limit exceeded for bucket '{}', requested {} tokens, available {}", bucketName, tokens, available);
                return false;
            }

            // Allow oversized requests when bucket has tokens - drives bucket negative until refill
            bucket.consumeIgnoringRateLimits(tokens);
            return true;
        } catch (Exception e) {
            throw new RuntimeException("Rate limiting failed for bucket '" + bucketName + "'", e);
        }
    }

    public void consume(String bucketName, long tokens, long capacity, Duration period) {
        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder().capacity(capacity).refillGreedy(capacity, period).build())
                .build();
                
        Bucket bucket = proxyManager.builder().build(bucketName, () -> configuration);
        bucket.consumeIgnoringRateLimits(tokens);
    }

    public void consume(String bucketName, long tokens) {
        Bucket bucket = getBucket(bucketName);
        if (bucket != null) {
            try {
                bucket.consumeIgnoringRateLimits(tokens);
            } catch (Exception e) {
                // Bucket doesn't exist in storage - no rate limit to track
            }
        }
    }

    public long getAvailableTokens(String bucketName) {
        Bucket bucket = getBucket(bucketName);
        if (bucket == null) {
            return Long.MAX_VALUE;
        }
        try {
            return bucket.getAvailableTokens();
        } catch (Exception e) {
            // Bucket doesn't exist in storage - supplier returned null
            return Long.MAX_VALUE;
        }
    }

    public void removeBucket(String bucketName) {
        proxyManager.removeProxy(bucketName);
        log.debug("Removed rate limit bucket '{}'", bucketName);
    }

    private Bucket getBucket(String bucketName) {
        try {
            return proxyManager.getProxy(bucketName, () -> null);
        } catch (Exception e) {
            log.warn("Failed to get bucket '{}': {}", bucketName, e.getMessage());
            return null;
        }
    }
}