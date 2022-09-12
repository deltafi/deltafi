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
package org.deltafi.core.types;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.deltafi.core.configuration.ActionConfiguration;
import org.deltafi.core.configuration.EgressActionConfiguration;
import org.deltafi.core.configuration.FormatActionConfiguration;
import org.deltafi.core.configuration.ValidateActionConfiguration;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Data
@Document("egressFlowPlan")
@EqualsAndHashCode(callSuper = true)
public class EgressFlowPlan extends FlowPlan {

    private EgressActionConfiguration egressAction;
    private FormatActionConfiguration formatAction;
    private List<ValidateActionConfiguration> validateActions = new ArrayList<>();

    private List<String> includeIngressFlows;
    private List<String> excludeIngressFlows;

    @Override
    public List<ActionConfiguration> allActionConfigurations() {
        List<ActionConfiguration> actionConfigurations = new ArrayList<>();
        actionConfigurations.add(formatAction);
        actionConfigurations.add(egressAction);
        actionConfigurations.addAll(validateActions);
        return actionConfigurations;
    }
}
