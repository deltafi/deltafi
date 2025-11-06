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
package org.deltafi.common.queue.valkey;

import io.valkey.*;
import io.valkey.params.ScanParams;
import io.valkey.params.ZAddParams;
import io.valkey.resps.ScanResult;
import io.valkey.resps.Tuple;
import io.valkey.util.KeyValue;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.deltafi.common.action.EventQueueProperties;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Predicate;

/**
 * A keyed blocking queue based on the valkey client library for Valkey.
 */
@Slf4j
public class ValkeyKeyedBlockingQueue {
    public static final String SSE_VALKEY_CHANNEL_PREFIX = "org.deltafi.ui.sse";
    public static final String HEARTBEAT_HASH = "org.deltafi.action-queue.heartbeat";
    public static final String LONG_RUNNING_TASKS_HASH = "org.deltafi.action-queue.long-running-tasks";
    public static final String MONITOR_STATUS_HASH = "org.deltafi.monitor.status";

    private final JedisPool jedisPool;

    /**
     * Constructs a ValkeyKeyedBlockingQueue with an existing JedisPool.
     *
     * @param jedisPool the JedisPool to use for Valkey connections
     */
    public ValkeyKeyedBlockingQueue(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    /**
     * Constructs a ValkeyKeyedBlockingQueue with custom connection parameters.
     *
     * @param eventQueueProperties the queue configuration properties
     * @param maxTotal the maximum number of pooled connections
     * @throws URISyntaxException if the URL is invalid
     */
    public ValkeyKeyedBlockingQueue(EventQueueProperties eventQueueProperties, int maxTotal) throws URISyntaxException {
        this.jedisPool = createJedisPool(eventQueueProperties, maxTotal, maxTotal / 2);
        log.info("Valkey pool size: {}", maxTotal);
    }

    /**
     * Helper method to create a JedisPool with specified configuration.
     */
    public static JedisPool createJedisPool(EventQueueProperties eventQueueProperties, int maxTotal, int maxIdle) throws URISyntaxException {
        GenericObjectPoolConfig<Jedis> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setMaxTotal(maxTotal);

        URI uri = new URI(eventQueueProperties.getUrl());

        return (eventQueueProperties.getPassword() == null || eventQueueProperties.getPassword().isEmpty()) ? 
                new JedisPool(poolConfig, uri) :
                new JedisPool(poolConfig, uri.getHost(), uri.getPort(), Protocol.DEFAULT_TIMEOUT, eventQueueProperties.getPassword());
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
    public void drop(Collection<String> keys) {
        try (Jedis jedis = jedisPool.getResource()) {
            drop(jedis, keys);
        }
    }

    private void drop(Jedis jedis, Collection<String> keys) {
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
     * Set a key value pair in valkey
     * @param key to use
     * @param value to use
     */
    public void set(String key, String value) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(key, value);
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
     * Get the list of action queue names that have a heartbeat
     * older than 1 minute
     * @return list of
     */
    public Set<String> getRecentQueues() {
        OffsetDateTime staleMarker = OffsetDateTime.now().minusMinutes(1);
        return heartbeats(null, heartbeat -> heartbeat.isAfter(staleMarker));
    }

    public Set<String> getOldDgsQueues() {
        return heartbeats("dgs-", heartbeat -> heartbeat.isBefore(OffsetDateTime.now().minusMinutes(5)));
    }

    private Set<String> heartbeats(String prefix, Predicate<OffsetDateTime> heartbeatCheck) {
        Set<String> queueNames = new HashSet<>();
        Map<String,String> heartbeats = hgetAll(HEARTBEAT_HASH);
        for (Map.Entry<String,String> entry : heartbeats.entrySet()) {
            if (prefix != null && !entry.getKey().startsWith(prefix)) {
                continue;
            }
            OffsetDateTime heartbeat = OffsetDateTime.parse(entry.getValue());
            if (heartbeatCheck.test(heartbeat)) {
                queueNames.add(entry.getKey());
            }
        }
        return queueNames;
    }

    /**
     * Takes an object out of the queue.
     * <p>
     * This method will block until an object for any of the provided keys is available. When multiple objects are
     * available, the earliest one put into the queue is retrieved.
     *
     * @param keys the keys for the object
     * @return the object value
     * type
     */
    public String take(String... keys) {
        return take(0, keys);
    }

    public String take(double timeout, String... keys) {
        try (Jedis jedis = jedisPool.getResource()) {
            try {
                KeyValue<String, Tuple> keyValue = jedis.bzpopmin(timeout, keys);
                return keyValue.getValue().getElement();
            } catch (NullPointerException npe) {
                // Workaround for bug fixed in redis/jedis but not pulled into valkey/jedis
                return null;
            }
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
        return hgetAll(LONG_RUNNING_TASKS_HASH);
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

    /**
     * Remove a long-running tasks with the specified keys
     *
     * @param fields the collection of keys of the tasks to be removed
     */
    public void removeLongRunningTasks(Collection<String> fields) {
        hdel(LONG_RUNNING_TASKS_HASH, fields);
    }

    /**
     * Remove the given heartbeat fields from the heartbeat hash
     * @param queueName list of queue names to remove
     */
    public void removeHeartbeats(Collection<String> queueName) {
        hdel(HEARTBEAT_HASH, queueName);
    }

    /**
     * Find all keys with the given prefix and the associated values
     * @param prefix to search for
     * @return map of keys to values with keys that match the prefix
     */
    public Map<String, String> getItemsWithPrefix(String prefix) {
        Map<String, String> result = new HashMap<>();

        try (Jedis jedis = jedisPool.getResource()) {
            ScanParams scanParams = new ScanParams().match(prefix + "*").count(100);
            boolean completed = false;
            while (!completed){
                ScanResult<String> scanResult = jedis.scan(ScanParams.SCAN_POINTER_START, scanParams);
                scanResult.getResult().forEach(key -> result.put(key, jedis.get(key)));
                completed = scanResult.isCompleteIteration();
            }
        }

        return result;
    }

    public String getByKey(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.get(key);
        }
    }

    /**
     * Get the values for each of the given keys
     * @param keys to get from valkey
     * @return map of the keys to their stored values
     */
    public Map<String, Map<String, String>> getByKeys(List<String> keys) {
        try (Jedis jedis = jedisPool.getResource()) {
            Pipeline pipeline = jedis.pipelined();

            List<Response<Map<String, String>>> responses = keys.stream()
                    .map(pipeline::hgetAll).toList();

            pipeline.sync();

            Map<String, Map<String, String>> results = new LinkedHashMap<>();

            int i = 0;
            for (String key : keys) {
                results.put(key, responses.get(i).get());
                i++;
            }
            return results;
        }
    }

    /**
     * Get the count of elements in set for each of the given keys
     * @param keys to get counts for
     * @return map of keys to their counts
     */
    public Map<String, Long> queuesCounts(Collection<String> keys) {
        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, Long> queueCnts = new HashMap<>();

            for (String key : keys) {
                long count = jedis.zcount(key, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
                queueCnts.put(key, count);
            }

            return queueCnts;
        }
    }

    private Map<String, String> hgetAll(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.hgetAll(key);
        }
    }

    private void hdel(String key, Collection<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hdel(key, fields.toArray(new String[0]));
        }
    }
}
