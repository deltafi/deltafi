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

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.exceptions.StorageCheckException;
import org.deltafi.core.services.DeltaFilesService;
import org.deltafi.core.services.DiskSpaceService;
import org.deltafi.core.types.DeltaFileDeleteDTO;
import org.deltafi.core.types.DiskMetrics;
import org.deltafi.core.types.DiskSpaceDeletePolicy;

import java.util.List;

@Getter
@Slf4j
public class DiskSpaceDelete extends DeletePolicyWorker {
    private final Integer maxPercent;
    private final String flow;
    private final DiskSpaceService diskSpaceService;

    public DiskSpaceDelete(DeltaFilesService deltaFilesService, DiskSpaceService diskSpaceService, DiskSpaceDeletePolicy policy) {
        super(deltaFilesService, policy.getName());

        this.diskSpaceService = diskSpaceService;
        this.maxPercent = policy.getMaxPercent();
        this.flow = policy.getFlow();
    }

    public boolean run() {
        DiskMetrics contentMetrics = null;
        try {
            contentMetrics = diskSpaceService.contentMetrics();
        } catch (StorageCheckException e) {
            log.warn("Unable to evaluate deletion criteria: {}", e.getMessage());
        }

        if (contentMetrics == null || contentMetrics.percentUsed() <= maxPercent) {
            return false;
        }

        log.info("Disk delete policy for {} executing: current used = {}%, maximum = {}%", flow == null ? "all flows" : flow, String.format("%.2f", contentMetrics.percentUsed()), maxPercent);
        long bytesToDelete = contentMetrics.bytesOverPercentage(maxPercent);
        log.info("Deleting up to {} bytes", bytesToDelete);
        while (bytesToDelete > 0) {
            List<DeltaFileDeleteDTO> deleted = deltaFilesService.diskSpaceDelete(bytesToDelete, flow, name);

            long bytesDeleted = deleted.stream().map(DeltaFileDeleteDTO::getTotalBytes).reduce(0L, Long::sum);
            bytesToDelete -= bytesDeleted;

            if (deleted.isEmpty()) {
                log.warn("No DeltaFiles deleted -- disk is above threshold despite all content already being deleted.");
                return false;
            }

            log.info("Deleted batch of {} files, {} bytes. Remaining: {} bytes", deleted.size(), bytesDeleted, Math.max(0, bytesToDelete));

            if (bytesToDelete > 0) {
                try {
                    contentMetrics = diskSpaceService.contentMetrics();
                    if (contentMetrics.percentUsed() <= maxPercent) {
                        log.info("Disk space delete batching stopped early due to disk usage below threshold.");
                        break;
                    }
                } catch (StorageCheckException ignored) {
                    // if the API is unreachable, continue deleting as planned
                }
            }
        }

        // disk space delete always runs until no more files should be deleted
        return false;
    }
}
