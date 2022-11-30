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
package org.deltafi.core.services;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.repo.DeltaFileRepo;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Duration;

@Service
@Slf4j
public class DeltaFileIndexService {

    private final DeltaFileRepo deltaFileRepo;
    private final DeltaFiProperties deltaFiProperties;

    public DeltaFileIndexService(DeltaFileRepo deltaFileRepo, DeltaFiProperties deltaFiProperties) {
        this.deltaFileRepo = deltaFileRepo;
        this.deltaFiProperties = deltaFiProperties;
    }

    @EventListener
    public void onEnvChange(final EnvironmentChangeEvent event) {
        if (event.getKeys().contains("deltafi.delete.ageOffDays")) {
            this.deltaFileRepo.setExpirationIndex(Duration.ofDays(deltaFiProperties.getDelete().getAgeOffDays()));
        }
    }

    @PostConstruct
    public void ensureAllIndices() {
        this.deltaFileRepo.ensureAllIndices(Duration.ofDays(deltaFiProperties.getDelete().getAgeOffDays()));
    }

}
