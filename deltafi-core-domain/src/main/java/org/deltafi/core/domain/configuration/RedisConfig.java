package org.deltafi.core.domain.configuration;

import lombok.Data;
import org.deltafi.common.queue.jedis.JedisKeyedBlockingQueue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URISyntaxException;

@Configuration
@ConfigurationProperties(prefix = "redis")
@Data
public class RedisConfig {
    private String url;
    private String password;

    @Bean
    public JedisKeyedBlockingQueue jedisKeyedBlockingQueue() throws URISyntaxException {
        return new JedisKeyedBlockingQueue(url, password, 8, 8);
    }
}