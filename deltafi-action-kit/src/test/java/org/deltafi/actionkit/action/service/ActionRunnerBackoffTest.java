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
package org.deltafi.actionkit.action.service;

import org.deltafi.actionkit.properties.ActionsProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActionRunnerBackoffTest {

    private ActionRunner runnerWith(long pollingPeriodMs, long maxBackoffMs) {
        ActionsProperties props = new ActionsProperties();
        props.setActionPollingPeriodMs(pollingPeriodMs);
        props.setActionPollingMaxBackoffMs(maxBackoffMs);
        return new ActionRunner(null, props, null, null, null, List.of(), "app", null);
    }

    @Test
    void escalatesByDoublingWhenNoProgress() {
        ActionRunner runner = runnerWith(100L, 5_000L);
        assertThat(runner.nextBackoffMs(100L, false)).isEqualTo(200L);
    }

    @Test
    void capsAtMaxBackoff() {
        ActionRunner runner = runnerWith(100L, 5_000L);
        assertThat(runner.nextBackoffMs(3_200L, false)).isEqualTo(5_000L);
        assertThat(runner.nextBackoffMs(5_000L, false)).isEqualTo(5_000L);
    }

    @Test
    void resetsToBaseWhenProgressMade() {
        ActionRunner runner = runnerWith(100L, 5_000L);
        assertThat(runner.nextBackoffMs(5_000L, true)).isEqualTo(100L);
    }

    @Test
    void neverEscalatesBelowBase() {
        ActionRunner runner = runnerWith(100L, 5_000L);
        assertThat(runner.nextBackoffMs(0L, false)).isEqualTo(200L);
        assertThat(runner.nextBackoffMs(50L, false)).isEqualTo(200L);
    }

    @Test
    void jitterStaysWithinHalfToFullOfDelay() {
        ActionRunner runner = runnerWith(100L, 5_000L);
        for (long delay : new long[]{2L, 100L, 200L, 5_000L}) {
            for (int i = 0; i < 1_000; i++) {
                long jittered = runner.jitteredDelayMs(delay);
                assertThat(jittered).isBetween(delay - delay / 2, delay);
            }
        }
    }

    @Test
    void jitterOfZeroIsZero() {
        ActionRunner runner = runnerWith(100L, 5_000L);
        assertThat(runner.jitteredDelayMs(0L)).isZero();
    }
}
