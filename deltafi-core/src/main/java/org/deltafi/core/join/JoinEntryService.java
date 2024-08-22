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

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JoinEntryService {
    private final Clock clock;
    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final JoinEntryRepo joinEntryRepo;
    private final JoinEntryDidRepo joinEntryDidRepo;

    @PostConstruct
    public void init() {
        joinEntryRepo.ensureJoinDefinitionIndex();
    }

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
        JoinEntry joinEntry = null;
        long endTimeMs = clock.millis() + deltaFiPropertiesService.getDeltaFiProperties().getJoinAcquireLockTimeoutMs();
        while ((joinEntry == null) && (clock.millis() < endTimeMs)) {
            try {
                joinEntry = joinEntryRepo.upsertAndLock(joinDefinition, joinDate, minNum, maxNum, flowDepth);
            } catch (DuplicateKeyException e) {
                // Tried to insert duplicate while other was locked. Sleep and try again.
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return joinEntry;
    }

    public JoinEntry lockOneBefore(OffsetDateTime joinDate) {
        return joinEntryRepo.lockOneBefore(joinDate);
    }

    public void unlock(UUID joinEntryId) {
        joinEntryRepo.unlock(joinEntryId);
    }

    public long unlockBefore(OffsetDateTime lockDate) {
        return joinEntryRepo.unlockBefore(lockDate);
    }

    public List<JoinEntry> findJoinEntriesByJoinDate() {
        return joinEntryRepo.findAllByOrderByJoinDate();
    }

    public List<UUID> findJoinedDids(UUID joinEntryId) {
        return joinEntryDidRepo.findByJoinEntryId(joinEntryId).stream().map(JoinEntryDid::getDid).toList();
    }

    public void delete(UUID joinEntryId) {
        joinEntryRepo.deleteById(joinEntryId);
        joinEntryDidRepo.deleteByJoinEntryId(joinEntryId);
    }
}