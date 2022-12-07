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
package org.deltafi.core.schedulers.trigger;

import org.deltafi.core.configuration.DeltaFiProperties;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;

import java.time.Duration;
import java.util.Date;
import java.util.function.Function;

public class ConfigurableFixedDelayTrigger implements Trigger {

    private final DeltaFiProperties deltaFiProperties;
    private final long initialDelay;
    private final Function<DeltaFiProperties, Duration> durationFunction;

    public ConfigurableFixedDelayTrigger(DeltaFiProperties deltaFiProperties, Function<DeltaFiProperties, Duration> durationFunction, long initialDelay) {
        this.deltaFiProperties = deltaFiProperties;
        this.initialDelay = initialDelay;
        this.durationFunction = durationFunction;
    }

    @Override
    public Date nextExecutionTime(TriggerContext triggerContext) {
        Date lastExecution = triggerContext.lastScheduledExecutionTime();
        Date lastCompletion = triggerContext.lastCompletionTime();
        if (lastExecution == null || lastCompletion == null) {
            return new Date(triggerContext.getClock().millis() + this.initialDelay);
        }

        return new Date(lastCompletion.getTime() + durationFunction.apply(deltaFiProperties).toMillis());
    }
}