package org.deltafi.core.domain.housekeeping.minio;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.core.domain.configuration.DeltaFiProperties;
import org.deltafi.core.domain.repo.DeltaFileRepo;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

@Service
@Slf4j
public class UnreferencedObjectCleanUp {
    private final ContentStorageService contentStorageService;
    private final DeltaFileRepo deltaFileRepo;
    private final Clock clock;
    private final int objectMinimumAgeForRemovalSeconds;

    @Inject
    public UnreferencedObjectCleanUp(ContentStorageService contentStorageService, DeltaFileRepo deltaFileRepo,
            Clock clock, DeltaFiProperties deltaFiProperties) {
        this.contentStorageService = contentStorageService;
        this.deltaFileRepo = deltaFileRepo;
        this.clock = clock;
        this.objectMinimumAgeForRemovalSeconds = deltaFiProperties.getHousekeeping().getMinio().getObjectMinimumAgeForRemovalSeconds();
    }

    public void removeUnreferencedObjects() {
        log.trace("Removing unreferenced Minio objects");

        ZonedDateTime lastModifiedBefore = ZonedDateTime.now(clock).minusSeconds(objectMinimumAgeForRemovalSeconds);

        Set<String> didsInObjectStorage = new HashSet<>(contentStorageService.findDidsLastModifiedBefore(lastModifiedBefore));

        didsInObjectStorage.removeAll(deltaFileRepo.readDids());

        didsInObjectStorage.forEach(did -> {
            log.info("Removing unreferenced objects from object storage with prefix {}", did);
            contentStorageService.deleteAll(did);
        });
    }
}
