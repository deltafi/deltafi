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
package org.deltafi.core.delete;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.DeltaFile;
import org.deltafi.core.exceptions.DeltafiApiException;
import org.deltafi.core.services.DeltaFilesService;
import org.deltafi.core.services.DiskSpaceService;
import org.deltafi.core.services.api.model.DiskMetrics;
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

    public void run() {
        try {
            DiskMetrics contentMetrics = diskSpaceService.uncachedContentMetrics();

            if (contentMetrics != null && contentMetrics.percentUsed() > maxPercent) {
                log.info("Disk delete policy for " + (flow == null ? "all flows" : flow) + " executing: current used = " + contentMetrics.percentUsed() + "%, maximum = " + maxPercent + "%");
                long bytesToDelete = contentMetrics.bytesOverPercentage(maxPercent);
                log.info("Deleting up to " + bytesToDelete + " bytes");
                List<DeltaFile> deleted = deltaFilesService.delete(bytesToDelete, flow, name, false);
                if (deleted.isEmpty()) {
                    log.warn("No DeltaFiles deleted -- disk is above threshold despite all content already being deleted.");
                }
            }
        } catch (DeltafiApiException e) {
            log.warn("DeltaFi API is unreachable.  Unable to evaluate deletion criteria.");
        }
    }
}