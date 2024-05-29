/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.common.queue.jackey;

import io.jackey.Jedis;
import io.jackey.JedisPool;
import io.jackey.Pipeline;
import io.jackey.Protocol;
import io.jackey.params.ScanParams;
import io.jackey.params.ZAddParams;
import io.jackey.resps.ScanResult;
import io.jackey.resps.Tuple;
import io.jackey.util.KeyValue;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A keyed blocking queue based on the Jackey client library for Valkey.
 */
public class ValkeyKeyedBlockingQueue {
    private static final String HEARTBEAT_HASH = "org.deltafi.action-queue.heartbeat";
    private static final String LONG_RUNNING_TASKS_HASH = "org.deltafi.action-queue.long-running-tasks";

    private final JedisPool jedisPool;

    /**
     * Constructs a JackeyKeyedBlockingQueue.
     *
     * @param url      the url of the valkey server
     * @param password the password for the valkey server
     * @param maxIdle  the maximum number of idle pooled connections to the valkey server
     * @param maxTotal the maximum number of pooled connections to the valkey server. This should be set higher than the
     *                 expected number of keys in the queue.
     * @throws URISyntaxException if the provided url is not valid
     */
    public ValkeyKeyedBlockingQueue(String url, String password, int maxIdle, int maxTotal) throws URISyntaxException {
        GenericObjectPoolConfig<Jedis> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setMaxTotal(maxTotal);

        URI uri = new URI(url);

        jedisPool = (password == null || password.isEmpty()) ? new JedisPool(poolConfig, uri) :
                new JedisPool(poolConfig, uri.getHost(), uri.getPort(), Protocol.DEFAULT_TIMEOUT, password);
    }

    /**
     * Puts an object into the queue.
     *
     * @param entry the SortedSetEntry to add to the queue
     */
    public void put(SortedSetEntry entry) {
        try (Jedis jedis = jedisPool.getResource()) {
            put(jedis, entry);
        }
    }

    /**
     * Check if an object exists in the queue based on the search pattern
     * @param key    the key for the object
     * @param search the search pattern
     * @return true if the pattern exists
     */
    public boolean exists(String key, String search) {
        ScanParams scanParams = new ScanParams().match(search);

        try (Jedis jedis = jedisPool.getResource()) {
            ScanResult<Tuple> scanResult = jedis.zscan(key, ScanParams.SCAN_POINTER_START, scanParams);
            return !scanResult.getResult().isEmpty();
        }
    }

    /**
     * Drop all queues in key list.
     *
     * @param keys list of keys
     */
    public void drop(List<String> keys) {
        try (Jedis jedis = jedisPool.getResource()) {
            drop(jedis, keys);
        }
    }

    private void drop(Jedis jedis, List<String> keys) {
        keys.forEach(jedis::del);
    }

    private void put(Jedis jedis, SortedSetEntry entry) {
        jedis.zadd(entry.getKey(), entry.getScoreEpochMilli(), entry.getValue(), ZAddParams.zAddParams().nx());
    }

    /**
     * Puts multiple objects into the queue.
     *
     * @param items a list of SortedSetEntry to put into the queue
     */
    public void put(List<SortedSetEntry> items) {
        try (Jedis jedis = jedisPool.getResource()) {
            Pipeline p = jedis.pipelined();
            items.forEach(item -> p.zadd(item.getKey(), item.getScoreEpochMilli(), item.getValue(), ZAddParams.zAddParams().nx()));
            p.sync();
        }
    }

    /**
     * Publish a heartbeat in the form of a timestamp
     *
     * @param key the name of the component publishing the heartbeat
     */
    public void setHeartbeat(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hset(HEARTBEAT_HASH, key, OffsetDateTime.now().toString());
        }
    }

    /**
     * Takes an object out of the queue.
     * <p>
     * This method will block until an object for the provided key is available. When multiple objects are available for
     * the provided key, the earliest one put into the queue is retrieved.
     *
     * @param key the key for the object
     * @return the object value
     * type
     */
    public String take(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            KeyValue<String, Tuple> keyValue = jedis.bzpopmin(0, key);
            return keyValue.getValue().getElement();
        }
    }

    /**
     * Get a list of unique keys from valkey
     * @return the set of keys
     */
    public Set<String> keys() {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.keys("*");
        }
    }

    /**
     * Get the size of the sorted set
     * @param key the name of the sorted set
     * @return the number of elements in the sorted set
     */
    public long sortedSetSize(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.zcard(key);
        }
    }

    /**
     * Retrieves a map of long-running tasks
     *
     * @return a map containing the long running tasks
     */
    public Map<String, String> getLongRunningTasks() {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.hgetAll(LONG_RUNNING_TASKS_HASH);
        }
    }

    /**
     * Retrieves the value associated with the given key from the long-running tasks
     *
     * @param key the key used to retrieve the value
     * @return the value associated with the given key, or null if the key is not found
     */
    public String getLongRunningTask(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.hget(LONG_RUNNING_TASKS_HASH, key);
        }
    }

    /**
     * Records a long running task in the long-running tasks
     *
     * @param key the key used to retrieve the value
     * @param value the value associated with the given key
     */
    public void recordLongRunningTask(String key, String value) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hset(LONG_RUNNING_TASKS_HASH, key, value);
        }
    }

    /**
     * Remove a long-running task with the specified key
     *
     * @param key the key of the task to be removed
     */
    public void removeLongRunningTask(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hdel(LONG_RUNNING_TASKS_HASH, key);
        }
    }
}
