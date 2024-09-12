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

import org.springframework.transaction.annotation.Transactional;
import org.deltafi.core.types.JoinEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JoinEntryRepo extends JpaRepository<JoinEntry, UUID> {
    List<JoinEntry> findAllByOrderByJoinDate();

    /**
     * Update or insert a locked JoinEntry with the provided DeltaFile id and index.
     *
     * @param joinDefinition the id consisting of flow, action, and join group
     * @param joinDate the date to force a join (set on insert)
     * @param minNum the minimum number of DeltaFiles to join (set on insert)
     * @param maxNum the maximum number of DeltaFiles to join (set on insert)
     * @param maxFlowDepth depth of the flow that caused the DeltaFile to join
     * @return the locked JoinEntry
     * @throws org.springframework.dao.DuplicateKeyException if a locked JoinEntry already exists for the provided id
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO join_entries (id, join_definition, locked, locked_time, join_date, min_num, max_num, max_flow_depth, count)
            VALUES (:id, CAST(:joinDefinition AS jsonb), true, :lockedTime, :joinDate, :minNum, :maxNum, :maxFlowDepth, 1)
            ON CONFLICT (join_definition) DO UPDATE SET
                locked = true,
                locked_time = :lockedTime,
                max_flow_depth = GREATEST(join_entries.max_flow_depth, :maxFlowDepth),
                count = join_entries.count + 1
            WHERE join_entries.locked = false
            """, nativeQuery = true)
    int upsertAndLock(UUID id, String joinDefinition, OffsetDateTime lockedTime,
                      OffsetDateTime joinDate, Integer minNum, Integer maxNum,
                      int maxFlowDepth);

    @Query(value = "SELECT * FROM join_entries WHERE join_definition = CAST(:joinDefinition AS jsonb)", nativeQuery = true)
    Optional<JoinEntry> findByJoinDefinition(String joinDefinition);

    /**
     * Lock a single JoinEntry with a join date less than or equal to the provided join date.
     *
     * @param joinDate the join date
     * @return a locked JoinEntry or null if there are no entries with appropriate join dates
     */
    @Query(value = "WITH updated AS ( " +
            "UPDATE join_entries SET locked = true, locked_time = CURRENT_TIMESTAMP " +
            "WHERE id = (SELECT id FROM join_entries WHERE join_date <= :joinDate AND locked = false LIMIT 1) " +
            "RETURNING * " +
            ") " +
            "SELECT * FROM UPDATED",
            nativeQuery = true)
    Optional<JoinEntry> lockOneBefore(OffsetDateTime joinDate);

    /**
     * Unlock a JoinEntry.
     *
     * @param id the id of the JoinEntry to unlock.
     */
    @Modifying
    @Transactional
    @Query("UPDATE JoinEntry j SET j.locked = false, j.lockedTime = null WHERE j.id = :id")
    void unlock(UUID id);

    /**
     * Unlock all JoinEntries locked before the provided date.
     *
     * @param lockDate the date before which JoinEntries were locked to unlock
     * @return the number of JoinEntries unlocked
     */
    @Modifying
    @Transactional
    @Query("UPDATE JoinEntry j SET j.locked = false, j.lockedTime = null WHERE j.lockedTime < :lockDate")
    int unlockBefore(OffsetDateTime lockDate);
}
