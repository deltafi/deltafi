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

import org.deltafi.core.types.JoinDefinition;
import org.deltafi.core.types.JoinEntry;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface JoinEntryRepoCustom {
    void ensureJoinDefinitionIndex();

    /**
     * Update or insert a locked JoinEntry with the provided DeltaFile id and index.
     *
     * @param joinDefinition the id consisting of flow, action, and join group
     * @param joinDate the date to force a join (set on insert)
     * @param minNum the minimum number of DeltaFiles to join (set on insert)
     * @param maxNum the maximum number of DeltaFiles to join (set on insert)
     * @param flowDepth depth of the flow that caused the DeltaFile to join
     * @return the locked JoinEntry
     * @throws org.springframework.dao.DuplicateKeyException if a locked JoinEntry already exists for the provided id
     */
    JoinEntry upsertAndLock(JoinDefinition joinDefinition, OffsetDateTime joinDate, Integer minNum,
                            Integer maxNum, int flowDepth);

    /**
     * Lock a single JoinEntry with a join date less than or equal to the provided join date.
     *
     * @param joinDate the join date
     * @return a locked JoinEntry or null if there are no entries with appropriate join dates
     */
    JoinEntry lockOneBefore(OffsetDateTime joinDate);

    /**
     * Unlock a JoinEntry.
     *
     * @param id the id of the JoinEntry to unlock.
     */
    void unlock(UUID id);

    /**
     * Unlock all JoinEntries locked before the provided date.
     *
     * @param lockDate the date before which JoinEntries were locked to unlock
     * @return the number of JoinEntries unlocked
     */
    long unlockBefore(OffsetDateTime lockDate);
}
