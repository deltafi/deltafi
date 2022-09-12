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
import org.deltafi.core.configuration.EnrichActionConfiguration;
import org.deltafi.core.configuration.DomainActionConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Data
@EqualsAndHashCode(callSuper = true)
public class EnrichFlowPlan extends FlowPlan {
    List<DomainActionConfiguration> domainActions = new ArrayList<>();
    List<EnrichActionConfiguration> enrichActions = new ArrayList<>();

    public Set<String> enrichActionTypes() {
        return distinctTypes(enrichActions);
    }

    public Set<String> domainActionTypes() {
        return distinctTypes(domainActions);
    }

    @Override
    public List<ActionConfiguration> allActionConfigurations() {
        List<ActionConfiguration> actions = new ArrayList<>();
        if (null != domainActions) {
            actions.addAll(domainActions);
        }

        if (null != enrichActions) {
            actions.addAll(enrichActions);
        }

        return actions;
    }

    private Set<String> distinctTypes(List<? extends ActionConfiguration> actions) {
        return null != actions ? actions.stream().map(ActionConfiguration::getType).collect(Collectors.toSet()) : Set.of();
    }
}
