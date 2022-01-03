package org.deltafi.core.domain.housekeeping.minio;

import org.deltafi.core.domain.configuration.DeltaFiProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@ConditionalOnProperty(value = "enableScheduling", havingValue = "true", matchIfMissing = true)
@Service
public class UnreferencedObjectCleanUpJob {
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    @Inject
    public UnreferencedObjectCleanUpJob(DeltaFiProperties deltaFiProperties,
            UnreferencedObjectCleanUp unreferencedObjectCleanUp) {
        executor.scheduleWithFixedDelay(unreferencedObjectCleanUp::removeUnreferencedObjects,
                deltaFiProperties.getHousekeeping().getMinio().getInitialDelaySeconds(),
                deltaFiProperties.getHousekeeping().getMinio().getDelaySeconds(), TimeUnit.SECONDS);
    }

    @PreDestroy
    public void preDestroy() {
        executor.shutdown();
    }
}
