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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.exceptions.DeltafiApiException;
import org.deltafi.core.services.api.DeltafiApiClient;
import org.deltafi.core.services.api.model.DiskMetrics;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiskSpaceService {

    private final DeltafiApiClient deltafiApiClient;
    private final DeltaFiPropertiesService deltaFiPropertiesService;

    private DiskMetrics contentStorageMetrics = null;
    private boolean diskSpaceAPIReachable = true;

    /**
     * Check to see if the content storage is depleted (bytes remaining is less than the configured requirement)
     *
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
        } catch (DeltafiApiException e) {
            if (diskSpaceAPIReachable) {
                log.warn("Unable to calculate storage depletion, error communicating with API: {}", e.getMessage());
                diskSpaceAPIReachable = false;
            }

            return false;
        }
    }

    /**
     * This method is a blocking call to the API to retrieve content storage disk metrics
     */
    public void getContentStorageDiskMetrics() {
        try {
            contentStorageMetrics = deltafiApiClient.contentMetrics();
        } catch (DeltafiApiException e) {
            log.warn("Unable to evaluate storage availability criteria, error communicating with API: {}", e.getMessage());
            contentStorageMetrics = null;
        }
    }

    /**
     * Get DiskMetrics for content storage with cache
     * @return Disk metrics
     * @throws DeltafiApiException when API is unavailable
     */
    public @NotNull DiskMetrics contentMetrics() throws DeltafiApiException {
        DiskMetrics retval = contentStorageMetrics;
        if (retval == null) throw new DeltafiApiException("Content Storage disk metrics unavailable from API");
        return retval;
    }

    private long requiredBytes() {
        return deltaFiPropertiesService.getDeltaFiProperties().getIngress().getDiskSpaceRequirementInMb() * 1000000;
    }
}
