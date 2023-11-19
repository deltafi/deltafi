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
package org.deltafi.core.services;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.DeltaFile;
import org.deltafi.core.repo.DeltaFileRepo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@ConditionalOnProperty(value = "schedule.actionEvents", havingValue = "true", matchIfMissing = true)
public class DeltaFileCacheServiceImpl extends DeltaFileCacheService {
    private final Map<String, DeltaFile> deltaFileCache;
    final DeltaFileRepo deltaFileRepo;
    final DeltaFiPropertiesService deltaFiPropertiesService;
    final DidMutexService didMutexService;
    final IdentityService identityService;
    final Clock clock;

    public DeltaFileCacheServiceImpl(DeltaFileRepo deltaFileRepo, DeltaFiPropertiesService deltaFiPropertiesService, DidMutexService didMutexService, IdentityService identityService, Clock clock) {
        this.deltaFileCache = new ConcurrentHashMap<>();
        this.deltaFileRepo = deltaFileRepo;
        this.deltaFiPropertiesService = deltaFiPropertiesService;
        this.didMutexService = didMutexService;
        this.identityService = identityService;
        this.clock = clock;
    }

    public void flush() {
        if (!deltaFileCache.isEmpty()) {
            log.info("Flushing {} files from the DeltaFileCache", deltaFileCache.size());
            removeOlderThan(0);
        }
    }

    public DeltaFile get(String did) {
        if (deltaFiPropertiesService.getDeltaFiProperties().getDeltaFileCache().isEnabled()) {
            DeltaFile deltaFile = deltaFileCache.computeIfAbsent(did, this::getFromRepo);
            if (deltaFile.getCacheTime() == null) {
                deltaFile.setCacheTime(OffsetDateTime.now(clock));
            }
            return deltaFile;
        } else {
            return getFromRepo(did);
        }
    }

    public boolean isCached(String did) {
        return deltaFileCache.containsKey(did);
    }

    private DeltaFile getFromRepo(String did) {
        return deltaFileRepo.findById(did.toLowerCase()).orElse(null);
    }

    public void remove(String did) {
        deltaFileCache.remove(did);
    }

    public void removeOlderThan(int seconds) {
        OffsetDateTime threshold = OffsetDateTime.now(clock).minusSeconds(seconds);
        List<DeltaFile> filesToRemove = deltaFileCache.values().stream()
                .filter(d -> d.getCacheTime().isBefore(threshold))
                .toList();

        for (DeltaFile d : filesToRemove) {
            synchronized (didMutexService.getMutex(d.getDid())) {
                try {
                    deltaFileRepo.save(d);
                    deltaFileCache.remove(d.getDid());
                } catch (Exception ignored) {
                }
            }
        }
    }

    public void save(DeltaFile deltaFile) {
        if (!deltaFiPropertiesService.getDeltaFiProperties().getDeltaFileCache().isEnabled() ||
                deltaFile.inactiveStage()) {
            try {
                deltaFileRepo.save(deltaFile);
            } finally {
                // prevent infinite loop if there are optimistic locking exceptions
                // force pulling a fresh copy from mongo on next get
                deltaFileCache.remove(deltaFile.getDid());
            }
        } else if (!deltaFileCache.containsKey(deltaFile.getDid())) {
            deltaFile.setCacheTime(OffsetDateTime.now(clock));
            deltaFileCache.put(deltaFile.getDid(), deltaFile);

            if (deltaFile.getVersion() == 0) {
                deltaFileRepo.save(deltaFile);
            }
        } else if(deltaFile.getCacheTime().isBefore(OffsetDateTime.now(clock).minusSeconds(deltaFiPropertiesService.getDeltaFiProperties().getDeltaFileCache().getSyncSeconds()))) {
            deltaFile.setCacheTime(OffsetDateTime.now(clock));
            deltaFileRepo.save(deltaFile);
        }
    }
}
