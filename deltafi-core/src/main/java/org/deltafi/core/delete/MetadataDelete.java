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
import org.deltafi.core.repo.DeltaFileRepo;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.deltafi.core.services.DeltaFilesService;
import org.deltafi.core.services.SystemService;
import org.deltafi.core.types.DiskMetrics;
import org.deltafi.core.types.MetadataStats;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataDelete {
    public static final String POLICY_NAME = "Metadata Policy";
    private final SystemService systemService;
    private final DeltaFilesService deltaFilesService;
    private final DeltaFiPropertiesService propertiesService;
    private final DeltaFileRepo deltaFileRepo;

    private MetadataStats lastProcessedMetadataStats = null;
    private Long lastProcessedTotalLimit = null;
    private Double lastProcessedMaxPercent = null;

    public void run() {
        double currentMaxPercent = propertiesService.getDeltaFiProperties().getMetadataDiskSpacePercentThreshold();
        int batchSize = propertiesService.getDeltaFiProperties().getDeletePolicyBatchSize();
        List<DiskMetrics> metadataMetrics = null;
        try {
            metadataMetrics = systemService.metadataNodesDiskMetrics();
        } catch (StorageCheckException e) {
            log.warn("Unable to evaluate deletion criteria: {}", e.getMessage());
        }

        if (metadataMetrics == null) {
            return;
        }

        long currentTotalLimit = metadataMetrics.stream().map(DiskMetrics::getLimit).reduce(0L, Long::sum);
        if (currentTotalLimit == 0L) {
            log.warn("Metadata total disk limit is zero");
            return;
        }

        MetadataStats currentMetadataStats = deltaFileRepo.metadataStats();
        double metadataPercent = (double) currentMetadataStats.metadataSize() / currentTotalLimit * 100.0;

        log.info("Evaluating metadata disk space: current used = {}%, maximum = {}%",
                String.format("%.2f", metadataPercent), String.format("%.2f", currentMaxPercent));

        if (metadataPercent < currentMaxPercent || currentMetadataStats.deltaFileCount() == 0 || currentMetadataStats.metadataSize() == 0) {
            return;
        }

        if (lastProcessedMetadataStats != null &&
                lastProcessedTotalLimit != null &&
                lastProcessedMaxPercent != null &&
                lastProcessedMetadataStats.metadataSize() == currentMetadataStats.metadataSize() &&
                lastProcessedMetadataStats.deltaFileCount() == currentMetadataStats.deltaFileCount() &&
                Objects.equals(lastProcessedTotalLimit, currentTotalLimit) &&
                Objects.equals(lastProcessedMaxPercent, currentMaxPercent)) {
            log.info("Metadata stats (size: {} bytes, count: {} files) are unchanged since the last evaluation," +
                            "skipping deletion until database stats refresh.",
                    currentMetadataStats.metadataSize(), currentMetadataStats.deltaFileCount());
            return;
        }

        this.lastProcessedMetadataStats = currentMetadataStats;
        this.lastProcessedTotalLimit = currentTotalLimit;
        this.lastProcessedMaxPercent = currentMaxPercent;

        long maxAllowed = (long) (currentTotalLimit * currentMaxPercent / 100.0);
        long bytesToDelete =  currentMetadataStats.metadataSize() - maxAllowed;
        int deltaFilesToDelete = 0;
        if (bytesToDelete > 0) {
            double averageBytesPerFile = (double) currentMetadataStats.metadataSize() / currentMetadataStats.deltaFileCount();
            deltaFilesToDelete = (int) Math.ceil(bytesToDelete / averageBytesPerFile);
            deltaFilesToDelete = Math.min(deltaFilesToDelete, (int) currentMetadataStats.deltaFileCount());
        }

        boolean fullBatch = true;
        while (deltaFilesToDelete > 0 && fullBatch) {
            log.info("{} more deltaFiles to delete to reclaim metadata space", deltaFilesToDelete);
            int currentBatchSize = Math.min(batchSize, deltaFilesToDelete);
            fullBatch = deltaFilesService.timedDelete(null, OffsetDateTime.now(), 0, null, POLICY_NAME, true, currentBatchSize, true);
            deltaFilesToDelete -= currentBatchSize;
        }
    }
}
