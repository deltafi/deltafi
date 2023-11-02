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
package org.deltafi.core.snapshot.types;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.deltafi.core.types.TimedIngressFlow;

@Data
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class TimedIngressFlowSnapshot extends FlowSnapshot {

    private String targetFlow;
    private String cronSchedule = "*/30 * * * * *";

    public TimedIngressFlowSnapshot(String name) {
        super(name);
    }

    public TimedIngressFlowSnapshot(TimedIngressFlow timedIngressFlow) {
        this(timedIngressFlow.getName());
        setRunning(timedIngressFlow.isRunning());
        setTestMode(timedIngressFlow.isTestMode());
        setTargetFlow(timedIngressFlow.getTargetFlow());
        setCronSchedule(timedIngressFlow.getCronSchedule());
    }
}
