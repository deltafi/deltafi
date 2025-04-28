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

import java.util.Map;

@ExtendWith(MockitoExtension.class)
class TimedDataSourcePlanValidatorTest {

    @InjectMocks
    TimedDataSourcePlanValidator validator;

    @Mock
    @SuppressWarnings("unused")
    RuleValidator ruleValidator;

    @Test
    void timedPlanErrors() {
        TimedDataSourcePlan flowPlan = new TimedDataSourcePlan(
                "timed",
                FlowType.TIMED_DATA_SOURCE,
                "desc",
                Map.of("missingValue", ""),
                new AnnotationConfig(
                        Map.of("", "missingKey"),
                        null,
                        null),
                "topic",
                new ActionConfiguration("actionName", ActionType.TIMED_INGRESS, "class"),
                "a b c d");

        Assertions.assertThatThrownBy(() -> validator.validate(flowPlan))
                .isInstanceOf(DeltafiConfigurationException.class)
                .hasMessage("Config named: timed had the following error: Blank metadata values are not allowed; Config named: timed had the following error: Blank annotation keys are not allowed; Config named: timed had the following error: Cannot add timed data source plan, cron schedule is invalid");
    }
}
