/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
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
        DeltaFiProperties deltaFiProperties = new DeltaFiProperties();
        deltaFiProperties.setHousekeeping(new HousekeepingConfiguration(new MinioHousekeepingConfiguration(0, 1, 10)));

        UnreferencedObjectCleanUp unreferencedObjectCleanUp = Mockito.mock(UnreferencedObjectCleanUp.class);

        UnreferencedObjectCleanUpJob unreferencedObjectCleanUpJob = new UnreferencedObjectCleanUpJob(deltaFiProperties, unreferencedObjectCleanUp);

        clearInvocations(unreferencedObjectCleanUp);

        Awaitility.await()
                .atMost(Duration.ofMillis(2999))
                .untilAsserted(() -> verify(unreferencedObjectCleanUp, times(2)).removeUnreferencedObjects());

        unreferencedObjectCleanUpJob.preDestroy();
    }
}
