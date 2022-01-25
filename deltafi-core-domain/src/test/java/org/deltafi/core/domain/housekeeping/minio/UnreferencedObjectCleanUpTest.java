package org.deltafi.core.domain.housekeeping.minio;

import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.test.time.TestClock;
import org.deltafi.core.domain.configuration.DeltaFiProperties;
import org.deltafi.core.domain.housekeeping.HousekeepingConfiguration;
import org.deltafi.core.domain.repo.DeltaFileRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;

public class UnreferencedObjectCleanUpTest {
    private static final int MIN_AGE = 5;

    private final Clock clock = new TestClock(Instant.now(), ZoneId.of("Z"));

    private ContentStorageService contentStorageService;
    private DeltaFileRepo deltaFileRepo;
    private UnreferencedObjectCleanUp unreferencedObjectCleanUp;

    @BeforeEach
    public void beforeEach() {
        contentStorageService = Mockito.mock(ContentStorageService.class);
        deltaFileRepo = Mockito.mock(DeltaFileRepo.class);

        DeltaFiProperties deltaFiProperties = new DeltaFiProperties();
        deltaFiProperties.setHousekeeping(new HousekeepingConfiguration(new MinioHousekeepingConfiguration(2, 10, MIN_AGE)));

        unreferencedObjectCleanUp = new UnreferencedObjectCleanUp(contentStorageService, deltaFileRepo, clock, deltaFiProperties);
    }

    @Test
    public void unreferencedMinioObjectsOfMinimumAgeAreRemoved() {
        Mockito.when(contentStorageService.findDidsLastModifiedBefore(Mockito.eq(ZonedDateTime.ofInstant(
                clock.instant().minusSeconds(MIN_AGE), clock.getZone())))).thenReturn(Set.of("did-1", "did-2", "did-3", "did-4"));
        Mockito.when(deltaFileRepo.readDids()).thenReturn(Set.of("did-2", "did-4"));

        unreferencedObjectCleanUp.removeUnreferencedObjects();

        Mockito.verify(contentStorageService, Mockito.times(2)).deleteAll(Mockito.any());
        Mockito.verify(contentStorageService).deleteAll(Mockito.eq("did-1"));
        Mockito.verify(contentStorageService).deleteAll(Mockito.eq("did-3"));
    }

    @Test
    public void allMinioObjectsOfMinimumAgeAreRemovedWhenDeltaFileRepoIsEmpty() {
        Mockito.when(contentStorageService.findDidsLastModifiedBefore(Mockito.eq(ZonedDateTime.ofInstant(
                clock.instant().minusSeconds(MIN_AGE), clock.getZone())))).thenReturn(Set.of("did-1", "did-2"));
        Mockito.when(deltaFileRepo.readDids()).thenReturn(Set.of());

        unreferencedObjectCleanUp.removeUnreferencedObjects();

        Mockito.verify(contentStorageService, Mockito.times(2)).deleteAll(Mockito.any());
        Mockito.verify(contentStorageService).deleteAll(Mockito.eq("did-1"));
        Mockito.verify(contentStorageService).deleteAll(Mockito.eq("did-2"));
    }

    @Test
    public void noMinioObjectsOfMinimumAgeAreRemovedWhenAllAreReferenced() {
        Mockito.when(contentStorageService.findDidsLastModifiedBefore(Mockito.eq(ZonedDateTime.ofInstant(
                clock.instant().minusSeconds(MIN_AGE), clock.getZone())))).thenReturn(Set.of("did-1", "did-2"));
        Mockito.when(deltaFileRepo.readDids()).thenReturn(Set.of("did-1", "did-2"));

        unreferencedObjectCleanUp.removeUnreferencedObjects();

        Mockito.verify(contentStorageService, Mockito.never()).deleteAll(Mockito.any());
    }
}
