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

import org.deltafi.common.types.DeltaFile;
import org.deltafi.core.repo.DeltaFileRepo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(value = "schedule.actionEvents", havingValue = "true", matchIfMissing = true)
public class DeltaFileCacheServiceImpl extends DeltaFileCacheService {
    private final Map<String, DeltaFile> deltaFileCache;
    final DeltaFileRepo deltaFileRepo;
    final DeltaFiPropertiesService deltaFiPropertiesService;

    public DeltaFileCacheServiceImpl(DeltaFileRepo deltaFileRepo, DeltaFiPropertiesService deltaFiPropertiesService) {
        this.deltaFileCache = new ConcurrentHashMap<>();
        this.deltaFileRepo = deltaFileRepo;
        this.deltaFiPropertiesService = deltaFiPropertiesService;
    }

    public void clearCache() {
        deltaFileCache.clear();
    }

    public DeltaFile get(String did) {
        if (deltaFiPropertiesService.getDeltaFiProperties().getDeltaFileCache().isEnabled()) {
            return deltaFileCache.computeIfAbsent(did, this::getFromRepo);
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

    public void removeOlderThan(int seconds) {
        deltaFileCache.values().stream()
                .filter(d -> d.getModified().isBefore(OffsetDateTime.now().minusSeconds(seconds)))
                .forEach(d -> {
                    // if the cache is stale we may receive optimistic locking exceptions, or comms with mongo may be down
                    // process files one at a time and ignore errors
                    try {
                        deltaFileRepo.save(d);
                    } catch (Exception ignored) {
                    } finally {
                        deltaFileCache.remove(d.getDid());
                    }
                });
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
            deltaFileCache.putIfAbsent(deltaFile.getDid(), deltaFile);
            if (deltaFile.getVersion() == 0) {
                deltaFileRepo.save(deltaFile);
            }
        }
    }
}
