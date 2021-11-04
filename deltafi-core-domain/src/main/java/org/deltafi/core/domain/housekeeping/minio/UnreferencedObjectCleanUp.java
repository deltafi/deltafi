package org.deltafi.core.domain.housekeeping.minio;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.storage.s3.ObjectStorageService;
import org.deltafi.core.domain.api.repo.DeltaFileRepo;
import org.springframework.beans.factory.annotation.Value;

import javax.inject.Inject;
import javax.inject.Named;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Named
@Slf4j
public class UnreferencedObjectCleanUp {
    private final ObjectStorageService objectStorageService;
    private final DeltaFileRepo deltaFileRepo;
    private final Clock clock;
    private final int objectMinimumAgeForRemovalSeconds;

    @Inject
    public UnreferencedObjectCleanUp(ObjectStorageService objectStorageService, DeltaFileRepo deltaFileRepo,
            Clock clock, @Value("${deltafi.housekeeping.minio.objectMinimumAgeForRemovalSeconds}") int objectMinimumAgeForRemovalSeconds) {
        this.objectStorageService = objectStorageService;
        this.deltaFileRepo = deltaFileRepo;
        this.clock = clock;
        this.objectMinimumAgeForRemovalSeconds = objectMinimumAgeForRemovalSeconds;
    }

    public void removeUnreferencedObjects() {
        log.trace("Removing unreferenced Minio objects");

        ZonedDateTime lastModifiedBefore = ZonedDateTime.now(clock).minusSeconds(objectMinimumAgeForRemovalSeconds);

        Set<String> didsInObjectStorage = objectStorageService.getObjectNames(DeltaFiConstants.MINIO_BUCKET, "",
                        lastModifiedBefore).stream()
                .map(objectName -> objectName.substring(0, objectName.indexOf('/')))
                .collect(Collectors.toSet());

        didsInObjectStorage.removeAll(deltaFileRepo.readDids());

        didsInObjectStorage.forEach(did -> {
            log.info("Removing unreferenced objects from object storage with prefix {}", did);
            objectStorageService.removeObjects(DeltaFiConstants.MINIO_BUCKET, did);
        });
    }
}
