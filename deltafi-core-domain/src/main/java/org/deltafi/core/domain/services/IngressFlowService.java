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
package org.deltafi.core.domain.services;

import org.deltafi.core.domain.converters.IngressFlowPlanConverter;
import org.deltafi.core.domain.repo.IngressFlowRepo;
import org.deltafi.core.domain.types.IngressFlow;
import org.deltafi.core.domain.types.IngressFlowPlan;
import org.deltafi.core.domain.validation.IngressFlowValidator;
import org.springframework.stereotype.Service;

@Service
public class IngressFlowService extends FlowService<IngressFlowPlan, IngressFlow> {

    private static final IngressFlowPlanConverter INGRESS_FLOW_PLAN_CONVERTER = new IngressFlowPlanConverter();

    public IngressFlowService(IngressFlowRepo ingressFlowRepo, PluginVariableService pluginVariableService, IngressFlowValidator ingressFlowValidator) {
        super("ingress", ingressFlowRepo, pluginVariableService, INGRESS_FLOW_PLAN_CONVERTER, ingressFlowValidator);
    }

}