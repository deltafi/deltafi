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
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document
@Getter
@Setter
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
public class EgressFlowPlan extends FlowPlan {
    private List<String> includeIngressFlows;
    private List<String> excludeIngressFlows;

    private final FormatActionConfiguration formatAction;
    private List<ValidateActionConfiguration> validateActions;
    private final EgressActionConfiguration egressAction;

    public EgressFlowPlan(String name, String description, FormatActionConfiguration formatAction,
                          EgressActionConfiguration egressAction) {
        super(name, FlowType.EGRESS, description);
        this.formatAction = formatAction;
        this.egressAction = egressAction;
    }

    public EgressFlowPlan(String name, String description, FormatActionConfiguration formatAction, List<ValidateActionConfiguration> validateActions,
                          EgressActionConfiguration egressAction) {
        super(name, FlowType.EGRESS, description);
        this.formatAction = formatAction;
        this.validateActions = validateActions;
        this.egressAction = egressAction;
    }

    @PersistenceCreator
    @JsonCreator
    @SuppressWarnings("unused")
    public EgressFlowPlan(@JsonProperty(value = "name", required = true) String name,
            @JsonProperty(value = "type") FlowType type,
            @JsonProperty(value = "description", required = true) String description,
            @JsonProperty(value = "formatAction", required = true) FormatActionConfiguration formatAction,
            @JsonProperty(value = "validateActions") List<ValidateActionConfiguration> validateActions,
            @JsonProperty(value = "egressAction", required = true) EgressActionConfiguration egressAction) {
        this(name, description, formatAction, validateActions, egressAction);
    }

    @Override
    public List<ActionConfiguration> allActionConfigurations() {
        List<ActionConfiguration> actionConfigurations = new ArrayList<>();
        if (formatAction != null) {
            actionConfigurations.add(formatAction);
        }
        if (validateActions != null) {
            actionConfigurations.addAll(validateActions);
        }
        if (egressAction != null) {
            actionConfigurations.add(egressAction);
        }
        return actionConfigurations;
    }
}
