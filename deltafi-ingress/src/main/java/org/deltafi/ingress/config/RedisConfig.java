package org.deltafi.ingress.config;

import io.smallrye.config.ConfigMapping;
import org.deltafi.ingress.service.RedisService;
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

    @Produces
    public RedisService redisService(Config config) throws URISyntaxException {
        return new RedisService(config.url(), password);
    }
}
