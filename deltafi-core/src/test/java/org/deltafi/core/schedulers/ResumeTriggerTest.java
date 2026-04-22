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
package org.deltafi.core.schedulers;

import org.deltafi.core.MockDeltaFiPropertiesService;
import org.deltafi.core.schedulers.trigger.ResumeTrigger;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TriggerContext;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeTriggerTest {
    @Test
    void nextExecution_usesBacklogDelayOnceWhenSignaled() {
        MockDeltaFiPropertiesService propertiesService = new MockDeltaFiPropertiesService();
        propertiesService.getDeltaFiProperties().setAutoResumeCheckFrequency(Duration.ofMinutes(1));
        ResumeTrigger trigger = new ResumeTrigger(propertiesService);

        Instant now = Instant.parse("2026-04-21T13:00:00Z");
        TestTriggerContext context = new TestTriggerContext(now.minusSeconds(30), now, Clock.fixed(now, ZoneOffset.UTC));

        trigger.signalBacklog();
        assertThat(trigger.nextExecution(context)).isEqualTo(now.plusSeconds(5));
        assertThat(trigger.nextExecution(context)).isEqualTo(now.plusSeconds(60));
    }

    private record TestTriggerContext(Instant lastScheduledExecution, Instant lastCompletion, Clock clock) implements TriggerContext {
        @Override
        public Instant lastActualExecution() {
            return lastScheduledExecution;
        }

        @Override
        public Clock getClock() {
            return clock;
        }
    }
}
