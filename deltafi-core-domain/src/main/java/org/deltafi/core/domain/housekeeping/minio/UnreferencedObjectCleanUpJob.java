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

import org.deltafi.core.domain.configuration.DeltaFiProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@ConditionalOnProperty(value = "enableScheduling", havingValue = "true", matchIfMissing = true)
@Service
public class UnreferencedObjectCleanUpJob {
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    @Inject
    public UnreferencedObjectCleanUpJob(DeltaFiProperties deltaFiProperties,
            UnreferencedObjectCleanUp unreferencedObjectCleanUp) {
        executor.scheduleWithFixedDelay(unreferencedObjectCleanUp::removeUnreferencedObjects,
                deltaFiProperties.getHousekeeping().getMinio().getInitialDelaySeconds(),
                deltaFiProperties.getHousekeeping().getMinio().getDelaySeconds(), TimeUnit.SECONDS);
    }

    @PreDestroy
    public void preDestroy() {
        executor.shutdown();
    }
}
