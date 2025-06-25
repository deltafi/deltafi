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

import org.deltafi.common.rules.RuleValidator;
import org.deltafi.common.types.OnErrorDataSourcePlan;
import org.deltafi.core.generated.types.FlowConfigError;
import org.deltafi.core.generated.types.FlowErrorType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Component
public class OnErrorDataSourcePlanValidator extends DataSourcePlanValidator<OnErrorDataSourcePlan> {

    public OnErrorDataSourcePlanValidator(RuleValidator ruleValidator) {
        super(ruleValidator);
    }

    @Override
    public List<FlowConfigError> dataSourceTypeSpecificValidation(OnErrorDataSourcePlan onErrorDataSourcePlan) {
        List<FlowConfigError> errors = new ArrayList<>();

        // Validate error message regex if provided
        if (onErrorDataSourcePlan.getErrorMessageRegex() != null && !onErrorDataSourcePlan.getErrorMessageRegex().isBlank()) {
            try {
                Pattern.compile(onErrorDataSourcePlan.getErrorMessageRegex());
            } catch (PatternSyntaxException e) {
                errors.add(FlowConfigError.newBuilder()
                        .configName(onErrorDataSourcePlan.getName())
                        .errorType(FlowErrorType.INVALID_CONFIG)
                        .message("Invalid error message regex: " + e.getMessage())
                        .build());
            }
        }

        // Validate include metadata regex patterns if provided
        if (onErrorDataSourcePlan.getIncludeSourceMetadataRegex() != null) {
            for (String regex : onErrorDataSourcePlan.getIncludeSourceMetadataRegex()) {
                if (regex != null && !regex.isBlank()) {
                    try {
                        Pattern.compile(regex);
                    } catch (PatternSyntaxException e) {
                        errors.add(FlowConfigError.newBuilder()
                                .configName(onErrorDataSourcePlan.getName())
                                .errorType(FlowErrorType.INVALID_CONFIG)
                                .message("Invalid metadata regex '" + regex + "': " + e.getMessage())
                                .build());
                    }
                }
            }
        }

        // Validate include annotations regex patterns if provided
        if (onErrorDataSourcePlan.getIncludeSourceAnnotationsRegex() != null) {
            for (String regex : onErrorDataSourcePlan.getIncludeSourceAnnotationsRegex()) {
                if (regex != null && !regex.isBlank()) {
                    try {
                        Pattern.compile(regex);
                    } catch (PatternSyntaxException e) {
                        errors.add(FlowConfigError.newBuilder()
                                .configName(onErrorDataSourcePlan.getName())
                                .errorType(FlowErrorType.INVALID_CONFIG)
                                .message("Invalid annotations regex '" + regex + "': " + e.getMessage())
                                .build());
                    }
                }
            }
        }

        return errors;
    }

}