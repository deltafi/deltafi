/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.common.queue.jedis;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.params.ZAddParams;
import redis.clients.jedis.resps.KeyedZSetElement;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.List;

/**
 * A keyed blocking queue based on the Jedis client library for Redis.
 */
public class JedisKeyedBlockingQueue {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JavaTimeModule());

    private final JedisPool jedisPool;

    /**
     * Constructs a JedisKeyedBlockingQueue.
     *
     * @param url      the url of the redis server
     * @param password the password for the redis server
     * @param maxIdle  the maximum number of idle pooled connections to the redis server
     * @param maxTotal the maximum number of pooled connections to the redis server. This should be set higher than the
     *                 expected number of keys in the queue.
     * @throws URISyntaxException if the provided url is not valid
     */
    public JedisKeyedBlockingQueue(String url, String password, int maxIdle, int maxTotal) throws URISyntaxException {
        GenericObjectPoolConfig<Jedis> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setMaxTotal(maxTotal);

        URI uri = new URI(url);

        jedisPool = password == null ? new JedisPool(poolConfig, uri) :
                new JedisPool(poolConfig, uri.getHost(), uri.getPort(), Protocol.DEFAULT_TIMEOUT, password);
    }

    /**
     * Puts an object into the queue.
     *
     * @param key    the key for the object
     * @param object the object
     * @throws JsonProcessingException if the object cannot be converted to a JSON string
     */
    public void put(String key, Object object) throws JsonProcessingException {
        try (Jedis jedis = jedisPool.getResource()) {
            put(jedis, key, object);
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

    private void put(Jedis jedis, String key, Object value) throws JsonProcessingException {
        jedis.zadd(key, Instant.now().toEpochMilli(),
                OBJECT_MAPPER.writeValueAsString(value), ZAddParams.zAddParams().nx());
    }

    /**
     * Puts multiple objects into the queue.
     *
     * @param items a list of key/object pairss to put into the queue
     * @throws JsonProcessingException if any object in the map cannot be converted to a JSON string. Objects retrieved
     *                                 from objectMap before the exception occurred will be placed in the queue, all others will not
     */
    public void put(List<Pair<String, Object>> items) throws JsonProcessingException, JedisConnectionException {
        try (Jedis jedis = jedisPool.getResource()) {
            Pipeline p = jedis.pipelined();
            for (Pair<String, Object> item : items) {
                p.zadd(item.getKey(), Instant.now().toEpochMilli(),
                        OBJECT_MAPPER.writeValueAsString(item.getValue()), ZAddParams.zAddParams().nx());
            }
            p.sync();
        }
    }

    /**
     * Takes an object out of the queue.
     * <p>
     * This method will block until an object for the provided key is available. When multiple objects are available for
     * the provided key, the earliest one put into the queue is retrieved.
     *
     * @param key         the key for the object
     * @param objectClass the class of the object
     * @param <T>         the type of the class of the object
     * @return the object
     * @throws JsonProcessingException if the JSON string retrieved from the queue cannot be converted to the provided
     *                                 type
     */
    public <T> T take(String key, Class<T> objectClass) throws JsonProcessingException, JedisConnectionException {
        try (Jedis jedis = jedisPool.getResource()) {
            KeyedZSetElement keyedZSetElement = jedis.bzpopmin(0, key);
            return OBJECT_MAPPER.readValue(keyedZSetElement.getElement(), objectClass);
        }
    }
}
