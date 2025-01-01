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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.exceptions.StorageCheckException;
import org.deltafi.core.types.DiskMetrics;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiskSpaceService {

    private final SystemService systemService;
    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private boolean diskSpaceAPIReachable = true;

    /**
     * Check to see if the content storage is depleted (bytes remaining is less than the configured requirement)
     * <p>
     * This method calculates based on a cached value that is asynchronously polled to prevent blocking on API calls
     *
     * @return true if content storage free bytes is lower than the configured threshold
     */
     public boolean isContentStorageDepleted() {
        try {
            boolean storageDepleted = contentMetrics().bytesRemaining() <= requiredBytes();

            if (!diskSpaceAPIReachable) {
                log.info("Disk Space API is reachable again");
                diskSpaceAPIReachable = true;
            }

            return storageDepleted;
        } catch (StorageCheckException e) {
            if (diskSpaceAPIReachable) {
                log.warn("Unable to calculate storage depletion, error communicating with API: {}", e.getMessage());
                diskSpaceAPIReachable = false;
            }

            return false;
        }
    }

    /**
     * Get DiskMetrics for content storage, caching is handled in the SystemService
     * @return Disk metrics
     * @throws StorageCheckException when API is unavailable
     */
    public @NotNull DiskMetrics contentMetrics() throws StorageCheckException {
        return systemService.contentNodeDiskMetrics();
    }

    private long requiredBytes() {
        return deltaFiPropertiesService.getDeltaFiProperties().getIngressDiskSpaceRequirementInBytes();
    }
}
