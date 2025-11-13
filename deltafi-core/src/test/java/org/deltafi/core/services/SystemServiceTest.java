/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.common.queue.valkey.ValkeyKeyedBlockingQueue;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class SystemServiceTest {

    @InjectMocks
    SystemService sut;

    @Mock
    ValkeyKeyedBlockingQueue valkeyBlockingQueue;

    @Mock
    PlatformService platformService;

    @Mock
    DeltaFiPropertiesService deltaFiPropertiesService;

    DeltaFiProperties deltaFiProperties = new DeltaFiProperties();

    @Test
    @SneakyThrows
    void isContentStorageDepleted() {
        diskSpaceRequirement(1);

        // build the raw metrics map returned by the mocked valkeyBLockingQueue
        Map<String, Map<String, String>> rawMetrics = new HashMap<>();
        long timestamp = (System.currentTimeMillis() / 1000) - 2;
        rawMetrics.put("a.b.disk-minio.usage", Map.of("node1", "[5000000, " + timestamp +"]", "node2", "[1000000, " + timestamp +"]"));
        rawMetrics.put("a.b.disk-minio.limit", Map.of("node1", "[10000000, " + timestamp + "]", "node2", "[50000000, " + timestamp + "]"));

        Mockito.when(valkeyBlockingQueue.getByKeys(SystemService.METRIC_KEYS)).thenReturn(rawMetrics);
        Mockito.when(platformService.contentNodeNames()).thenReturn(List.of("node1", "node2"));

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
    void failToGetMetrics() {
        // build the raw metrics map returned by the mocked valkeyBLockingQueue
        Map<String, Map<String, String>> rawMetrics = new HashMap<>();
        long timestamp = (System.currentTimeMillis() / 1000) - 2;
        rawMetrics.put("a.b.disk-minio.usage", Map.of("node1", "[5000000, " + timestamp +"]", "node2", "[1000000, " + timestamp +"]"));
        rawMetrics.put("a.b.disk-minio.limit", Map.of("node1", "[10000000, " + timestamp + "]", "node2", "[50000000, " + timestamp + "]"));

        Mockito.when(deltaFiPropertiesService.isExternalContent()).thenReturn(false);
        Mockito.when(valkeyBlockingQueue.getByKeys(SystemService.METRIC_KEYS)).thenReturn(rawMetrics);

        // mock failing to find the content node
        Mockito.when(platformService.contentNodeNames()).thenReturn(List.of());


        // When the API is unreachable, storage is not considered depleted (contentMetrics is null at this point mimicking an unreachable API)
        assertFalse(sut.isContentStorageDepleted());
        assertFalse(sut.diskSpaceAPIReachable);
    }

    @Test
    void isContentStorageDepleted_externalStorage() {
        Mockito.when(deltaFiPropertiesService.isExternalContent()).thenReturn(true);
        assertFalse(sut.isContentStorageDepleted());
        assertTrue(sut.diskSpaceAPIReachable);
        Mockito.verify(valkeyBlockingQueue, Mockito.never()).getByKeys(SystemService.METRIC_KEYS);
        Mockito.verify(deltaFiPropertiesService, Mockito.never()).getDeltaFiProperties();
    }

    private void diskSpaceRequirement(int val) {
        deltaFiProperties.setIngressDiskSpaceRequirementInMb(val);
        Mockito.when(deltaFiPropertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
        Mockito.when(deltaFiPropertiesService.isExternalContent()).thenReturn(false);
    }
}