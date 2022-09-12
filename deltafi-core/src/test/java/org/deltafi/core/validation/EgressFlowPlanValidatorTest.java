/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.core.configuration.EgressActionConfiguration;
import org.deltafi.core.configuration.FormatActionConfiguration;
import org.deltafi.core.configuration.ValidateActionConfiguration;
import org.deltafi.core.exceptions.DeltafiConfigurationException;
import org.deltafi.core.types.EgressFlowPlan;
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
        EgressFlowPlan egressFlow = new EgressFlowPlan();
        egressFlow.setName("egressFlow");

        FormatActionConfiguration format = new FormatActionConfiguration();
        format.setName("action");
        format.setType("org.deltafi.format.Action");

        ValidateActionConfiguration validate1 = new ValidateActionConfiguration();
        validate1.setName("action");
        validate1.setType("org.deltafi.validate.Action1");

        ValidateActionConfiguration validate2 = new ValidateActionConfiguration();
        validate2.setName("validate");
        validate2.setType("org.deltafi.validate.Action2");

        ValidateActionConfiguration validate3 = new ValidateActionConfiguration();
        validate3.setName("validate");
        validate3.setType("org.deltafi.validate.Action3");

        EgressActionConfiguration egress = new EgressActionConfiguration();
        egress.setName("action");
        egress.setType("org.deltafi.egress.Action");

        egressFlow.setFormatAction(format);
        egressFlow.setValidateActions(List.of(validate1, validate2, validate3));
        egressFlow.setEgressAction(egress);

        Assertions.assertThatThrownBy(() -> egressFlowPlanValidator.validate(egressFlow))
                .isInstanceOf(DeltafiConfigurationException.class)
                .hasMessage("Config named: action had the following error: The action name: action is duplicated for the following action types: org.deltafi.format.Action, org.deltafi.egress.Action, org.deltafi.validate.Action1; Config named: validate had the following error: The action name: validate is duplicated for the following action types: org.deltafi.validate.Action2, org.deltafi.validate.Action3");
    }

}
