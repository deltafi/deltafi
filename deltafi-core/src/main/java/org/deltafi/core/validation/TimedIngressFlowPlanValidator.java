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
package org.deltafi.core.validation;

import org.deltafi.common.rules.RuleValidator;
import org.deltafi.common.types.TimedIngressFlowPlan;
import org.deltafi.core.generated.types.FlowConfigError;
import org.deltafi.core.generated.types.FlowErrorType;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TimedIngressFlowPlanValidator extends FlowPlanValidator<TimedIngressFlowPlan> {

    public TimedIngressFlowPlanValidator(RuleValidator ruleValidator) {
        super(ruleValidator);
    }

    /**
     * Flow plan type specific validation checks
     * @return list of errors
     */
    @Override
    public List<FlowConfigError> flowPlanSpecificValidation(TimedIngressFlowPlan flowPlan) {
        List<FlowConfigError> errors = new ArrayList<>();
        if (flowPlan.getTimedIngressAction() == null) {
            errors.add(FlowConfigError.newBuilder().errorType(FlowErrorType.INVALID_CONFIG)
                    .configName(flowPlan.getName())
                    .message("Cannot add timed ingress flow plan, timed ingress action is missing").build());
        }
        if (flowPlan.getTargetFlow() == null && flowPlan.getPublishRules() == null) {
            errors.add(FlowConfigError.newBuilder().errorType(FlowErrorType.INVALID_CONFIG)
                    .configName(flowPlan.getName())
                    .message("Cannot add timed ingress flow plan, both publish rules and target flow are missing, one must be set").build());
        }
        if (flowPlan.getTargetFlow() != null && flowPlan.getPublishRules() != null) {
            errors.add(FlowConfigError.newBuilder().errorType(FlowErrorType.INVALID_CONFIG)
                    .configName(flowPlan.getName())
                    .message("Cannot add timed ingress flow plan, both publish rules and target flow are set, only one should be set").build());
        }
        if (flowPlan.getCronSchedule() == null) {
            errors.add(FlowConfigError.newBuilder().errorType(FlowErrorType.INVALID_CONFIG)
                    .configName(flowPlan.getName())
                    .message("Cannot add timed ingress flow plan, cron schedule is missing").build());
        } else {
            try {
                CronExpression.parse(flowPlan.getCronSchedule());
            } catch (IllegalArgumentException e) {
                errors.add(FlowConfigError.newBuilder().errorType(FlowErrorType.INVALID_CONFIG)
                        .configName(flowPlan.getName())
                        .message("Cannot add timed ingress flow plan, cron schedule is invalid").build());
            }
        }
        return errors;
    }
}
