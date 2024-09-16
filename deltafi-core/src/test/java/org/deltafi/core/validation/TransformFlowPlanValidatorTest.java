/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.core.repo.FlowPlanRepo;
import org.deltafi.core.types.TransformFlowPlanEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.deltafi.core.datafetchers.FlowPlanDatafetcherTestHelper.PLUGIN_COORDINATES;

@ExtendWith(MockitoExtension.class)
class TransformFlowPlanValidatorTest {

    @Mock
    @SuppressWarnings("unused")
    FlowPlanRepo flowPlanRepo;

    @InjectMocks
    TransformFlowPlanValidator transformFlowPlanValidator;

    @Mock
    @SuppressWarnings("unused")
    RuleValidator ruleValidator;

    @Test
    void duplicateActionNameErrors() {
        ActionConfiguration transform1 = new ActionConfiguration("action", ActionType.TRANSFORM, "org.deltafi.transform.Action1");
        ActionConfiguration transform2 = new ActionConfiguration("transform", ActionType.TRANSFORM, "org.deltafi.transform.Action2");
        ActionConfiguration transform3 = new ActionConfiguration("transform",  ActionType.TRANSFORM, "org.deltafi.transform.Action3");

        TransformFlowPlanEntity transformFlow = new TransformFlowPlanEntity("flow", null, PLUGIN_COORDINATES);
        transformFlow.setTransformActions(List.of(transform1, transform2, transform3));

        Assertions.assertThatThrownBy(() -> transformFlowPlanValidator.validate(transformFlow))
                .isInstanceOf(DeltafiConfigurationException.class)
                .hasMessage("Config named: transform had the following error: The action name: transform is duplicated for the following action types: org.deltafi.transform.Action2, org.deltafi.transform.Action3");
    }
}
