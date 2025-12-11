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
import org.deltafi.common.lookup.LookupTable;
import org.deltafi.common.types.*;
import org.deltafi.core.plugin.deployer.InstallDetails;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
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
    private String deploymentName;
    private String displayName;
    private String description;
    private String actionKitVersion;
    private String registrationHash;

    @Enumerated(EnumType.STRING)
    private PluginState installState = PluginState.INSTALLED;

    private String installError;
    private OffsetDateTime lastStateChange;
    private int installAttempts;

    private String lastSuccessfulVersion;
    private String lastSuccessfulImage;
    private String lastSuccessfulImageTag;

    private boolean disabled;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<ActionDescriptor> actions = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<PluginCoordinates> dependencies = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<FlowPlan> flowPlans = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<LookupTable> lookupTables = new ArrayList<>();

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
        this.deploymentName = plugin.getImageName() != null ? InstallDetails.from(plugin.getImageName()).appName() : null;
        this.displayName = plugin.getDisplayName();
        this.description = plugin.getDescription();
        this.actionKitVersion = plugin.getActionKitVersion();
        this.actions = plugin.getActions();
        this.dependencies = plugin.getDependencies();
        this.variables = plugin.getVariables();
        this.flowPlans = plugin.getFlowPlans();
        this.lookupTables = plugin.getLookupTables();
    }

    public Plugin toPlugin() {
        Plugin plugin = new Plugin();
        plugin.setImageName(imageName);
        plugin.setImageTag(imageTag);
        plugin.setImagePullSecret(imagePullSecret);
        plugin.setDeploymentName(deploymentName);
        plugin.setPluginCoordinates(this.getPluginCoordinates());
        plugin.setDisplayName(this.displayName);
        plugin.setDescription(this.description);
        plugin.setActionKitVersion(this.actionKitVersion);
        plugin.setActions(this.actions);
        plugin.setDependencies(this.dependencies);
        plugin.setVariables(this.variables);
        plugin.setFlowPlans(this.flowPlans);
        plugin.setLookupTables(this.lookupTables);
        plugin.setInstallState(this.installState != null ? this.installState : PluginState.INSTALLED);
        plugin.setInstallError(this.installError);
        plugin.setInstallAttempts(this.installAttempts);
        plugin.setLastSuccessfulVersion(this.lastSuccessfulVersion);
        plugin.setCanRollback(this.canRollback());
        plugin.setDisabled(this.isDisabled());
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

    /**
     * Transition to INSTALLING state.
     * Increments install attempts unless this is an auto-restart from INSTALLED.
     */
    public void transitionToInstalling(boolean isAutoRestart) {
        if (!isAutoRestart) {
            this.installAttempts++;
            // Clear fields that come from plugin registration - new container must re-register
            this.registrationHash = null;
            this.version = "loading";
            this.actionKitVersion = "loading";
        }
        this.installState = PluginState.INSTALLING;
        this.installError = null;
        this.lastStateChange = OffsetDateTime.now();
    }

    /**
     * Transition to INSTALLED state after successful registration.
     * Saves current version/image as last successful for potential rollback.
     */
    public void transitionToInstalled() {
        this.installState = PluginState.INSTALLED;
        this.installError = null;
        this.lastStateChange = OffsetDateTime.now();

        // Save current version as last successful for rollback
        this.lastSuccessfulVersion = this.version;
        this.lastSuccessfulImage = this.imageName;
        this.lastSuccessfulImageTag = this.imageTag;
    }

    /**
     * Transition to FAILED state with an error message.
     */
    public void transitionToFailed(String error) {
        this.installState = PluginState.FAILED;
        this.installError = error;
        this.lastStateChange = OffsetDateTime.now();
    }

    /**
     * Transition to REMOVING state.
     */
    public void transitionToRemoving() {
        this.installState = PluginState.REMOVING;
        this.lastStateChange = OffsetDateTime.now();
    }

    /**
     * Reset to PENDING state for retry.
     */
    public void transitionToPending() {
        this.installState = PluginState.PENDING;
        this.installError = null;
        this.lastStateChange = OffsetDateTime.now();
    }

    /**
     * Check if rollback to a previous version is available.
     */
    @Transient
    public boolean canRollback() {
        return installState == PluginState.FAILED &&
               lastSuccessfulVersion != null &&
               lastSuccessfulImage != null &&
               !lastSuccessfulVersion.equals(version);
    }

    /**
     * Rollback to the last successful version.
     * Sets the image/version back to last successful and transitions to PENDING.
     */
    public void rollback() {
        if (!canRollback()) {
            return;
        }

        this.version = this.lastSuccessfulVersion;
        this.imageName = this.lastSuccessfulImage;
        this.imageTag = this.lastSuccessfulImageTag;
        this.installState = PluginState.PENDING;
        this.installError = null;
        this.installAttempts = 0;
        this.lastStateChange = OffsetDateTime.now();
    }
}