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
package org.deltafi.core.domain.snapshot;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.common.types.PropertySet;
import org.deltafi.core.domain.types.DeletePolicies;
import org.deltafi.core.domain.types.FlowAssignmentRule;
import org.deltafi.core.domain.types.PluginVariables;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

@Data
@Document
@NoArgsConstructor
public class SystemSnapshot {
    @Id
    private String id;
    private OffsetDateTime created = OffsetDateTime.now();
    private List<PluginVariables> pluginVariables;
    private DeletePolicies deletePolicies;
    private List<PropertySet> propertySets;
    private List<FlowAssignmentRule> flowAssignmentRules;
    private List<String> runningIngressFlows;
    private List<String> runningEnrichFlows;
    private List<String> runningEgressFlows;
    private Set<PluginCoordinates> installedPlugins;
}
