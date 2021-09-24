package org.deltafi.core.domain.configuration;

import lombok.Data;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Protocol;

import java.net.URI;
import java.net.URISyntaxException;

@Configuration
@ConfigurationProperties(prefix = "redis")
@Data
public class RedisConfig {
    private String url;
    private String password;

    @Bean
    public JedisPool jedisPool() throws URISyntaxException {
        URI uri = new URI(url);
        GenericObjectPoolConfig<Jedis> pool = new GenericObjectPoolConfig<>();
        pool.setMaxIdle(8);
        pool.setMaxTotal(8);
        if (password.isEmpty()) {
            return new JedisPool(pool, uri);
        } else {
            return new JedisPool(pool, uri.getHost(), uri.getPort(), Protocol.DEFAULT_TIMEOUT, password);
        }
    }
}