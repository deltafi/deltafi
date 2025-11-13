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
package org.deltafi.core.delete;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.exceptions.StorageCheckException;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.deltafi.core.services.DeltaFilesService;
import org.deltafi.core.services.SystemService;
import org.deltafi.core.types.DeltaFileDeleteDTO;
import org.deltafi.core.types.DiskMetrics;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiskSpaceDelete {
    public static final String POLICY_NAME = "Disk Space Policy";
    private final SystemService systemService;
    private final DeltaFilesService deltaFilesService;
    private final DeltaFiPropertiesService propertiesService;

    public boolean run() {
        double maxPercent = propertiesService.getDeltaFiProperties().getDiskSpacePercentThreshold();
        int batchSize = propertiesService.getDeltaFiProperties().getDeletePolicyBatchSize();
        List<DiskMetrics> contentMetrics = null;
        try {
            contentMetrics = systemService.contentNodesDiskMetrics();
        } catch (StorageCheckException e) {
            log.warn("Unable to evaluate deletion criteria: {}", e.getMessage());
        }

        if (contentMetrics == null || contentMetrics.isEmpty()) {
            return false;
        }

        double currentUsed = contentMetrics.stream().map(DiskMetrics::percentUsed).reduce(0D, Double::max);
        log.info("Evaluating content disk space: current used = {}%, maximum = {}%",
                String.format("%.2f", currentUsed), String.format("%.2f", maxPercent));

        if (contentMetrics.stream().map(DiskMetrics::percentUsed).reduce(0D, Double::max) <= maxPercent) {
            return false;
        }

        long bytesToDelete = contentMetrics.stream().map(c -> c.bytesOverPercentage(maxPercent)).reduce(0L, Long::max);
        log.info("Deleting up to {} bytes", bytesToDelete);
        List<DeltaFileDeleteDTO> deleted = deltaFilesService.diskSpaceDelete(bytesToDelete, batchSize);

        long bytesDeleted = deleted.stream().map(DeltaFileDeleteDTO::getTotalBytes).reduce(0L, Long::sum);
        bytesToDelete -= bytesDeleted;

        if (deleted.isEmpty()) {
            log.warn("No DeltaFiles deleted -- disk is above threshold despite all content already being deleted.");
            return false;
        }

        log.info("Deleted batch of {} files, {} bytes. Remaining: {} bytes", deleted.size(), bytesDeleted, Math.max(0, bytesToDelete));

        if (bytesToDelete > 0) {
            try {
                contentMetrics = systemService.contentNodesDiskMetrics();
                if (contentMetrics.stream().allMatch(c -> c.percentUsed() <= maxPercent)) {
                    log.info("Disk space delete batching stopped early due to disk usage below threshold.");
                    return false;
                }

                return true;
            } catch (StorageCheckException ignored) {
                return true;
            }
        }

        return false;
    }
}
