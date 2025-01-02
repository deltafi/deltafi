/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.exceptions;

import lombok.Getter;
import org.deltafi.common.types.FlowType;
import org.deltafi.core.generated.types.FlowState;

@Getter
public class MissingFlowException extends RuntimeException {
    private static final String MISSING_FLOW_CONTEXT = "The %s named %s is not %s";
    private static final String MISSING_FLOW_CAUSE = "The %s is no longer installed";
    private static final String NOT_RUNNING_CAUSE = "The %s is not running";

    private final String missingCause;

    public MissingFlowException(String flowName, FlowType flowType) {
        super(MISSING_FLOW_CONTEXT.formatted(flowType.getDisplayName(), flowName, "installed"));
        this.missingCause = MISSING_FLOW_CAUSE.formatted(flowType.getDisplayName());
    }

    public MissingFlowException(String flowName, FlowType flowType, FlowState flowState) {
        super(MISSING_FLOW_CONTEXT.formatted(flowType.getDisplayName(), flowName, "running (flow state is " + flowState + ")"));
        this.missingCause = NOT_RUNNING_CAUSE.formatted(flowType.getDisplayName());
    }
}