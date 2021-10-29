package org.deltafi.actionkit.config;

import io.quarkus.arc.profile.IfBuildProfile;
import org.deltafi.actionkit.service.RedisActionEventService;
import org.deltafi.common.properties.RedisProperties;

import javax.enterprise.inject.Produces;
import java.net.URISyntaxException;

public class RedisConfig {

    @IfBuildProfile("prod")
    @Produces
    public RedisActionEventService redisActionEventService(RedisProperties redisProperties) throws URISyntaxException {
        return new RedisActionEventService(redisProperties.getUrl(), redisProperties.getPassword().orElse(""));
    }
}
