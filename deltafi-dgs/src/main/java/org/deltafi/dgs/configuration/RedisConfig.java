package org.deltafi.dgs.configuration;

import org.deltafi.dgs.services.RedisService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URISyntaxException;

@Configuration
public class RedisConfig {

    @Value("${redis.url:http://localhost:6379}")
    private String redisUrl;

    @Value("${redis.password:}")
    private String redisPassword;

    @Bean
    public RedisService redisService() throws URISyntaxException {
        return new RedisService(redisUrl, redisPassword);
    }
}
