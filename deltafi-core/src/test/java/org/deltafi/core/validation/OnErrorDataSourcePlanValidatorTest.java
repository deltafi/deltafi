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

import org.assertj.core.api.Assertions;
import org.deltafi.common.rules.RuleValidator;
import org.deltafi.common.types.*;
import org.deltafi.core.exceptions.DeltafiConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class OnErrorDataSourcePlanValidatorTest {

    @InjectMocks
    OnErrorDataSourcePlanValidator validator;

    @Mock
    @SuppressWarnings("unused")
    RuleValidator ruleValidator;

    private OnErrorDataSourcePlan createPlan(String errorRegex, List<ErrorSourceFilter> sourceFilters, List<String> includeMetadataRegex, String sourceMetaPrefix,
                                           List<String> includeAnnotationsRegex, Map<String, String> metadata, AnnotationConfig annotations) {
        return new OnErrorDataSourcePlan(
                "test-plan",
                FlowType.ON_ERROR_DATA_SOURCE,
                "Test description",
                metadata != null ? metadata : Map.of(),
                annotations != null ? annotations : new AnnotationConfig(Map.of(), null, null),
                "test-topic",
                errorRegex,
                sourceFilters,
                null, // metadataFilters
                null, // annotationFilters
                includeMetadataRegex,
                sourceMetaPrefix,
                includeAnnotationsRegex
        );
    }

    @Test
    void validPlanWithErrorRegex() {
        OnErrorDataSourcePlan plan = createPlan(".*error.*", null, null, null, null, null, null);
        
        Assertions.assertThatCode(() -> validator.validate(plan))
                .doesNotThrowAnyException();
    }

    @Test
    void validPlanWithSourceActions() {
        ErrorSourceFilter filter = new ErrorSourceFilter(null, null, "action1", null);
        OnErrorDataSourcePlan plan = createPlan(null, List.of(filter), null, null, null, null, null);
        
        Assertions.assertThatCode(() -> validator.validate(plan))
                .doesNotThrowAnyException();
    }

    @Test
    void allowCatchAllFilters() {
        OnErrorDataSourcePlan plan = createPlan(null, null, null, null, null, null, null);

        Assertions.assertThatCode(() -> validator.validate(plan))
                .doesNotThrowAnyException();
    }

    @Test
    void invalidErrorMessageRegex() {
        OnErrorDataSourcePlan plan = createPlan("[invalid regex", null, null, null, null, null, null);

        Assertions.assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(DeltafiConfigurationException.class)
                .hasMessageContaining("Invalid error message regex");
    }

    @Test
    void invalidIncludeSourceMetadataRegex() {
        OnErrorDataSourcePlan plan = createPlan(".*valid.*", null, List.of("[invalid regex"), "prefix.",  null, null, null);

        Assertions.assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(DeltafiConfigurationException.class)
                .hasMessageContaining("Invalid metadata regex");
    }

    @Test
    void invalidIncludeSourceAnnotationsRegex() {
        OnErrorDataSourcePlan plan = createPlan(".*valid.*", null, null, "",  List.of("[invalid regex"), null, null);

        Assertions.assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(DeltafiConfigurationException.class)
                .hasMessageContaining("Invalid annotations regex");
    }

    @Test
    void multipleValidationErrors() {
        Map<String, String> metadata = Map.of("key", ""); // blank value
        AnnotationConfig annotationConfig = new AnnotationConfig(Map.of("", "value"), null, null); // blank key
        OnErrorDataSourcePlan plan = createPlan(null, null, null, null,   null,  metadata, annotationConfig);

        Assertions.assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(DeltafiConfigurationException.class)
                .hasMessageContaining("Blank metadata values are not allowed")
                .hasMessageContaining("Blank annotation keys are not allowed");
    }
}