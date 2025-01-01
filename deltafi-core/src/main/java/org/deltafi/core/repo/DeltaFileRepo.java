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
package org.deltafi.core.repo;

import org.deltafi.core.generated.types.DeltaFileStats;
import org.deltafi.core.types.DeltaFile;
import org.deltafi.common.types.DeltaFileFlowState;
import org.deltafi.common.types.DeltaFileStage;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Repository
public interface DeltaFileRepo extends JpaRepository<DeltaFile, UUID>, DeltaFileRepoCustom {
    Page<DeltaFile> findAllByOrderByCreatedDesc(Pageable pageable);
    Page<DeltaFile> findAllByOrderByModifiedDesc(Pageable pageable);
    Page<DeltaFile> findByStageOrderByModifiedDesc(DeltaFileStage stage, Pageable pageable);
    Page<DeltaFile> findByNameOrderByCreatedDesc(String filename, Pageable pageable);

    @NotNull
    @Query("SELECT df FROM DeltaFile df " +
            "LEFT JOIN FETCH df.annotations " +
            "LEFT JOIN FETCH df.flows f " +
            "WHERE df.did = :did")
    Optional<DeltaFile> findById(@NotNull UUID did);

    @Query("SELECT df FROM DeltaFile df " +
            "LEFT JOIN FETCH df.annotations " +
            "LEFT JOIN FETCH df.flows f " +
            "WHERE df.did IN (:dids)")
    List<DeltaFile> findByIdsIn(@NotNull List<UUID> dids);

    /**
     * Find the DeltaFiles that include the given flowName in their pendingAnnotationsForFlows set
     * @param flowName name of dataSource to search for
     * @return stream of matching DeltaFiles
     */
    Stream<DeltaFile> findByTerminalAndFlowsNameAndFlowsState(boolean isTerminal, String flowName, DeltaFileFlowState state);

    Optional<DeltaFile> findByDidAndStageIn(UUID did, List<DeltaFileStage> stages);

    @Query(value = """
        WITH table_stats AS (
          SELECT CAST(reltuples AS BIGINT) as estimated_count
          FROM pg_class
          WHERE relname = 'delta_files'
        ),
        in_flight_stats AS (
          SELECT
            COUNT(*) as in_flight_count,
            COALESCE(SUM(referenced_bytes), 0) as in_flight_bytes
          FROM delta_files
          WHERE stage = 'IN_FLIGHT'
        )
        SELECT
          CASE
            WHEN ts.estimated_count < 100000 THEN (SELECT COUNT(*) FROM delta_files)
            ELSE ts.estimated_count
          END as estimate,
          ifs.in_flight_count as count,
          ifs.in_flight_bytes as total_bytes
        FROM table_stats ts
        CROSS JOIN in_flight_stats ifs
    """, nativeQuery = true)
    List<Object[]> getRawDeltaFileStats();

    /**
     * Get estimated count and sizes of deltaFiles in the system
     * @return stats
     */
    default DeltaFileStats deltaFileStats() {
        List<Object[]> rawStats = getRawDeltaFileStats();
        if (rawStats.isEmpty()) {
            return new DeltaFileStats(0L, 0L, 0L);  // or however you want to handle no results
        }
        Object[] row = rawStats.getFirst();
        return new DeltaFileStats(
                Math.max(0, ((Number) row[0]).longValue()),
                Math.max(0, ((Number) row[1]).longValue()),
                Math.max(0, ((Number) row[2]).longValue())
        );
    }
}