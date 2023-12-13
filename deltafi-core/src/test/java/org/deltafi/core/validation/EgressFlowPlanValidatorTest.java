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
import org.deltafi.common.types.EgressActionConfiguration;
import org.deltafi.common.types.EgressFlowPlan;
import org.deltafi.core.exceptions.DeltafiConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class EgressFlowPlanValidatorTest {

    @InjectMocks
    EgressFlowPlanValidator egressFlowPlanValidator;

    @Test
    void duplicateActionNameErrors() {
        EgressActionConfiguration egress = new EgressActionConfiguration("action", "org.deltafi.egress.Action");

        EgressFlowPlan egressFlow = new EgressFlowPlan("egressFlow", null, egress);

        Assertions.assertThatThrownBy(() -> egressFlowPlanValidator.validate(egressFlow))
                .isInstanceOf(DeltafiConfigurationException.class)
                .hasMessage("Config named: action had the following error: The action name: action is duplicated for the following action types: org.deltafi.egress.Action");
    }
}
