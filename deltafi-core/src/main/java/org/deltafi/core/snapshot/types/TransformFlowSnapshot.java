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
import lombok.ToString;
import org.deltafi.common.types.FlowType;
import org.deltafi.core.types.TransformFlow;

import java.util.HashSet;
import java.util.Set;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class TransformFlowSnapshot extends FlowSnapshot implements HasExpectedAnnotations {

    private int maxErrors = -1;
    private Set<String> expectedAnnotations = new HashSet<>();


    public TransformFlowSnapshot() {}

    public TransformFlowSnapshot(String name) {
        super(name);
    }

    public TransformFlowSnapshot(TransformFlow transformFlow) {
        this(transformFlow.getName());
        setRunning(transformFlow.isRunning());
        setTestMode(transformFlow.isTestMode());
        setMaxErrors(transformFlow.getMaxErrors());
        setExpectedAnnotations(transformFlow.getExpectedAnnotations());
    }

    @Override
    public FlowType getFlowType() {
        return FlowType.TRANSFORM;
    }
}
