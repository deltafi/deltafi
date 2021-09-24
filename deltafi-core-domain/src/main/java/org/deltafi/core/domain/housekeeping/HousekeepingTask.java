package org.deltafi.core.domain.housekeeping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.storage.s3.ObjectStorageService;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.core.domain.api.repo.DeltaFileRepo;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Named
@RequiredArgsConstructor(onConstructor = @__(@Inject))
@Slf4j
public class HousekeepingTask {
    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor();

    private final ObjectStorageService objectStorageService;
    private final DeltaFileRepo deltaFileRepo;
    private final Clock clock;

    @Value("${deltafi.housekeeping.initialDelaySeconds}")
    int initialDelaySeconds;

    @Value("${deltafi.housekeeping.delaySeconds}")
    int delaySeconds;

    @Value("${deltafi.housekeeping.objectMinimumAgeForRemovalSeconds}")
    int objectMinimumAgeForRemovalSeconds;

    @PostConstruct
    public void postConstruct() {
        EXECUTOR.scheduleWithFixedDelay(() -> {
            log.trace("Running housekeeping tasks");
            removeUnreferencedObjectStorageObjects();
        }, initialDelaySeconds, delaySeconds, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void preDestroy() {
        EXECUTOR.shutdownNow();
    }

    private void removeUnreferencedObjectStorageObjects() {
        ZonedDateTime lastModifiedBefore = ZonedDateTime.now(clock).minusSeconds(objectMinimumAgeForRemovalSeconds);

        Set<String> didsInObjectStorage = objectStorageService.getObjectNames(DeltaFiConstants.MINIO_BUCKET, "", lastModifiedBefore).stream()
                .map(objectName -> objectName.substring(0, objectName.indexOf('/')))
                .collect(Collectors.toSet());

        if (!didsInObjectStorage.removeAll(deltaFileRepo.readDids())) {
            return;
        }

        didsInObjectStorage.forEach(did -> {
            log.info("Removing unreferenced objects from object storage with prefix {}", did);
            objectStorageService.removeObjects(DeltaFiConstants.MINIO_BUCKET, did);
        });
    }
}
