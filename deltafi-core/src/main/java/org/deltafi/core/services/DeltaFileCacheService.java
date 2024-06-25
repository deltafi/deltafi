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

import lombok.RequiredArgsConstructor;
import org.deltafi.core.types.DeltaFile;
import org.deltafi.core.repo.DeltaFileRepo;

import java.time.Duration;
import java.util.Collection;
import java.util.UUID;

@RequiredArgsConstructor
public abstract class DeltaFileCacheService {

    final DeltaFileRepo deltaFileRepo;

    public abstract void flush();

    public abstract DeltaFile get(UUID did);

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

    protected void updateRepo(DeltaFile deltaFile) {
        if (deltaFile == null) {
            return;
        }
        deltaFileRepo.save(deltaFile);
    }
}
