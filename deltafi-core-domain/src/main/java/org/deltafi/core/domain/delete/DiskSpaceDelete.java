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
import org.deltafi.core.domain.services.DeltaFilesService;
import org.deltafi.core.domain.services.DiskSpaceService;
import org.deltafi.core.domain.services.api.model.DiskMetrics;

import java.util.Map;

@Getter
public class DiskSpaceDelete extends DeletePolicy {
    private final Integer maxPercent;
    private final String flow;
    private final DiskSpaceService diskSpaceService;

    final static String TYPE = "diskSpace";

    public DiskSpaceDelete(DeltaFilesService deltaFilesService, DiskSpaceService diskSpaceService, String name, Map<String, String> parameters) {
        super(deltaFilesService, name, parameters);

        this.diskSpaceService = diskSpaceService;
        this.maxPercent = getParameters().containsKey("maxPercent") ? Integer.parseInt(getParameters().get("maxPercent")) : null;

        if (maxPercent == null) {
            throw new IllegalArgumentException("Disk space delete policy " + name + " must specify maxPercent");
        }

        this.flow = getParameters().get("flow");
    }

    public void run() {
        DiskMetrics contentMetrics = diskSpaceService.contentMetrics();
        if (contentMetrics != null && contentMetrics.percentUsed() > maxPercent) {
            long bytesToDelete = contentMetrics.bytesOverPercentage(maxPercent);
            deltaFilesService.delete(bytesToDelete, flow, name, false);
        }
    }
}