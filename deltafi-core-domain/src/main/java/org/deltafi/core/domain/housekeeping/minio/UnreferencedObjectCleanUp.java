/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.domain.housekeeping.minio;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.core.domain.configuration.DeltaFiProperties;
import org.deltafi.core.domain.repo.DeltaFileRepo;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

@Service
@Slf4j
public class UnreferencedObjectCleanUp {
    private final ContentStorageService contentStorageService;
    private final DeltaFileRepo deltaFileRepo;
    private final Clock clock;
    private final int objectMinimumAgeForRemovalSeconds;

    @Inject
    @SuppressWarnings("CdiInjectionPointsInspection")
    public UnreferencedObjectCleanUp(ContentStorageService contentStorageService, DeltaFileRepo deltaFileRepo,
            Clock clock, DeltaFiProperties deltaFiProperties) {
        this.contentStorageService = contentStorageService;
        this.deltaFileRepo = deltaFileRepo;
        this.clock = clock;
        this.objectMinimumAgeForRemovalSeconds = deltaFiProperties.getHousekeeping().getMinio().getObjectMinimumAgeForRemovalSeconds();
    }

    public void removeUnreferencedObjects() {
        log.trace("Removing unreferenced Minio objects");

        ZonedDateTime lastModifiedBefore = ZonedDateTime.now(clock).minusSeconds(objectMinimumAgeForRemovalSeconds);

        Set<String> didsInObjectStorage = new HashSet<>(contentStorageService.findDidsLastModifiedBefore(lastModifiedBefore));
        didsInObjectStorage.removeAll(deltaFileRepo.readDidsWithContent());
        if (!didsInObjectStorage.isEmpty()) {
            contentStorageService.deleteAll(new ArrayList<>(didsInObjectStorage));
        }
    }
}
