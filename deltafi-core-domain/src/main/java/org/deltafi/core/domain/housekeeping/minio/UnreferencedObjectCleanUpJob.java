package org.deltafi.core.domain.housekeeping.minio;

import org.springframework.beans.factory.annotation.Value;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Named
public class UnreferencedObjectCleanUpJob {
    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor();

    @Inject
    public UnreferencedObjectCleanUpJob(
            @Value("${deltafi.housekeeping.minio.initialDelaySeconds}") int initialDelaySeconds,
            @Value("${deltafi.housekeeping.minio.delaySeconds}") int delaySeconds,
            UnreferencedObjectCleanUp unreferencedObjectCleanUp) {
        EXECUTOR.scheduleWithFixedDelay(unreferencedObjectCleanUp::removeUnreferencedObjects, initialDelaySeconds,
                delaySeconds, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void preDestroy() {
        EXECUTOR.shutdownNow();
    }
}
