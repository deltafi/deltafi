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
        log.info("Initializing RateLimitService with JedisPool: {}", jedisPool);
        this.proxyManager = ValkeyBasedProxyManager.builderFor(jedisPool).build();
        log.info("RateLimitService initialized successfully");
    }

    public void updateLimit(String bucketName, long capacity, Duration period) {
        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder().capacity(capacity).refillGreedy(capacity, period).build())
                .build();

        proxyManager.builder().build(bucketName, () -> configuration);
        
        log.debug("Updated rate limit for bucket '{}': {} requests per {}", bucketName, capacity, period);
    }

    public boolean tryConsume(String bucketName, long tokens, long capacity, Duration period) {
        try {
            Bucket bucket = proxyManager.getProxy(bucketName, () -> {
                // This supplier only gets called if the bucket doesn't exist
                log.info("Creating new bucket '{}' with {}req/{}s", bucketName, capacity, period);
                return BucketConfiguration.builder()
                        .addLimit(Bandwidth.builder().capacity(capacity).refillGreedy(capacity, period).build())
                        .build();
            });
            
            boolean consumed = bucket.tryConsume(tokens);
            if (!consumed) {
                log.debug("Rate limit exceeded for bucket '{}', requested {} tokens", bucketName, tokens);
            }
            return consumed;
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
            bucket.consumeIgnoringRateLimits(tokens);
        }
    }

    public long getAvailableTokens(String bucketName) {
        Bucket bucket = getBucket(bucketName);
        return bucket != null ? bucket.getAvailableTokens() : Long.MAX_VALUE;
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