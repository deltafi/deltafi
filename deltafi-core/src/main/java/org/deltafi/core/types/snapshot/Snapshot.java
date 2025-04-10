/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.types.snapshot;

import lombok.Data;
import org.deltafi.common.types.KeyValue;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.configuration.ui.Link;
import org.deltafi.core.generated.types.SystemFlowPlans;
import org.deltafi.core.types.DeletePolicies;
import org.deltafi.core.types.PluginVariables;
import org.deltafi.core.types.ResumePolicy;

import java.util.ArrayList;
import java.util.List;

@Data
public class Snapshot {
    private List<PluginVariables> pluginVariables;
    private DeletePolicies deletePolicies;
    private List<KeyValue> deltaFiProperties;
    private List<Link> links;
    private List<RestDataSourceSnapshot> restDataSources = new ArrayList<>();
    private List<TimedDataSourceSnapshot> timedDataSources = new ArrayList<>();
    private List<TransformFlowSnapshot> transformFlows = new ArrayList<>();
    private List<DataSinkSnapshot> dataSinks = new ArrayList<>();
    private List<PluginSnapshot> plugins;
    private List<ResumePolicy> resumePolicies = new ArrayList<>();
    private SystemFlowPlans systemFlowPlans = new SystemFlowPlans();
    private List<UserSnapshot> users = new ArrayList<>();
    private List<RoleSnapshot> roles = new ArrayList<>();
}