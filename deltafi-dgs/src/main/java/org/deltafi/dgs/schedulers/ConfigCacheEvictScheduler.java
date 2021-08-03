package org.deltafi.dgs.schedulers;

import org.deltafi.dgs.services.DeltaFiConfigService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@ConditionalOnProperty(value = "enableScheduling", havingValue = "true", matchIfMissing = true)
@Service
@EnableScheduling
public class ConfigCacheEvictScheduler {

    DeltaFiConfigService deltaFiConfigService;

    public ConfigCacheEvictScheduler(DeltaFiConfigService deltaFiConfigService) {
        this.deltaFiConfigService = deltaFiConfigService;
    }

    @Scheduled(fixedDelay = 30000)
    public void cacheEvict() {
        deltaFiConfigService.refreshConfig();
    }
}
