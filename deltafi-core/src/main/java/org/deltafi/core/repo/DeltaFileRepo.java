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
package org.deltafi.core.repo;

import org.deltafi.core.generated.types.DeltaFileStats;
import org.deltafi.core.types.DeltaFile;
import org.deltafi.core.types.DeltaFileFlowState;
import org.deltafi.common.types.DeltaFileStage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Find the DeltaFiles that include the given flowName in their pendingAnnotationsForFlows set
     * @param flowName name of flow to search for
     * @return stream of matching DeltaFiles
     */
    Stream<DeltaFile> findByTerminalAndFlowsNameAndFlowsState(boolean isTerminal, String flowName, DeltaFileFlowState state);

    @Query(value = """
            SELECT COUNT(*)
            FROM delta_files df
            JOIN delta_file_flows dff ON df.did = dff.delta_file_id
            JOIN actions a ON dff.id = a.delta_file_flow_id
            WHERE df.stage = :stage
            AND a.state = 'ERROR'
            AND a.error_acknowledged IS NULL
            """, nativeQuery = true)
    long countByStageAndErrorAcknowledgedIsNull(@Param("stage") String stage);

    Optional<DeltaFile> findByDidAndStageIn(UUID did, List<DeltaFileStage> stages);

    /**
     * Get count and sizes of deltaFiles in the system
     * @return stats
     */
    @Query("""
            SELECT new org.deltafi.core.generated.types.DeltaFileStats(
              (SELECT COUNT(d) FROM DeltaFile d),
               COUNT(d) FILTER (WHERE d.stage = 'IN_FLIGHT'),
               COALESCE(SUM(d.referencedBytes) FILTER (WHERE d.stage = 'IN_FLIGHT'), 0))
            FROM DeltaFile d""")
    DeltaFileStats deltaFileStats();
}