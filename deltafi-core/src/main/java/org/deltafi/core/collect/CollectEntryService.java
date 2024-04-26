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
public class CollectEntryService {
    private final Clock clock;
    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final CollectEntryRepo collectEntryRepo;
    private final CollectEntryDidRepo collectEntryDidRepo;

    @PostConstruct
    public void init() {
        collectEntryRepo.ensureCollectDefinitionIndex();
    }

    public CollectEntry upsertAndLock(CollectDefinition collectDefinition, OffsetDateTime collectDate, Integer minNum,
            Integer maxNum, UUID did) {
        CollectEntry collectEntry = upsertAndLock(collectDefinition, collectDate, minNum, maxNum);
        if (collectEntry == null) {
            return null;
        }
        collectEntryDidRepo.save(new CollectEntryDid(collectEntry.getId(), did));
        return collectEntry;
    }

    private CollectEntry upsertAndLock(CollectDefinition collectDefinition, OffsetDateTime collectDate, Integer minNum,
            Integer maxNum) {
        CollectEntry collectEntry = null;
        long endTimeMs = clock.millis() + deltaFiPropertiesService.getDeltaFiProperties().getCollect().getAcquireLockTimeoutMs();
        while ((collectEntry == null) && (clock.millis() < endTimeMs)) {
            try {
                collectEntry = collectEntryRepo.upsertAndLock(collectDefinition, collectDate, minNum, maxNum);
            } catch (DuplicateKeyException e) {
                // Tried to insert duplicate while other was locked. Sleep and try again.
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {}
            }
        }
        return collectEntry;
    }

    public CollectEntry lockOneBefore(OffsetDateTime collectDate) {
        return collectEntryRepo.lockOneBefore(collectDate);
    }

    public void unlock(String collectEntryId) {
        collectEntryRepo.unlock(collectEntryId);
    }

    public long unlockBefore(OffsetDateTime lockDate) {
        return collectEntryRepo.unlockBefore(lockDate);
    }

    public List<CollectEntry> findCollectEntriesByCollectDate() {
        return collectEntryRepo.findAllByOrderByCollectDate();
    }

    public List<UUID> findCollectedDids(UUID collectEntryId) {
        return collectEntryDidRepo.findByCollectEntryId(collectEntryId).stream().map(CollectEntryDid::getDid).toList();
    }

    public void delete(UUID collectEntryId) {
        collectEntryRepo.deleteById(collectEntryId);
        collectEntryDidRepo.deleteByCollectEntryId(collectEntryId);
    }
}
