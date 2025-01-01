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
import org.deltafi.core.types.DeltaFile;
import org.deltafi.core.repo.DeltaFileRepo;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
public abstract class DeltaFileCacheService {

    final DeltaFileRepo deltaFileRepo;

    public abstract void flush();

    public abstract DeltaFile get(UUID did);

    public abstract List<DeltaFile> get(List<UUID> dids);

    public abstract boolean isCached(UUID did);

    public abstract void remove(UUID did);

    public abstract void removeOlderThan(Duration duration);

    public abstract void save(DeltaFile deltaFile);

    public void saveAll(Collection<DeltaFile> deltaFiles) {
        for (DeltaFile deltaFile : deltaFiles) {
            save(deltaFile);
        }
    }

    protected DeltaFile getFromRepo(UUID did) {
        return deltaFileRepo.findById(did).orElse(null);
    }

    protected List<DeltaFile> getFromRepo(List<UUID> dids) {
        return deltaFileRepo.findByIdsIn(dids);
    }

    protected void put(DeltaFile deltaFile) {}

    protected void updateRepo(DeltaFile deltaFile) {
        if (deltaFile == null) {
            return;
        }

        if (deltaFile.getVersion() == 0 && deltaFile.getCacheTime() == null) {
            deltaFileRepo.insertOne(deltaFile);
            put(deltaFile);
        } else {
            put(deltaFileRepo.saveAndFlush(deltaFile));
        }
    }
}
