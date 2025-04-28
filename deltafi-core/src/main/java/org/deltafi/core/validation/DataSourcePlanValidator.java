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
import org.deltafi.common.types.AnnotationConfig;
import org.deltafi.common.types.DataSourcePlan;
import org.deltafi.core.generated.types.FlowConfigError;
import org.deltafi.core.generated.types.FlowErrorType;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public abstract class DataSourcePlanValidator<T extends DataSourcePlan> extends FlowPlanValidator<T> {

    public DataSourcePlanValidator(RuleValidator ruleValidator) {
        super(ruleValidator);
    }

    /**
     * Data source sub-type specific validation checks
     *
     * @return list of errors
     */
    public abstract List<FlowConfigError> dataSourceTypeSpecificValidation(T flowPlan);

    @Override
    public List<FlowConfigError> flowPlanSpecificValidation(T flowPlan) {
        List<FlowConfigError> errors = new ArrayList<>();
        errors.addAll(checkMetadata(flowPlan));
        errors.addAll(checkAnnotationConfig(flowPlan));
        errors.addAll(dataSourceTypeSpecificValidation(flowPlan));
        return errors;
    }

    private Collection<? extends FlowConfigError> checkAnnotationConfig(DataSourcePlan flowPlan) {
        AnnotationConfig annotationConfig = flowPlan.getAnnotationConfig();
        if (annotationConfig == null || annotationConfig.nothingConfigured()) {
            return Collections.emptyList();
        }
        List<FlowConfigError> errors = new ArrayList<>();

        if (annotationConfig.getAnnotations() != null) {
            if (annotationConfig.getAnnotations().keySet().stream().anyMatch(org.apache.commons.lang3.StringUtils::isBlank)) {
                errors.add(FlowConfigError.newBuilder().errorType(FlowErrorType.INVALID_CONFIG)
                        .configName(flowPlan.getName())
                        .message("Blank annotation keys are not allowed").build());
            }

            List<String> blankAnnotations = annotationConfig.getAnnotations().entrySet().stream()
                    .filter(annotationEntry -> org.apache.commons.lang3.StringUtils.isBlank(annotationEntry.getValue()))
                    .map(Map.Entry::getKey)
                    .toList();
            if (!blankAnnotations.isEmpty()) {
                errors.add(FlowConfigError.newBuilder().errorType(FlowErrorType.INVALID_CONFIG)
                        .configName(flowPlan.getName())
                        .message("Blank annotation values are not allowed").build());
            }
        }
        return errors;
    }

    private Collection<? extends FlowConfigError> checkMetadata(DataSourcePlan flowPlan) {
        Map<String, String> metadata = flowPlan.getMetadata();
        if (metadata == null || metadata.isEmpty()) {
            return Collections.emptyList();
        }
        List<FlowConfigError> errors = new ArrayList<>();

        if (metadata.keySet().stream().anyMatch(org.apache.commons.lang3.StringUtils::isBlank)) {
            errors.add(FlowConfigError.newBuilder().errorType(FlowErrorType.INVALID_CONFIG)
                    .configName(flowPlan.getName())
                    .message("Blank metadata keys are not allowed").build());
        }

        List<String> blankAnnotations = metadata.entrySet().stream()
                .filter(annotationEntry -> org.apache.commons.lang3.StringUtils.isBlank(annotationEntry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        if (!blankAnnotations.isEmpty()) {
            errors.add(FlowConfigError.newBuilder().errorType(FlowErrorType.INVALID_CONFIG)
                    .configName(flowPlan.getName())
                    .message("Blank metadata values are not allowed").build());
        }

        return errors;
    }

}
