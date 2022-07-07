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
package org.deltafi.core.domain.delete;

import lombok.Getter;
import org.deltafi.core.domain.api.types.DiskSpaceDeletePolicy;
import org.deltafi.core.domain.services.DeltaFilesService;
import org.deltafi.core.domain.services.DiskSpaceService;
import org.deltafi.core.domain.services.api.model.DiskMetrics;

@Getter
public class DiskSpaceDelete extends DeletePolicyWorker {
    private final Integer maxPercent;
    private final String flow;
    private final DiskSpaceService diskSpaceService;

    public DiskSpaceDelete(DeltaFilesService deltaFilesService, DiskSpaceService diskSpaceService, DiskSpaceDeletePolicy policy) {
        super(deltaFilesService, policy.getId());

        this.diskSpaceService = diskSpaceService;
        this.maxPercent = policy.getMaxPercent();
        this.flow = policy.getFlow();
    }

    public void run() {
        DiskMetrics contentMetrics = diskSpaceService.contentMetrics();
        if (contentMetrics != null && contentMetrics.percentUsed() > maxPercent) {
            long bytesToDelete = contentMetrics.bytesOverPercentage(maxPercent);
            deltaFilesService.delete(bytesToDelete, flow, name, false);
        }
    }
}