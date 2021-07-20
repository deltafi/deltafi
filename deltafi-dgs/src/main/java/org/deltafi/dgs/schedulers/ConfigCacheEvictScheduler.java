package org.deltafi.dgs.schedulers;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@ConditionalOnProperty(value = "enableScheduling", havingValue = "true", matchIfMissing = true)
@Service
@EnableScheduling
public class ConfigCacheEvictScheduler {
    @CacheEvict(allEntries = true, cacheNames = { "ingressFlow", "egressFlows", "optionalEgressFlow", "egressFlow", "loadAction", "enrichAction", "formatAction", "domainEndpoints", "loadGroups", "config", "actionNames" })
    @Scheduled(fixedDelay = 30000)
    public void cacheEvict() {
    }
}
