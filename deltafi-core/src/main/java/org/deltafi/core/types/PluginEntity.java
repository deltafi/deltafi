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
package org.deltafi.core.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.types.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "plugins")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Data
@NoArgsConstructor
public class PluginEntity {
    @EmbeddedId
    private GroupIdArtifactId key;
    private String version;

    private String imageName;
    private String imageTag;
    private String imagePullSecret;
    private String displayName;
    private String description;
    private String actionKitVersion;
    private String registrationHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<ActionDescriptor> actions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<PluginCoordinates> dependencies;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<FlowPlan> flowPlans = new ArrayList<>();

    @Transient
    private List<Variable> variables;

    @Transient
    public PluginCoordinates getPluginCoordinates() {
        return PluginCoordinates.builder()
                .groupId(key == null ? null : key.getGroupId())
                .artifactId(key == null ? null : key.getArtifactId())
                .version(version)
                .build();
    }

    @Transient
    public void setPluginCoordinates(PluginCoordinates pluginCoordinates) {
        this.key = new GroupIdArtifactId(pluginCoordinates.getGroupId(), pluginCoordinates.getArtifactId());
        this.version = pluginCoordinates.getVersion();
    }

    public PluginEntity(Plugin plugin) {
        this.key = new GroupIdArtifactId(plugin.getPluginCoordinates().getGroupId(), plugin.getPluginCoordinates().getArtifactId());
        this.version = plugin.getPluginCoordinates().getVersion();
        this.imageName = plugin.getImageName();
        this.imageTag = plugin.getImageTag();
        this.imagePullSecret = plugin.getImagePullSecret();
        this.displayName = plugin.getDisplayName();
        this.description = plugin.getDescription();
        this.actionKitVersion = plugin.getActionKitVersion();
        this.actions = plugin.getActions();
        this.dependencies = plugin.getDependencies();
        this.variables = plugin.getVariables();
        this.flowPlans = plugin.getFlowPlans();
    }

    public Plugin toPlugin() {
        Plugin plugin = new Plugin();
        plugin.setImageName(imageName);
        plugin.setImageTag(imageTag);
        plugin.setImagePullSecret(imagePullSecret);
        plugin.setPluginCoordinates(this.getPluginCoordinates());
        plugin.setDisplayName(this.displayName);
        plugin.setDescription(this.description);
        plugin.setActionKitVersion(this.actionKitVersion);
        plugin.setActions(this.actions);
        plugin.setDependencies(this.dependencies);
        plugin.setVariables(this.variables);
        plugin.setFlowPlans(this.flowPlans);
        return plugin;
    }

    public List<String> actionNames() {
        return Objects.nonNull(getActions()) ?
                getActions().stream().map(ActionDescriptor::getName).toList() : List.of();
    }

    public void addOrReplaceFlowPlans(List<? extends FlowPlan> flowPlans) {
        if (flowPlans == null) {
            return;
        }

        for (FlowPlan flowPlan : flowPlans) {
            this.flowPlans.removeIf(flowPlan::nameAndTypeMatch);
            flowPlan.setSourcePlugin(this.getPluginCoordinates());
            this.flowPlans.add(flowPlan);
        }
    }

    public String imageAndTag() {
        return StringUtils.isNotEmpty(imageTag) ? imageName + ":" + imageTag : imageName;
    }
}