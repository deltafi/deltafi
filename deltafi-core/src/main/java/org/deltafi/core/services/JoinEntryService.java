/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.uuid.Generators;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.repo.JoinEntryDidRepo;
import org.deltafi.core.repo.JoinEntryRepo;
import org.deltafi.core.types.JoinDefinition;
import org.deltafi.core.types.JoinEntry;
import org.deltafi.core.types.JoinEntryDid;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JoinEntryService {
    public static final int ORPHAN_BATCH_SIZE = 5000;
    private final Clock clock;
    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final JoinEntryRepo joinEntryRepo;
    private final JoinEntryDidRepo joinEntryDidRepo;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    public JoinEntry upsertAndLock(JoinDefinition joinDefinition, OffsetDateTime joinDate, Integer minNum,
                                   Integer maxNum, int flowDepth, UUID did) {
        JoinEntry joinEntry = upsertAndLock(joinDefinition, joinDate, minNum, maxNum, flowDepth);
        if (joinEntry == null) {
            return null;
        }
        joinEntryDidRepo.save(new JoinEntryDid(joinEntry.getId(), did));
        return joinEntry;
    }

    private JoinEntry upsertAndLock(JoinDefinition joinDefinition, OffsetDateTime joinDate, Integer minNum,
                                    Integer maxNum, int flowDepth) {
        long endTimeMs = clock.millis() + deltaFiPropertiesService.getDeltaFiProperties().getJoinAcquireLockTimeoutMs();
        UUID id = Generators.timeBasedEpochGenerator().generate();
        String joinDefinitionJson;
        try {
            joinDefinitionJson = OBJECT_MAPPER.writeValueAsString(joinDefinition);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize join definition", e);
            return null;
        }
        while (clock.millis() < endTimeMs) {
            OffsetDateTime lockedTime = OffsetDateTime.now(clock);

            JoinEntry joinEntry = joinEntryRepo.upsertAndLock(id, joinDefinitionJson, lockedTime,
                    joinDate, minNum, maxNum, flowDepth);

            if (joinEntry != null) {
                return joinEntry;
            }

            // If we couldn't insert or update, sleep before retrying
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        // time out and give up
        return null;
    }

    public Optional<JoinEntry> lockOneBefore(OffsetDateTime joinDate) {
        return joinEntryRepo.lockOneBefore(joinDate);
    }

    public void unlock(UUID joinEntryId) {
        joinEntryRepo.unlock(joinEntryId);
    }

    public long unlockBefore(OffsetDateTime lockDate) {
        return joinEntryRepo.unlockBefore(lockDate);
    }

    public List<UUID> findJoinedDids(UUID joinEntryId) {
        return joinEntryDidRepo.findByJoinEntryId(joinEntryId).stream().map(JoinEntryDid::getDid).toList();
    }

    public List<JoinEntryDid> findOrphans() {
        return joinEntryDidRepo.findByOrphanIsTrue(Limit.of(ORPHAN_BATCH_SIZE));
    }

    @Transactional
    public void handleProcessedJoinEntry(JoinEntry joinEntry, List<UUID> processed, String reason, boolean hadOrphans) {
        UUID joinId = joinEntry.getId();
        if (hadOrphans) {
            // delete entries that were successfully processed first
            delete(joinId, processed);
            // mark anything left with that joinId as errored
            markOrphans(joinId, reason, joinEntry.getJoinDefinition().getAction());
        } else {
            // if there are no orphans remove everything with the joinId
            delete(joinId);
        }
    }

    public void markOrphans(UUID joinId, String errorReason, String actionName) {
        joinEntryDidRepo.setOrphanState(joinId, errorReason, actionName);
    }

    public void delete(UUID joinEntryId, List<UUID> dids) {
        joinEntryRepo.deleteById(joinEntryId);
        joinEntryDidRepo.deleteAllByJoinEntryIdAndDidIn(joinEntryId, dids);
    }

    public void delete(UUID joinEntryId) {
        joinEntryRepo.deleteById(joinEntryId);
        joinEntryDidRepo.deleteByJoinEntryId(joinEntryId);
    }

    public void delete(List<UUID> ids) {
        if (ids == null) {
            return;
        }
        joinEntryRepo.deleteAllById(ids);
    }
}
