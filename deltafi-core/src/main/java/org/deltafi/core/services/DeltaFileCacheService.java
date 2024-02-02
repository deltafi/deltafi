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
import org.deltafi.common.types.DeltaFile;
import org.deltafi.core.repo.DeltaFileRepo;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

@RequiredArgsConstructor
public abstract class DeltaFileCacheService {

    final DeltaFileRepo deltaFileRepo;

    public abstract void flush();

    public abstract DeltaFile get(String did);

    public abstract boolean isCached(String did);

    public abstract void remove(String did);

    public abstract void removeOlderThan(int seconds);

    public abstract void save(DeltaFile deltaFile);

    public void saveAll(List<DeltaFile> deltaFiles) {
        for (DeltaFile deltaFile : deltaFiles) {
            save(deltaFile);
        }
    }

    protected DeltaFile getFromRepo(String did, boolean updateSnapshot) {
        DeltaFile deltaFile = deltaFileRepo.findById(did.toLowerCase()).orElse(null);
        if (deltaFile != null && updateSnapshot) {
            deltaFile.snapshot();
        }
        return deltaFile;
    }

    protected void updateRepo(DeltaFile deltaFile, boolean updateSnapshot) {
        if (deltaFile == null) {
            return;
        }
        if (deltaFile.getVersion() == 0) {
            deltaFileRepo.insert(deltaFile);
        } else if (deltaFile.getSnapshot() != null) {
            Update update = deltaFile.generateUpdate();
            if (update != null) {
                boolean updated = deltaFileRepo.update(deltaFile.getDid(), deltaFile.getVersion(), deltaFile.generateUpdate());
                if (updated) {
                    deltaFile.setVersion(deltaFile.getVersion() + 1);
                } else {
                    deltaFileRepo.save(deltaFile);
                }
                if (updateSnapshot) {
                    deltaFile.snapshot();
                }
            }
        } else {
            deltaFileRepo.save(deltaFile);
        }
    }
}
