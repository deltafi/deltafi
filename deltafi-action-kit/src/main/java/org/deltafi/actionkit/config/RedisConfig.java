package org.deltafi.actionkit.config;

import io.quarkus.arc.profile.IfBuildProfile;
import org.deltafi.actionkit.service.RedisActionEventService;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.inject.Produces;
import javax.inject.Singleton;
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
    @IfBuildProfile("prod")
    public RedisActionEventService redisService() throws URISyntaxException {
        return new RedisActionEventService(redisUrl, redisPassword.orElse(""));
    }

}
