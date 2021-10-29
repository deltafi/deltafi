package org.deltafi.ingress.config;

import org.deltafi.common.properties.RedisProperties;
import org.deltafi.ingress.service.RedisService;

import javax.enterprise.inject.Produces;
import java.net.URISyntaxException;

public class RedisConfig {

    @Produces
    public RedisService redisService(RedisProperties redisProperties) throws URISyntaxException {
        return new RedisService(redisProperties.getUrl(), redisProperties.getPassword().orElseGet(() ->""));
    }
}
