package org.deltafi.dgs.configuration;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class ConfigCache {
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("ingressFlow", "egressFlows", "optionalEgressFlow", "egressFlow", "loadAction", "enrichAction", "formatAction", "domainEndpoints", "loadGroups", "config", "actionNames");
    }
}
