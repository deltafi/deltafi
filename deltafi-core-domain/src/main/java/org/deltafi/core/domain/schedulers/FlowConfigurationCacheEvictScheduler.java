package org.deltafi.core.domain.schedulers;

import org.deltafi.core.domain.services.EgressFlowService;
import org.deltafi.core.domain.services.IngressFlowService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@ConditionalOnProperty(value = "enableScheduling", havingValue = "true", matchIfMissing = true)
@Service
@EnableScheduling
public class FlowConfigurationCacheEvictScheduler {

    IngressFlowService ingressFlowService;
    EgressFlowService egressFlowService;

    public FlowConfigurationCacheEvictScheduler(IngressFlowService ingressFlowService, EgressFlowService egressFlowService) {
        this.ingressFlowService = ingressFlowService;
        this.egressFlowService = egressFlowService;
    }

    @Scheduled(fixedDelay = 30000)
    public void cacheEvict() {
        ingressFlowService.refreshCache();
        egressFlowService.refreshCache();
    }
}