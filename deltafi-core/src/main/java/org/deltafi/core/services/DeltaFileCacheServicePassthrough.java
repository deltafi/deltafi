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

import org.deltafi.core.types.DeltaFile;
import org.deltafi.core.repo.DeltaFileRepo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@ConditionalOnProperty(value = "schedule.actionEvents", havingValue = "false")
public class DeltaFileCacheServicePassthrough extends DeltaFileCacheService {
    public DeltaFileCacheServicePassthrough(DeltaFileRepo deltaFileRepo) {
        super(deltaFileRepo);
    }

    @Override
    public void flush() {}

    @Override
    public DeltaFile get(UUID did) {
        return getFromRepo(did);
    }

    @Override
    public boolean isCached(UUID did) {
        return false;
    }

    @Override
    public void remove(UUID did) {}

    @Override
    public void removeOlderThan(Duration duration) {}

    @Override
    public void save(DeltaFile deltaFile) {
        updateRepo(deltaFile);
    }
}
