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

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ClientSideConfig;
import io.github.bucket4j.distributed.proxy.generic.compare_and_swap.*;
import io.github.bucket4j.distributed.remote.RemoteBucketState;
import io.github.bucket4j.distributed.serialization.Mapper;
import io.valkey.Jedis;
import io.valkey.JedisPool;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class ValkeyBasedProxyManager<K> extends AbstractCompareAndSwapBasedProxyManager<K> {

    private final ValkeyApi valkeyApi;
    private final ExpirationAfterWriteStrategy expirationStrategy;
    private final Mapper<K> keyMapper;

    public static ValkeyBasedProxyManagerBuilder<String> builderFor(final JedisPool jedisPool) {
        Objects.requireNonNull(jedisPool);
        ValkeyApi valkeyApi = new ValkeyApi() {
            @Override
            public Object eval(byte[] script, int keyCount, byte[]... params) {
                try (Jedis jedis = jedisPool.getResource()) {
                    return jedis.eval(script, 1, params);
                }
            }

            @Override
            public byte[] get(byte[] key) {
                try (Jedis jedis = jedisPool.getResource()) {
                    return jedis.get(key);
                }
            }

            @Override
            public void delete(byte[] key) {
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.del(key);
                }
            }
        };
        return new ValkeyBasedProxyManagerBuilder<>(Mapper.STRING, valkeyApi);
    }

    private ValkeyBasedProxyManager(ValkeyBasedProxyManagerBuilder<K> builder) {
        super(builder.getClientSideConfig());
        this.valkeyApi = builder.valkeyApi;
        this.expirationStrategy = builder.getNotNullExpirationStrategy();
        this.keyMapper = builder.keyMapper;
    }

    @Override
    public boolean isExpireAfterWriteSupported() {
        return true;
    }

    @Override
    protected CompareAndSwapOperation beginCompareAndSwapOperation(K key) {
        final byte[] keyBytes = this.keyMapper.toBytes(key);
        return new CompareAndSwapOperation() {
            @Override
            public Optional<byte[]> getStateData(Optional<Long> timeoutNanos) {
                return Optional.ofNullable(ValkeyBasedProxyManager.this.valkeyApi.get(keyBytes));
            }

            @Override
            public boolean compareAndSwap(byte[] originalData, byte[] newData, RemoteBucketState newState, Optional<Long> timeoutNanos) {
                return ValkeyBasedProxyManager.this.compareAndSwap(keyBytes, originalData, newData, newState);
            }
        };
    }

    @Override
    protected AsyncCompareAndSwapOperation beginAsyncCompareAndSwapOperation(K key) {
        throw new UnsupportedOperationException("Async operations not supported");
    }

    @Override
    public void removeProxy(K key) {
        this.valkeyApi.delete(this.keyMapper.toBytes(key));
    }

    @Override
    protected CompletableFuture<Void> removeAsync(K key) {
        throw new UnsupportedOperationException("Async operations not supported");
    }

    @Override
    public boolean isAsyncModeSupported() {
        return false;
    }

    private Boolean compareAndSwap(byte[] key, byte[] originalData, byte[] newData, RemoteBucketState newState) {
        long ttlMillis = this.expirationStrategy.calculateTimeToLiveMillis(newState, this.currentTimeNanos());
        
        if (ttlMillis > 0L) {
            if (originalData == null) {
                // SET key value NX PX ttl
                byte[][] keysAndArgs = new byte[][]{key, newData, encodeLong(ttlMillis)};
                Object res = this.valkeyApi.eval(
                    "if redis.call('set', KEYS[1], ARGV[1], 'nx', 'px', ARGV[2]) then return 1; else return 0; end"
                        .getBytes(StandardCharsets.UTF_8), 
                    1, keysAndArgs);
                return res != null && !res.equals(0L);
            } else {
                // Compare and set with expiration
                byte[][] keysAndArgs = new byte[][]{key, originalData, newData, encodeLong(ttlMillis)};
                Object res = this.valkeyApi.eval(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then redis.call('psetex', KEYS[1], ARGV[3], ARGV[2]); return 1; else return 0; end"
                        .getBytes(StandardCharsets.UTF_8), 
                    1, keysAndArgs);
                return res != null && !res.equals(0L);
            }
        } else {
            if (originalData == null) {
                // SET key value NX (no expiration)
                byte[][] keysAndArgs = new byte[][]{key, newData};
                Object res = this.valkeyApi.eval(
                    "if redis.call('set', KEYS[1], ARGV[1], 'nx') then return 1; else return 0; end"
                        .getBytes(StandardCharsets.UTF_8), 
                    1, keysAndArgs);
                return res != null && !res.equals(0L);
            } else {
                // Compare and set without expiration
                byte[][] keysAndArgs = new byte[][]{key, originalData, newData};
                Object res = this.valkeyApi.eval(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then redis.call('set', KEYS[1], ARGV[2]); return 1; else return 0; end"
                        .getBytes(StandardCharsets.UTF_8), 
                    1, keysAndArgs);
                return res != null && !res.equals(0L);
            }
        }
    }

    private byte[] encodeLong(Long value) {
        return ("" + value).getBytes(StandardCharsets.UTF_8);
    }

    public interface ValkeyApi {
        Object eval(byte[] script, int keyCount, byte[]... params);
        byte[] get(byte[] key);
        void delete(byte[] key);
    }

    public static class ValkeyBasedProxyManagerBuilder<K> {
        private final ValkeyApi valkeyApi;
        private final Mapper<K> keyMapper;
        private ExpirationAfterWriteStrategy expirationStrategy = ExpirationAfterWriteStrategy.none();
        @Getter
        private ClientSideConfig clientSideConfig = ClientSideConfig.getDefault();

        public <Key> ValkeyBasedProxyManagerBuilder<Key> withKeyMapper(Mapper<Key> keyMapper) {
            return new ValkeyBasedProxyManagerBuilder<>(keyMapper, this.valkeyApi);
        }

        public ValkeyBasedProxyManagerBuilder<K> withExpirationStrategy(ExpirationAfterWriteStrategy expirationStrategy) {
            this.expirationStrategy = Objects.requireNonNull(expirationStrategy);
            return this;
        }

        public ValkeyBasedProxyManagerBuilder<K> withClientSideConfig(ClientSideConfig clientSideConfig) {
            this.clientSideConfig = Objects.requireNonNull(clientSideConfig);
            return this;
        }

        private ValkeyBasedProxyManagerBuilder(Mapper<K> keyMapper, ValkeyApi valkeyApi) {
            this.keyMapper = Objects.requireNonNull(keyMapper);
            this.valkeyApi = Objects.requireNonNull(valkeyApi);
        }

        public ValkeyBasedProxyManager<K> build() {
            return new ValkeyBasedProxyManager<>(this);
        }

        public ExpirationAfterWriteStrategy getNotNullExpirationStrategy() {
            return expirationStrategy;
        }
    }
}