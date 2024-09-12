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

import org.deltafi.core.types.DeltaFileFlow;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeltaFileFlowRepo extends JpaRepository<DeltaFileFlow, UUID> {
    @NotNull
    @EntityGraph(value = "deltaFile.withActions")
    Optional<DeltaFileFlow> findById(@NotNull UUID did);

    @Query("SELECT df FROM DeltaFileFlow df WHERE df.deltaFile.did IN :deltaFileIds")
    List<DeltaFileFlow> findAllByDeltaFileIds(@Param("deltaFileIds") List<UUID> deltaFileIds);

    @Query("SELECT df FROM DeltaFileFlow df WHERE df.deltaFile.did IN :deltaFileIds AND df.number = 0")
    List<DeltaFileFlow> findAllByDeltaFileIdsAndFlowZero(@Param("deltaFileIds") List<UUID> deltaFileIds);
}