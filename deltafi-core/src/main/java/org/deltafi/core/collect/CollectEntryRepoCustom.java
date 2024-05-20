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
package org.deltafi.core.collect;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface CollectEntryRepoCustom {
    void ensureCollectDefinitionIndex();

    /**
     * Update or insert a locked CollectEntry with the provided DeltaFile id and index.
     *
     * @param collectDefinition the id consisting of flow, action, and collect group
     * @param collectDate the date to force a collect (set on insert)
     * @param minNum the minimum number of DeltaFiles to collect (set on insert)
     * @param maxNum the maximum number of DeltaFiles to collect (set on insert)
     * @param flowDepth depth of the flow that caused the DeltaFile to join
     * @return the locked CollectEntry
     * @throws org.springframework.dao.DuplicateKeyException if a locked CollectEntry already exists for the provided id
     */
    CollectEntry upsertAndLock(CollectDefinition collectDefinition, OffsetDateTime collectDate, Integer minNum,
            Integer maxNum, int flowDepth);

    /**
     * Lock a single CollectEntry with a collect date less than or equal to the provided collect date.
     *
     * @param collectDate the collect date
     * @return a locked CollectEntry or null if there are no entries with appropriate collect dates
     */
    CollectEntry lockOneBefore(OffsetDateTime collectDate);

    /**
     * Unlock a CollectEntry.
     *
     * @param id the id of the CollectEntry to unlock.
     */
    void unlock(UUID id);

    /**
     * Unlock all CollectEntrys locked before the provided date.
     *
     * @param lockDate the date before which CollectEntrys were locked to unlock
     * @return the number of CollectEntrys unlocked
     */
    long unlockBefore(OffsetDateTime lockDate);
}
