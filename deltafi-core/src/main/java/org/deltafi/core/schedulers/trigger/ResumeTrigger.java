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
package org.deltafi.core.schedulers.trigger;

import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.springframework.scheduling.TriggerContext;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Calculates the next execution time for auto-resume based on the configured
 * autoResumeCheckFrequency, while also supporting a short backlog-driven recheck
 * when {@link #signalBacklog()} indicates there is more work ready immediately.
 */
@Service
public class ResumeTrigger extends ConfigurableFixedDelayTrigger {
    public static final Duration BACKLOG_RECHECK_DELAY = Duration.ofSeconds(5);
    private final AtomicBoolean hasBacklog = new AtomicBoolean(false);

    public ResumeTrigger(DeltaFiPropertiesService deltaFiPropertiesService) {
        super(deltaFiPropertiesService, DeltaFiProperties::getAutoResumeCheckFrequency, 15_000L);
    }

    public void signalBacklog() {
        hasBacklog.set(true);
    }

    @Override
    public Instant nextExecution(TriggerContext triggerContext) {
        Instant lastCompletion = triggerContext.lastCompletion();
        if (lastCompletion != null && hasBacklog.getAndSet(false)) {
            return lastCompletion.plus(BACKLOG_RECHECK_DELAY);
        }

        return super.nextExecution(triggerContext);
    }
}
