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
import org.deltafi.core.types.DeltaFile;
import org.deltafi.core.repo.DeltaFileRepo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@ConditionalOnProperty(value = "schedule.actionEvents", havingValue = "true", matchIfMissing = true)
public class DeltaFileCacheServiceImpl extends DeltaFileCacheService {
    private final Map<UUID, DeltaFile> deltaFileCache;
    final DeltaFiPropertiesService deltaFiPropertiesService;
    final DidMutexService didMutexService;
    final IdentityService identityService;
    final Clock clock;

    public DeltaFileCacheServiceImpl(DeltaFileRepo deltaFileRepo, DeltaFiPropertiesService deltaFiPropertiesService, DidMutexService didMutexService, IdentityService identityService, Clock clock) {
        super(deltaFileRepo);
        this.deltaFileCache = new ConcurrentHashMap<>();
        this.deltaFiPropertiesService = deltaFiPropertiesService;
        this.didMutexService = didMutexService;
        this.identityService = identityService;
        this.clock = clock;
    }

    @Override
    public void flush() {
        if (!deltaFileCache.isEmpty()) {
            log.info("Flushing {} files from the DeltaFileCache", deltaFileCache.size());
            removeOlderThan(Duration.ZERO);
        }
    }

    @Override
    public DeltaFile get(UUID did) {
        if (deltaFiPropertiesService.getDeltaFiProperties().isCacheEnabled()) {
            DeltaFile deltaFile = deltaFileCache.computeIfAbsent(did, d -> getFromRepo(d, true));
            if (deltaFile.getCacheTime() == null) {
                deltaFile.setCacheTime(OffsetDateTime.now(clock));
            }
            return deltaFile;
        } else {
            return getFromRepo(did, false);
        }
    }

    @Override
    public boolean isCached(UUID did) {
        return deltaFileCache.containsKey(did);
    }

    @Override
    public void remove(UUID did) {
        deltaFileCache.remove(did);
    }

    @Override
    public void removeOlderThan(Duration duration) {
        OffsetDateTime threshold = OffsetDateTime.now(clock).minus(duration);
        List<DeltaFile> filesToRemove = deltaFileCache.values().stream()
                .filter(d -> d.getCacheTime().isBefore(threshold))
                .toList();

        for (DeltaFile d : filesToRemove) {
            synchronized (didMutexService.getMutex(d.getDid())) {
                try {
                    updateRepo(d, false);
                } catch (Exception ignored) {
                } finally {
                    remove(d.getDid());
                }
            }
        }
    }

    @Override
    public void save(DeltaFile deltaFile) {
        if (!deltaFiPropertiesService.getDeltaFiProperties().isCacheEnabled() ||
                deltaFile.inactiveStage() || deltaFile.getVersion() == 0) {
            try {
                updateRepo(deltaFile, false);
            } finally {
                // prevent infinite loop if there are exceptions
                // force pulling a fresh copy from mongo on next get
                deltaFileCache.remove(deltaFile.getDid());
            }
        } else if (!deltaFileCache.containsKey(deltaFile.getDid())) {
            deltaFile.setCacheTime(OffsetDateTime.now(clock));
            deltaFileCache.put(deltaFile.getDid(), deltaFile);
            updateRepo(deltaFile, true);
        } else if (deltaFile.getCacheTime().isBefore(OffsetDateTime.now(clock).minus(
                deltaFiPropertiesService.getDeltaFiProperties().getCacheSyncDuration()))) {
            deltaFile.setCacheTime(OffsetDateTime.now(clock));
            updateRepo(deltaFile, true);
        }
    }
}
