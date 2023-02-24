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
package org.deltafi.core.join;

import java.time.OffsetDateTime;

public interface JoinRepoCustom {
    /**
     * Update or insert a locked JoinEntry with the provided DeltaFile id and index.
     * @param id the id consisting of flow, action, and join group
     * @param joinDate the date to force a join (set on insert)
     * @param maxDeltaFileEntries the maximum number of DeltaFiles to join (set on insert)
     * @param did the DeltaFile id to add
     * @param index the index of the DeltaFile to add
     * @return the locked JoinEntry
     * @throws org.springframework.dao.DuplicateKeyException if a locked JoinEntry already exists for the provided id
     */
    JoinEntry upsertAndLock(JoinEntryId id, OffsetDateTime joinDate, Integer maxDeltaFileEntries, String did,
            String index);

    /**
     * Lock a single JoinEntry with a join date less than or equal to the provided join date.
     * @param joinDate the join date
     * @return a locked JoinEntry or null if there are no entries with appropriate join dates
     */
    JoinEntry lockFirstBefore(OffsetDateTime joinDate);

    /**
     * Unlock a JoinEntry.
     * @param id the id of the JoinEntry to unlock.
     */
    void unlock(JoinEntryId id);

    /**
     * Unlock all JoinEntrys locked before the provided date.
     * @param lockDate the date before which JoinEntrys were locked to unlock
     * @return the number of JoinEntrys unlocked
     */
    long unlockBefore(OffsetDateTime lockDate);
}
