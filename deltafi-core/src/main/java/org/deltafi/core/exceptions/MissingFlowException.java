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

@Getter
public class MissingFlowException extends RuntimeException {
    private static final String MISSING_FLOW_CONTEXT = "The %s named %s is not %s";
    private static final String MISSING_FLOW_CAUSE = "The %s is no longer installed";
    private static final String NOT_RUNNING_CAUSE = "The %s is stopped";
    private static final String INVALID_CAUSE = "The %s is invalid";

    private final String missingCause;

    public MissingFlowException(FlowType flowType, String flowName, String context, String missingCause) {
        super(MISSING_FLOW_CONTEXT.formatted(flowType.getDisplayName(), flowName, context));
        this.missingCause = missingCause.formatted(flowType.getDisplayName());
    }

    public static MissingFlowException notFound(String flowName, FlowType flowType) {
        return new MissingFlowException(flowType, flowName, "installed", MISSING_FLOW_CAUSE);
    }

    public static MissingFlowException stopped(String flowName, FlowType flowType) {
        return new MissingFlowException(flowType, flowName, "running", NOT_RUNNING_CAUSE);
    }

    public static MissingFlowException invalid(String flowName, FlowType flowType) {
        return new MissingFlowException(flowType, flowName, "valid", INVALID_CAUSE);
    }
}