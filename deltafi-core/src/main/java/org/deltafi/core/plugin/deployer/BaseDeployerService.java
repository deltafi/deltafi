/**
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
package org.deltafi.core.plugin.deployer;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.plugin.PluginRegistryService;
import org.deltafi.core.plugin.deployer.customization.PluginCustomizationConfig;
import org.deltafi.core.plugin.deployer.customization.PluginCustomizationService;
import org.deltafi.core.plugin.deployer.image.PluginImageRepositoryService;
import org.deltafi.core.services.EventService;
import org.deltafi.core.services.api.model.Event;
import org.deltafi.core.snapshot.SystemSnapshotService;
import org.deltafi.core.types.Result;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

import java.util.List;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class BaseDeployerService implements DeployerService {


    final PluginImageRepositoryService pluginImageRepositoryService;
    private final PluginRegistryService pluginRegistryService;
    final PluginCustomizationService pluginCustomizationService;
    private final SystemSnapshotService systemSnapshotService;
    private final EventService eventService;

    @Override
    public Result installOrUpgradePlugin(PluginCoordinates pluginCoordinates, String imageRepoOverride, String imagePullSecretOverride, String customDeploymentOverride) {
        systemSnapshotService.createSnapshot(preUpgradeMessage(pluginCoordinates));
        DeployResult deployResult = deploy(pluginCoordinates, imageRepoOverride, imagePullSecretOverride, customDeploymentOverride);
        publishEvent(pluginCoordinates, deployResult);
        return deployResult.detailedResult();
    }

    private void publishEvent(PluginCoordinates pluginCoordinates, DeployResult retval) {
        Event.EventBuilder eventBuilder = Event.builder("Plugin installed: " + pluginCoordinates.getArtifactId() + ":" + pluginCoordinates.getVersion())
                .notification(true)
                .severity(retval.isSuccess() ? Event.Severity.SUCCESS : Event.Severity.ERROR)
                .content(pluginCoordinates + "\\n\\n")
                .addList("Additional information:", retval.getInfo())
                .addList("Errors:", retval.getErrors());

        if (retval.hasEvents()) {
            eventBuilder.addTable("#### Events:", K8sEventUtil.EVENT_COLUMNS, retval.getEvents());
        }

        if (retval.getLogs() != null) {
            eventBuilder.addJsonLog("#### Plugin Logs:", retval.getLogs());
        }

        eventService.publishEvent(eventBuilder.build());
    }

    @Override
    public Result uninstallPlugin(PluginCoordinates pluginCoordinates) {
        List<String> uninstallPreCheckErrors = pluginRegistryService.canBeUninstalled(pluginCoordinates);
        if (!uninstallPreCheckErrors.isEmpty()) {
            return Result.newBuilder().success(false).errors(uninstallPreCheckErrors).build();
        }

        pluginRegistryService.uninstallPlugin(pluginCoordinates);
        Result retval = removePluginResources(pluginCoordinates);

        eventService.publishEvent(
                Event.builder("Plugin uninstalled: " + pluginCoordinates.getArtifactId() + ":" + pluginCoordinates.getVersion())
                        .notification(true)
                        .severity(retval.isSuccess() ? Event.Severity.SUCCESS : Event.Severity.ERROR)
                        .content(pluginCoordinates + "\\n\\n")
                        .addList("Additional information:", retval.getInfo())
                        .addList("Errors:", retval.getErrors())
                        .build()
        );

        return retval;
    }

    abstract DeployResult deploy(PluginCoordinates pluginCoordinates, String imageRepoOverride, String imagePullSecretOverride, String customDeploymentOverride);

    abstract Result removePluginResources(PluginCoordinates pluginCoordinates);

    @Override
    public List<PluginCustomizationConfig> getPluginCustomizationConfigs() {
        return pluginCustomizationService.getAllConfiguration();
    }

    @Override
    public PluginCustomizationConfig savePluginCustomizationConfig(PluginCustomizationConfig pluginCustomizationConfigInput) {
        pluginCustomizationConfigInput.setComputedId();
        return pluginCustomizationService.save(pluginCustomizationConfigInput);
    }

    @Override
    public Result removePluginCustomizationConfig(String id) {
        return pluginCustomizationService.delete(id);
    }

    private String preUpgradeMessage(PluginCoordinates pluginCoordinates) {
        String username = getUsername();
        String reason = "Deploying plugin: " + pluginCoordinates.toString();
        if (username != null) {
            reason += " triggered by " + username;
        }
        return reason;
    }

    private String getUsername() {
        SecurityContext securityContext = SecurityContextHolder.getContext();

        if (securityContext == null || securityContext.getAuthentication() == null || securityContext.getAuthentication().getPrincipal() == null) {
            return null;
        }

        Object principal = securityContext.getAuthentication().getPrincipal();

        return principal instanceof User ? ((User) principal).getUsername() : principal.toString();
    }

}
