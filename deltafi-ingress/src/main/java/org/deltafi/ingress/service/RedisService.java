package org.deltafi.ingress.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.deltafi.dgs.generated.types.IngressInput;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.params.ZAddParams;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;

import static org.deltafi.dgs.api.Constants.DGS_INGRESS;

public class RedisService {
    private final JedisPool jedisPool;
    private static final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JavaTimeModule());

    public RedisService(String redisUrl, String redisPassword) throws URISyntaxException {
        URI uri = new URI(redisUrl);
        GenericObjectPoolConfig<Jedis> pool = new GenericObjectPoolConfig<>();
        if (redisPassword.isEmpty()) {
            this.jedisPool = new JedisPool(pool, uri);
        } else {
            this.jedisPool = new JedisPool(pool, uri.getHost(), uri.getPort(), Protocol.DEFAULT_TIMEOUT, redisPassword);
        }
    }

    public void ingress(IngressInput input) throws JsonProcessingException {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.zadd(DGS_INGRESS, Instant.now().toEpochMilli(), mapper.writeValueAsString(input), ZAddParams.zAddParams().nx());
        }
    }
}
