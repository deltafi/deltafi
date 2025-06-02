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

import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.exceptions.StorageCheckException;
import org.deltafi.core.repo.DeltaFileRepo;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.deltafi.core.services.DeltaFilesService;
import org.deltafi.core.services.SystemService;
import org.deltafi.core.types.DiskMetrics;
import org.deltafi.core.types.MetadataStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetadataDeleteTest {

    @Mock
    private SystemService systemService;

    @Mock
    private DeltaFilesService deltaFilesService;

    @Mock
    private DeltaFiPropertiesService propertiesService;

    @Mock
    private DeltaFileRepo deltaFileRepo;

    @InjectMocks
    private MetadataDelete metadataDelete;

    private DeltaFiProperties deltaFiProperties;

    @BeforeEach
    void setUp() {
        deltaFiProperties = new DeltaFiProperties();
        deltaFiProperties.setMetadataDiskSpacePercentThreshold(80.0);
        deltaFiProperties.setDeletePolicyBatchSize(100);
        when(propertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);
    }

    @Test
    void run_storageCheckException_doesNotProceed() throws StorageCheckException {
        when(systemService.metadataNodesDiskMetrics()).thenThrow(new StorageCheckException("Disk unavailable"));

        metadataDelete.run();

        verify(deltaFileRepo, never()).metadataStats();
        verify(deltaFilesService, never()).timedDelete(any(), any(), anyLong(), any(), anyString(), anyBoolean(), anyInt(), anyBoolean());
    }

    @Test
    void run_nullDiskMetrics_doesNotProceed() throws StorageCheckException {
        when(systemService.metadataNodesDiskMetrics()).thenReturn(null);

        metadataDelete.run();

        verify(deltaFileRepo, never()).metadataStats();
    }

    @Test
    void run_emptyDiskMetrics_doesNotProceed() throws StorageCheckException {
        when(systemService.metadataNodesDiskMetrics()).thenReturn(Collections.emptyList());

        metadataDelete.run();

        verify(deltaFileRepo, never()).metadataStats();
    }


    @Test
    void run_totalDiskLimitZero_doesNotProceed() throws StorageCheckException {
        DiskMetrics metrics = new DiskMetrics("node1", 0L, 0L);
        when(systemService.metadataNodesDiskMetrics()).thenReturn(List.of(metrics));

        metadataDelete.run();

        verify(deltaFileRepo, never()).metadataStats();
    }

    @Test
    void run_metadataStatsUnchanged_skipsDeletion() throws StorageCheckException {
        DiskMetrics metrics = new DiskMetrics("node1", 1000L, 850L);
        when(systemService.metadataNodesDiskMetrics()).thenReturn(List.of(metrics));
        MetadataStats stats = new MetadataStats(850L, 10L);
        when(deltaFileRepo.metadataStats()).thenReturn(stats);
        deltaFiProperties.setMetadataDiskSpacePercentThreshold(80.0);
        when(propertiesService.getDeltaFiProperties()).thenReturn(deltaFiProperties);

        metadataDelete.run();
        metadataDelete.run();

        // timedDelete should be called once for the first run, but not for the second
        verify(deltaFilesService, times(1)).timedDelete(
                isNull(), any(OffsetDateTime.class), eq(0L), isNull(),
                eq(MetadataDelete.POLICY_NAME), eq(true), anyInt(), eq(true)
        );
        // metadataStats should be called twice (once per run call)
        verify(deltaFileRepo, times(2)).metadataStats();
    }

    @Test
    void run_diskUsageBelowThreshold_doesNotDelete() throws StorageCheckException {
        DiskMetrics metrics = new DiskMetrics("node1", 1000L, 700L);
        when(systemService.metadataNodesDiskMetrics()).thenReturn(List.of(metrics));
        MetadataStats stats = new MetadataStats(700L, 10L);
        when(deltaFileRepo.metadataStats()).thenReturn(stats);
        deltaFiProperties.setMetadataDiskSpacePercentThreshold(80.0);

        metadataDelete.run();

        verify(deltaFilesService, never()).timedDelete(any(), any(), anyLong(), any(), anyString(), anyBoolean(), anyInt(), anyBoolean());
    }

    @Test
    void run_noDeltaFiles_doesNotDelete() throws StorageCheckException {
        DiskMetrics metrics = new DiskMetrics("node1", 1000L, 850L);
        when(systemService.metadataNodesDiskMetrics()).thenReturn(List.of(metrics));
        MetadataStats stats = new MetadataStats(850L, 0L);
        when(deltaFileRepo.metadataStats()).thenReturn(stats);
        deltaFiProperties.setMetadataDiskSpacePercentThreshold(80.0);

        metadataDelete.run();

        verify(deltaFilesService, never()).timedDelete(any(), any(), anyLong(), any(), anyString(), anyBoolean(), anyInt(), anyBoolean());
    }

    @Test
    void run_zeroMetadataSize_doesNotDelete() throws StorageCheckException {
        DiskMetrics metrics = new DiskMetrics("node1", 1000L, 0L);
        when(systemService.metadataNodesDiskMetrics()).thenReturn(List.of(metrics));
        MetadataStats stats = new MetadataStats(0L, 10L);
        when(deltaFileRepo.metadataStats()).thenReturn(stats);
        deltaFiProperties.setMetadataDiskSpacePercentThreshold(80.0);

        metadataDelete.run();

        verify(deltaFilesService, never()).timedDelete(any(), any(), anyLong(), any(), anyString(), anyBoolean(), anyInt(), anyBoolean());
    }

    @Test
    void run_performsDeletion_singleBatch() throws StorageCheckException {
        DiskMetrics metrics = new DiskMetrics("node1", 1000L, 850L);
        when(systemService.metadataNodesDiskMetrics()).thenReturn(List.of(metrics));
        // Metadata size 850, total limit 1000, threshold 80% (max allowed 800)
        // Bytes to delete = 850 - 800 = 50
        // Delta file count 10, average size 85
        // Delta files to delete = ceil(50 / 85) = 1
        MetadataStats stats = new MetadataStats(850L, 10L);
        when(deltaFileRepo.metadataStats()).thenReturn(stats);
        deltaFiProperties.setMetadataDiskSpacePercentThreshold(80.0);
        deltaFiProperties.setDeletePolicyBatchSize(100);

        when(deltaFilesService.timedDelete(isNull(), any(OffsetDateTime.class), eq(0L), isNull(), eq(MetadataDelete.POLICY_NAME), eq(true), eq(1), eq(true)))
                .thenReturn(true);

        metadataDelete.run();

        verify(deltaFilesService, times(1)).timedDelete(
                isNull(), any(OffsetDateTime.class), eq(0L), isNull(),
                eq(MetadataDelete.POLICY_NAME), eq(true), eq(1), eq(true)
        );
    }

    @Test
    void run_performsDeletion_multipleBatches() throws StorageCheckException {
        DiskMetrics metrics = new DiskMetrics("node1", 10000L, 9000L);
        when(systemService.metadataNodesDiskMetrics()).thenReturn(List.of(metrics));
        // Metadata size 9000, total limit 10000, threshold 80% (max allowed 8000)
        // Bytes to delete = 9000 - 8000 = 1000
        // Delta file count 100, average size 90
        // Delta files to delete = ceil(1000 / 90) = ceil(11.11) = 12
        MetadataStats stats = new MetadataStats(9000L, 100L);
        when(deltaFileRepo.metadataStats()).thenReturn(stats);
        deltaFiProperties.setMetadataDiskSpacePercentThreshold(80.0);
        deltaFiProperties.setDeletePolicyBatchSize(5); // Batch size 5

        when(deltaFilesService.timedDelete(isNull(), any(OffsetDateTime.class), eq(0L), isNull(), eq(MetadataDelete.POLICY_NAME), eq(true), eq(5), eq(true)))
                .thenReturn(true); // First two batches of 5
        when(deltaFilesService.timedDelete(isNull(), any(OffsetDateTime.class), eq(0L), isNull(), eq(MetadataDelete.POLICY_NAME), eq(true), eq(2), eq(true)))
                .thenReturn(true); // Last batch of 2

        metadataDelete.run();

        verify(deltaFilesService, times(2)).timedDelete(
                isNull(), any(OffsetDateTime.class), eq(0L), isNull(),
                eq(MetadataDelete.POLICY_NAME), eq(true), eq(5), eq(true)
        );
        verify(deltaFilesService, times(1)).timedDelete(
                isNull(), any(OffsetDateTime.class), eq(0L), isNull(),
                eq(MetadataDelete.POLICY_NAME), eq(true), eq(2), eq(true)
        );
    }

    @Test
    void run_performsDeletion_timedDeleteReturnsFalse_stopsEarly() throws StorageCheckException {
        DiskMetrics metrics = new DiskMetrics("node1", 10000L, 9000L);
        when(systemService.metadataNodesDiskMetrics()).thenReturn(List.of(metrics));
        MetadataStats stats = new MetadataStats(9000L, 100L); // Files to delete = 12 (as per previous test)
        when(deltaFileRepo.metadataStats()).thenReturn(stats);
        deltaFiProperties.setMetadataDiskSpacePercentThreshold(80.0);
        deltaFiProperties.setDeletePolicyBatchSize(5);

        // first batch succeeds, second batch doesn't delete fully
        when(deltaFilesService.timedDelete(isNull(), any(OffsetDateTime.class), eq(0L), isNull(), eq(MetadataDelete.POLICY_NAME), eq(true), eq(5), eq(true)))
                .thenReturn(true)
                .thenReturn(false);

        metadataDelete.run();

        // Should attempt two batches. The first with 5, the second with 5.
        verify(deltaFilesService, times(2)).timedDelete(
                isNull(), any(OffsetDateTime.class), eq(0L), isNull(),
                eq(MetadataDelete.POLICY_NAME), eq(true), eq(5), eq(true)
        );
        // Should not attempt the third batch of 2 because the second returned false.
        verify(deltaFilesService, never()).timedDelete(
                isNull(), any(OffsetDateTime.class), eq(0L), isNull(),
                eq(MetadataDelete.POLICY_NAME), eq(true), eq(2), eq(true)
        );
    }

    @Test
    void run_lastProcessedStatsUpdatedCorrectly() throws StorageCheckException {
        DiskMetrics metrics = new DiskMetrics("node1", 1000L, 850L);
        MetadataStats initialStats = new MetadataStats(850L, 10L);
        double initialMaxPercent = 75.0;
        deltaFiProperties.setMetadataDiskSpacePercentThreshold(initialMaxPercent);

        when(systemService.metadataNodesDiskMetrics()).thenReturn(List.of(metrics));
        when(deltaFileRepo.metadataStats()).thenReturn(initialStats);
        when(deltaFilesService.timedDelete(any(), any(), anyLong(), any(), anyString(), anyBoolean(), anyInt(), anyBoolean())).thenReturn(true);

        metadataDelete.run();

        // First run should have triggered deletion logic and updated lastProcessedStats
        verify(deltaFilesService, atLeastOnce()).timedDelete(any(), any(), anyLong(), any(), anyString(), anyBoolean(), anyInt(), anyBoolean());

        // Second run, currentMaxPercent changes

        DiskMetrics newMetrics = new DiskMetrics("node1", 1000L, 850L); // Same limit as first run
        MetadataStats newStats = new MetadataStats(850L, 10L); // Same stats as first run
        double newMaxPercent = 70.0; // New threshold
        deltaFiProperties.setMetadataDiskSpacePercentThreshold(newMaxPercent);

        reset(deltaFilesService); // Reset to check if it's called in the second run
        when(systemService.metadataNodesDiskMetrics()).thenReturn(List.of(newMetrics));
        when(deltaFileRepo.metadataStats()).thenReturn(newStats);
        when(deltaFilesService.timedDelete(any(), any(), anyLong(), any(), anyString(), anyBoolean(), anyInt(), anyBoolean())).thenReturn(true);

        metadataDelete.run();

        // Second run should also proceed because currentMaxPercent changed
        verify(deltaFilesService, atLeastOnce()).timedDelete(any(), any(), anyLong(), any(), anyString(), anyBoolean(), anyInt(), anyBoolean());
    }

    @Test
    void run_totalLimitChanges_triggersReEvaluation() throws StorageCheckException {
        DiskMetrics metrics1 = new DiskMetrics("node1", 1000L, 850L);
        MetadataStats stats1 = new MetadataStats(850L, 10L);
        deltaFiProperties.setMetadataDiskSpacePercentThreshold(80.0);
        when(systemService.metadataNodesDiskMetrics()).thenReturn(List.of(metrics1));
        when(deltaFileRepo.metadataStats()).thenReturn(stats1);
        when(deltaFilesService.timedDelete(any(), any(), anyLong(), any(), anyString(), anyBoolean(), anyInt(), anyBoolean())).thenReturn(true);

        metadataDelete.run();
        verify(deltaFilesService, times(1)).timedDelete(any(), any(), anyLong(), any(), anyString(), anyBoolean(), anyInt(), eq(true));

        // Second run with different totalLimit but same stats and threshold
        reset(deltaFilesService);
        DiskMetrics metrics2 = new DiskMetrics("node1", 2000L, 850L); // New total limit, same actual usage
        when(systemService.metadataNodesDiskMetrics()).thenReturn(List.of(metrics2));

        metadataDelete.run();

        // The new percentage (42.5%) is below threshold (80%), so no actual delete call
        verify(deltaFilesService, never()).timedDelete(any(), any(), anyLong(), any(), anyString(), anyBoolean(), anyInt(), eq(true));
    }


    @Test
    void run_maxPercentPropertyChanges_triggersReEvaluation() throws StorageCheckException {
        DiskMetrics metrics1 = new DiskMetrics("node1", 1000L, 850L);
        MetadataStats stats1 = new MetadataStats(850L, 10L);
        deltaFiProperties.setMetadataDiskSpacePercentThreshold(86.0); // Usage 85%, threshold 86% -> no delete
        when(systemService.metadataNodesDiskMetrics()).thenReturn(List.of(metrics1));
        when(deltaFileRepo.metadataStats()).thenReturn(stats1);

        metadataDelete.run();
        verify(deltaFilesService, never()).timedDelete(any(), any(), anyLong(), any(), anyString(), anyBoolean(), anyInt(), eq(true));

        // Second run with different maxPercent but same stats and totalLimit
        reset(deltaFilesService);
        deltaFiProperties.setMetadataDiskSpacePercentThreshold(80.0); // New threshold, now usage 85% > threshold 80%
        when(deltaFilesService.timedDelete(any(), any(), anyLong(), any(), anyString(), anyBoolean(), anyInt(), anyBoolean())).thenReturn(true);


        metadataDelete.run();

        // Deletion logic should run again because maxPercent changed, and this time it should delete
        verify(deltaFilesService, times(1)).timedDelete(any(), any(), anyLong(), any(), anyString(), anyBoolean(), anyInt(), eq(true));
    }
}