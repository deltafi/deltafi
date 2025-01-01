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
package org.deltafi.core.validation;

import com.networknt.schema.utils.StringUtils;
import org.deltafi.common.rules.RuleValidator;
import org.deltafi.common.types.RestDataSourcePlan;
import org.deltafi.core.generated.types.FlowConfigError;
import org.deltafi.core.generated.types.FlowErrorType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RestDataSourcePlanValidator extends FlowPlanValidator<RestDataSourcePlan> {

    public RestDataSourcePlanValidator(RuleValidator ruleValidator) {
        super(ruleValidator);
    }

    /**
     * Flow plan type specific validation checks
     * @return list of errors
     */
    @Override
    public List<FlowConfigError> flowPlanSpecificValidation(RestDataSourcePlan flowPlan) {
        List<FlowConfigError> errors = new ArrayList<>();
        if (StringUtils.isBlank(flowPlan.getTopic())) {
            FlowConfigError.newBuilder().errorType(FlowErrorType.INVALID_CONFIG)
                    .configName(flowPlan.getName())
                    .message("Cannot add rest data source plan, topic is missing").build();
        }
        return errors;
    }
}
