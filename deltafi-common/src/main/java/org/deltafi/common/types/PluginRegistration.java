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
package org.deltafi.common.types;

import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.types.integration.IntegrationTest;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class PluginRegistration {
    private String image;
    private String imagePullSecret;
    private PluginCoordinates pluginCoordinates;
    private String displayName;
    private String description;
    private String actionKitVersion;
    private List<PluginCoordinates> dependencies;
    private List<ActionDescriptor> actions;
    private List<Variable> variables;
    @Builder.Default
    private List<FlowPlan> flowPlans = new ArrayList<>();
    @Builder.Default
    private List<IntegrationTest> integrationTests = new ArrayList<>();

    public Plugin toPlugin() {
        Plugin plugin = new Plugin();
        if (StringUtils.isNotBlank(image)) {
            Image imageObj = Image.image(image);
            plugin.setImageName(imageObj.name());
            plugin.setImageTag(imageObj.tag());
        }
        plugin.setImagePullSecret(imagePullSecret);
        plugin.setPluginCoordinates(pluginCoordinates);
        plugin.setDisplayName(displayName);
        plugin.setDescription(description);
        plugin.setActionKitVersion(actionKitVersion);
        plugin.setActions(actions);
        plugin.setDependencies(dependencies);
        plugin.setFlowPlans(flowPlans);
        return plugin;
    }
}
