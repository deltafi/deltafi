package org.deltafi.ingress.config;

import org.deltafi.ingress.service.RedisService;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.inject.Singleton;
import javax.ws.rs.Produces;
import java.net.URISyntaxException;
import java.util.Optional;

@Singleton
public class RedisConfig {

    @ConfigProperty(name = "redis.url")
    String redisUrl;

    @ConfigProperty(name = "redis.password")
    Optional<String> redisPassword;

    @Produces
    @Singleton
    public RedisService redisService() throws URISyntaxException {
        return new RedisService(redisUrl, redisPassword.orElse(""));
    }
}
