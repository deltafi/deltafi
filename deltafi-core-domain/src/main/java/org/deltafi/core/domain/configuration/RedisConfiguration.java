package org.deltafi.core.domain.configuration;

import org.deltafi.common.queue.jedis.JedisKeyedBlockingQueue;
import org.deltafi.common.properties.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URISyntaxException;

@Configuration
@EnableConfigurationProperties(RedisProperties.class)
public class RedisConfiguration {

    @Bean
    public JedisKeyedBlockingQueue jedisKeyedBlockingQueue(RedisProperties redisProperties) throws URISyntaxException {
        return new JedisKeyedBlockingQueue(redisProperties.getUrl(), redisProperties.getPassword().orElse(null), 8, 8);
    }

}