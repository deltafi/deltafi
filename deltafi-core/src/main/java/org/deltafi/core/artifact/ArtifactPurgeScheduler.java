/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.artifact;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the artifact purge policy across all plugins and shared artifacts
 * on a configurable interval (default: hourly).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(value = "schedule.maintenance", havingValue = "true", matchIfMissing = true)
public class ArtifactPurgeScheduler {

    private final ArtifactRegistryService registryService;

    @Scheduled(fixedDelayString = "${artifact-registry.purge.interval-ms:3600000}")
    public void runPurge() {
        log.debug("Running scheduled artifact purge");
        try {
            registryService.runScheduledPurge();
        } catch (Exception e) {
            log.error("Artifact purge scheduler encountered an error", e);
        }
    }
}
