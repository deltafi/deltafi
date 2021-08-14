package org.deltafi.actionkit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.deltafi.actionkit.action.Result;
import org.deltafi.dgs.api.types.ActionInput;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.params.ZAddParams;
import redis.clients.jedis.resps.KeyedZSetElement;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Objects;

import static org.deltafi.dgs.api.Constants.DGS_QUEUE;

public class RedisActionEventService implements ActionEventService {
    private final JedisPool jedisPool;
    private static final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JavaTimeModule());

    public RedisActionEventService(String redisUrl, String redisPassword) throws URISyntaxException {
        URI uri = new URI(redisUrl);
        GenericObjectPoolConfig<Jedis> pool = new GenericObjectPoolConfig<>();
        // TODO: This needs to scale up with the number of actions configured
        pool.setMaxIdle(16);
        pool.setMaxTotal(16);
        if (Objects.isNull(redisPassword) || redisPassword.isEmpty()) {
            this.jedisPool = new JedisPool(pool, uri);
        } else {
            this.jedisPool = new JedisPool(pool, uri.getHost(), uri.getPort(), Protocol.DEFAULT_TIMEOUT, redisPassword);
        }
    }

    public ActionInput getAction(String actionClassName) throws JsonProcessingException {
        try (Jedis jedis = jedisPool.getResource()) {
            KeyedZSetElement keyedZSetElement = jedis.bzpopmin(0, actionClassName);
            return mapper.readValue(keyedZSetElement.getElement(), ActionInput.class);
        }
    }

    public void submitResult(Result result) throws JsonProcessingException {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.zadd(DGS_QUEUE, Instant.now().toEpochMilli(), mapper.writeValueAsString(result.toEvent()), ZAddParams.zAddParams().nx());
        }
    }
}
