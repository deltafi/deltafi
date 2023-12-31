/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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

package org.deltafi.core.services;

import lombok.SneakyThrows;
import org.deltafi.core.MockDeltaFiPropertiesService;
import org.deltafi.core.services.api.DeltafiApiClient;
import org.deltafi.core.services.api.model.DiskMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DiskSpaceServiceTest {

    @InjectMocks
    DiskSpaceService sut;

    @Spy
    DeltaFiPropertiesService deltaFiPropertiesService = new MockDeltaFiPropertiesService();

    @Mock
    DeltafiApiClient deltaFiApiClient;

    @Test
    @SneakyThrows
    void isContentStorageDepleted() {
        diskSpaceRequirement(1);

        // When the API is unreachable storage is not considered depleted (contentMetrics is null at this point mimicking an unreachable API)
        assertFalse(sut.isContentStorageDepleted());

        // fill in the metrics using the mock
        Mockito.when(deltaFiApiClient.contentMetrics()).thenReturn(new DiskMetrics(10000000, 5000000));
        sut.getContentStorageDiskMetrics();

        diskSpaceRequirement(1);
        assertFalse(sut.isContentStorageDepleted());
        diskSpaceRequirement(4);
        assertFalse(sut.isContentStorageDepleted());
        diskSpaceRequirement(6);
        assertTrue(sut.isContentStorageDepleted());
        diskSpaceRequirement(5);
        assertTrue(sut.isContentStorageDepleted());

//        Mockito.when(sut.contentMetrics())
//        Mockito.when(diskSpaceService)
//        deltaFiProperties().getIngress().setDiskSpaceRequirementInMb(1);
//        Assertions.assertFalse(diskSpaceService.isContentStorageDepleted());
//        deltaFiProperties().getIngress().setDiskSpaceRequirementInMb(4);
//        Assertions.assertFalse(diskSpaceService.isContentStorageDepleted());
//        deltaFiProperties().getIngress().setDiskSpaceRequirementInMb(5);
//        Assertions.assertTrue(diskSpaceService.isContentStorageDepleted());
//        deltaFiProperties().getIngress().setDiskSpaceRequirementInMb(6);
//        Assertions.assertFalse(diskSpaceService.isContentStorageDepleted());
    }

    @Test
    void contentMetrics() {
    }

    private void diskSpaceRequirement(int val) { deltaFiPropertiesService.getDeltaFiProperties().getIngress().setDiskSpaceRequirementInMb(val);
    }
}