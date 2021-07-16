package org.deltafi.dgs.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.deltafi.dgs.api.types.DeltaFile;
import org.deltafi.dgs.generated.types.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.params.ZAddParams;
import redis.clients.jedis.resps.KeyedZSetElement;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.List;

import static org.deltafi.dgs.api.Constants.*;

public class RedisService {

    private final JedisPool jedisPool;
    private static final ObjectMapper mapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .registerModule(new JavaTimeModule());

    public RedisService(String redisUrl, String redisPassword) throws URISyntaxException {
        URI uri = new URI(redisUrl);
        GenericObjectPoolConfig<Jedis> pool = new GenericObjectPoolConfig<>();
        pool.setMaxIdle(8);
        pool.setMaxTotal(8);
        if (redisPassword.isEmpty()) {
            this.jedisPool = new JedisPool(pool, uri);
        } else {
            this.jedisPool = new JedisPool(pool, uri.getHost(), uri.getPort(), Protocol.DEFAULT_TIMEOUT, redisPassword);
        }
    }

    public void enqueue(List<String> actionNames, DeltaFile deltaFile) {
        try (Jedis jedis = jedisPool.getResource()) {
            for (String actionName : actionNames) {
                jedis.zadd(actionName, Instant.now().toEpochMilli(), mapper.writeValueAsString(deltaFile.forQueue(actionName)), ZAddParams.zAddParams().nx());
            }
        } catch (JsonProcessingException e) {
            // TODO: this should never happen, but do something?
        }
    }

    public ActionEventInput dgsFeed() throws JsonProcessingException {
        try (Jedis jedis = jedisPool.getResource()) {
            KeyedZSetElement keyedZSetElement = jedis.bzpopmin(0, DGS_QUEUE);
            return mapper.readValue(keyedZSetElement.getElement(), ActionEventInput.class);
        }
    }
}
