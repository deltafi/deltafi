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
package org.deltafi.core.domain.types;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.deltafi.core.domain.configuration.ActionConfiguration;
import org.deltafi.core.domain.configuration.LoadActionConfiguration;
import org.deltafi.core.domain.configuration.TransformActionConfiguration;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Data
@Document("ingressFlowPlan")
@EqualsAndHashCode(callSuper = true)
public class IngressFlowPlan extends FlowPlan {

    private String type;
    private List<TransformActionConfiguration> transformActions = new ArrayList<>();
    private LoadActionConfiguration loadAction;

    @Override
    public List<ActionConfiguration> allActionConfigurations() {
        List<ActionConfiguration> actionConfigurations = new ArrayList<>();
        actionConfigurations.add(loadAction);
        actionConfigurations.addAll(transformActions);
        return actionConfigurations;
    }
}
