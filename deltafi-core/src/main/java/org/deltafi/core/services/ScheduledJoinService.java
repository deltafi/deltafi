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
package org.deltafi.core.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.types.JoinEntry;
import org.deltafi.core.types.JoinEntryDid;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledJoinService {

    private final Clock clock;
    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final JoinEntryService joinEntryService;
    private final DeltaFilesService deltaFilesService;

    public void handleTimedOutJoins() {
        log.debug("Handling timed out joins");

        Optional<JoinEntry> maybeJoinEntry = joinEntryService.lockOneBefore(OffsetDateTime.now(clock));
        while (maybeJoinEntry.isPresent()) {
            JoinEntry joinEntry = maybeJoinEntry.get();

            List<UUID> joinedDids = joinEntryService.findJoinedDids(joinEntry.getId());
            if ((joinEntry.getMinNum() != null) && (joinEntry.getCount() < joinEntry.getMinNum())) {
                String errorReason = String.format("Join incomplete: Timed out after receiving %s of %s files",
                        joinEntry.getCount(), joinEntry.getMinNum());

                List<UUID> processed = deltaFilesService.failTimedOutJoin(joinEntry, joinedDids, errorReason);
                joinEntryService.handleProcessedJoinEntry(joinEntry, processed, errorReason, joinedDids.size() > processed.size());
            } else {
                List<UUID> processed = deltaFilesService.queueTimedOutJoin(joinEntry, joinedDids);
                joinEntryService.handleProcessedJoinEntry(joinEntry, processed, null,joinedDids.size() > processed.size());
            }

            maybeJoinEntry = joinEntryService.lockOneBefore(OffsetDateTime.now(clock));
        }
    }

    public void handleOrphanedJoins() {
        log.debug("Handling orphaned joins");
        List<JoinEntryDid> orphans = joinEntryService.findOrphans();

        Set<UUID> processedUuids = new HashSet<>();
        while (!orphans.isEmpty()) {
            List<UUID> complete = deltaFilesService.handleOrphanedJoins(orphans);
            joinEntryService.delete(complete);
            orphans.forEach(orphan -> processedUuids.add(orphan.getId()));
            orphans = joinEntryService.findOrphans();
            orphans.removeIf(orphan -> processedUuids.contains(orphan.getId()));
        }
    }

    public void unlockTimedOutJoinEntryLocks() {
        long numUnlocked = joinEntryService.unlockBefore(OffsetDateTime.now(clock)
                .minus(deltaFiPropertiesService.getDeltaFiProperties().getJoinMaxLockDuration()));

        if (numUnlocked > 0) {
            log.warn("Unlocked {} timed out join entries", numUnlocked);
        }
    }
}
