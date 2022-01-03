package org.deltafi.core.domain.housekeeping.minio;

import org.awaitility.Awaitility;
import org.deltafi.core.domain.configuration.DeltaFiProperties;
import org.deltafi.core.domain.housekeeping.HousekeepingConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;

import static org.mockito.Mockito.*;

public class UnreferencedObjectCleanUpJobTest {
    @Test
    public void unreferencedObjectCleanupCalledPeriodically() {
        DeltaFiProperties deltaFiProperties = DeltaFiProperties.builder()
                .housekeeping(HousekeepingConfiguration.builder()
                        .minio(MinioHousekeepingConfiguration.builder()
                                .initialDelaySeconds(0)
                                .delaySeconds(1)
                                .build())
                        .build())
                .build();

        UnreferencedObjectCleanUp unreferencedObjectCleanUp = Mockito.mock(UnreferencedObjectCleanUp.class);

        UnreferencedObjectCleanUpJob unreferencedObjectCleanUpJob = new UnreferencedObjectCleanUpJob(deltaFiProperties, unreferencedObjectCleanUp);

        clearInvocations(unreferencedObjectCleanUp);

        Awaitility.await()
                .atMost(Duration.ofMillis(2999))
                .untilAsserted(() -> verify(unreferencedObjectCleanUp, times(2)).removeUnreferencedObjects());

        unreferencedObjectCleanUpJob.preDestroy();
    }
}
