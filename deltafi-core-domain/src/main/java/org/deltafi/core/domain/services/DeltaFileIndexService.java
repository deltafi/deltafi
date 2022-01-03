package org.deltafi.core.domain.services;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.domain.repo.DeltaFileRepo;
import org.deltafi.core.domain.configuration.DeltaFiProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
@Slf4j
@RefreshScope
public class DeltaFileIndexService {

    private final DeltaFileRepo deltaFileRepo;
    private final DeltaFiProperties deltaFiProperties;

    public DeltaFileIndexService(DeltaFileRepo deltaFileRepo, DeltaFiProperties deltaFiProperties) {
        this.deltaFileRepo = deltaFileRepo;
        this.deltaFiProperties = deltaFiProperties;
    }

    @EventListener
    public void onRefresh(final RefreshScopeRefreshedEvent event) {
        setDeltaFileTtl();
    }

    @PostConstruct
    public void setDeltaFileTtl() {
        this.deltaFileRepo.setExpirationIndex(deltaFiProperties.getDeltaFileTtl());
    }
}
