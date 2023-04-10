/**
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
package org.deltafi.core.services;

import org.deltafi.core.converters.EnrichFlowPlanConverter;
import org.deltafi.core.repo.EnrichFlowRepo;
import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.types.EnrichFlow;
import org.deltafi.common.types.EnrichFlowPlan;
import org.deltafi.core.validation.EnrichFlowValidator;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class EnrichFlowService extends FlowService<EnrichFlowPlan, EnrichFlow> {

    private static final EnrichFlowPlanConverter ENRICH_FLOW_PLAN_CONVERTER = new EnrichFlowPlanConverter();

    public EnrichFlowService(EnrichFlowRepo flowRepo, PluginVariableService pluginVariableService, EnrichFlowValidator enrichFlowValidator) {
        super("enrich", flowRepo, pluginVariableService, ENRICH_FLOW_PLAN_CONVERTER, enrichFlowValidator);
    }

    @Override
    public void updateSnapshot(SystemSnapshot systemSnapshot) {
        refreshCache();
        systemSnapshot.setRunningEnrichFlows(getRunningFlowNames());
    }


    @Override
    List<String> getRunningFromSnapshot(SystemSnapshot systemSnapshot) {
        return systemSnapshot.getRunningEnrichFlows();
    }

    @Override
    List<String> getTestModeFromSnapshot(SystemSnapshot systemSnapshot) {
        return Collections.emptyList();
    }
}
