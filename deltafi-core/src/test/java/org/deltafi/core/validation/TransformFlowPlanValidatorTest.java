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

import org.assertj.core.api.Assertions;
import org.deltafi.common.types.*;
import org.deltafi.core.exceptions.DeltafiConfigurationException;
import org.deltafi.core.repo.IngressFlowPlanRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class TransformFlowPlanValidatorTest {

    @Mock
    @SuppressWarnings("unused")
    IngressFlowPlanRepo ingressFlowPlanRepo;

    @InjectMocks
    TransformFlowPlanValidator transformFlowPlanValidator;

    @Test
    void duplicateActionNameErrors() {
        EgressActionConfiguration egress = new EgressActionConfiguration("action", "org.deltafi.load.Action");

        TransformActionConfiguration transform1 = new TransformActionConfiguration("action", "org.deltafi.transform.Action1");
        TransformActionConfiguration transform2 = new TransformActionConfiguration("transform", "org.deltafi.transform.Action2");
        TransformActionConfiguration transform3 = new TransformActionConfiguration("transform",  "org.deltafi.transform.Action3");

        TransformFlowPlan transformFlow = new TransformFlowPlan("flow", null);
        transformFlow.setEgressAction(egress);
        transformFlow.setTransformActions(List.of(transform1, transform2, transform3));

        Assertions.assertThatThrownBy(() -> transformFlowPlanValidator.validate(transformFlow))
                .isInstanceOf(DeltafiConfigurationException.class)
                .hasMessage("Config named: transform had the following error: The action name: transform is duplicated for the following action types: org.deltafi.transform.Action2, org.deltafi.transform.Action3; Config named: action had the following error: The action name: action is duplicated for the following action types: org.deltafi.transform.Action1, org.deltafi.load.Action");
    }
}
