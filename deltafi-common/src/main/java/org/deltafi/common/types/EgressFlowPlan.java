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
package org.deltafi.common.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
public class EgressFlowPlan extends FlowPlan implements Subscriber {
    private final ActionConfiguration egressAction;
    @JsonProperty(required = true)
    private Set<Rule> subscribe;

    public EgressFlowPlan(String name, String description, ActionConfiguration egressAction) {
        super(name, FlowType.EGRESS, description);
        this.egressAction = egressAction;
    }

    @JsonCreator
    @SuppressWarnings("unused")
    public EgressFlowPlan(@JsonProperty(value = "name", required = true) String name,
            @JsonProperty(value = "type") FlowType type,
            @JsonProperty(value = "description", required = true) String description,
            @JsonProperty(value = "egressAction", required = true) ActionConfiguration egressAction) {
        this(name, description, egressAction);
    }

    @Override
    public List<ActionConfiguration> allActionConfigurations() {
        List<ActionConfiguration> actionConfigurations = new ArrayList<>();
        if (egressAction != null) {
            actionConfigurations.add(egressAction);
        }
        return actionConfigurations;
    }

    @Override
    public Set<Rule> subscribeRules() {
        return subscribe;
    }

    @Override
    public FlowType flowType() {
        return FlowType.EGRESS;
    }
}
