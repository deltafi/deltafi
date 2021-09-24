package org.deltafi.actionkit.config;

import io.quarkus.arc.profile.IfBuildProfile;
import io.smallrye.config.ConfigMapping;
import org.deltafi.actionkit.service.RedisActionEventService;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.inject.Produces;
import java.net.URISyntaxException;

public class RedisConfig {
    @ConfigMapping(prefix = "redis")
    public interface Config {
        String url();
    }

    @ConfigProperty(name = "redis-password") // not under redis config mapping since it's pulled from redis-password secret by quarkus-kubernetes-config extension
    String password;

    @IfBuildProfile("prod")
    @Produces
    public RedisActionEventService redisActionEventService(Config config) throws URISyntaxException {
        return new RedisActionEventService(config.url(), password);
    }
}
