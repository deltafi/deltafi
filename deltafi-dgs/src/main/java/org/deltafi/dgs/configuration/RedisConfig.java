package org.deltafi.dgs.configuration;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Protocol;

import java.net.URI;
import java.net.URISyntaxException;

@Configuration
public class RedisConfig {

    @Value("${redis.url:http://localhost:6379}")
    private String redisUrl;

    @Value("${redis.password:}")
    private String redisPassword;

    @Bean
    public JedisPool jedisPool() throws URISyntaxException {
        URI uri = new URI(redisUrl);
        GenericObjectPoolConfig<Jedis> pool = new GenericObjectPoolConfig<>();
        pool.setMaxIdle(8);
        pool.setMaxTotal(8);
        if (redisPassword.isEmpty()) {
            return new JedisPool(pool, uri);
        } else {
            return new JedisPool(pool, uri.getHost(), uri.getPort(), Protocol.DEFAULT_TIMEOUT, redisPassword);
        }
    }
}
