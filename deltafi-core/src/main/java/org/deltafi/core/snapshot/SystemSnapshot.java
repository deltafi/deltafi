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
package org.deltafi.core.snapshot;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.plugin.deployer.customization.PluginCustomizationConfig;
import org.deltafi.core.plugin.deployer.image.PluginImageRepository;
import org.deltafi.core.snapshot.types.*;
import org.deltafi.core.types.DeletePolicies;
import org.deltafi.core.types.FlowAssignmentRule;
import org.deltafi.core.types.PluginVariables;
import org.deltafi.core.types.ResumePolicy;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Data
@Document
@NoArgsConstructor
public class SystemSnapshot {
    @Id
    private String id;
    private String reason;
    private OffsetDateTime created = OffsetDateTime.now();
    private List<PluginVariables> pluginVariables;
    private DeletePolicies deletePolicies;
    private DeltaFiProperties deltaFiProperties;
    private List<FlowAssignmentRule> flowAssignmentRules;
    private List<String> runningIngressFlows;
    private List<String> testIngressFlows;
    private List<String> runningEnrichFlows;
    private List<String> runningEgressFlows;
    private List<String> testEgressFlows;
    private List<String> runningTransformFlows;
    private List<String> testTransformFlows;

    private List<TimedIngressFlowSnapshot> timedIngressFlows = new ArrayList<>();
    private List<NormalizeFlowSnapshot> normalizeFlows = new ArrayList<>();
    private List<TransformFlowSnapshot> transformFlows = new ArrayList<>();
    private List<EnrichFlowSnapshot> enrichFlows = new ArrayList<>();
    private List<EgressFlowSnapshot> egressFlows = new ArrayList<>();

    private Set<PluginCoordinates> installedPlugins;
    private List<PluginCustomizationConfig> pluginCustomizationConfigs = new ArrayList<>();
    private List<PluginImageRepository> pluginImageRepositories = new ArrayList<>();
    private List<ResumePolicy> resumePolicies = new ArrayList<>();
}
