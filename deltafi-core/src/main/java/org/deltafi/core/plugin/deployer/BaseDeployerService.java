/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.core.plugin.deployer.image.PluginImageRepository;
import org.deltafi.core.plugin.deployer.image.PluginImageRepositoryService;
import org.deltafi.core.services.DeltaFiUserService;
import org.deltafi.core.services.EventService;
import org.deltafi.core.services.PluginService;
import org.deltafi.core.services.SystemSnapshotService;
import org.deltafi.core.types.Event;
import org.deltafi.core.types.Event.Severity;
import org.deltafi.core.types.Result;
import org.deltafi.core.util.MarkdownBuilder;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class BaseDeployerService implements DeployerService {

    final PluginImageRepositoryService pluginImageRepositoryService;
    private final PluginService pluginService;
    private final SystemSnapshotService systemSnapshotService;
    private final EventService eventService;

    @Override
    public Result installOrUpgradePlugin(PluginCoordinates pluginCoordinates, String imageRepoOverride, String imagePullSecretOverride, String customDeploymentOverride) {
        systemSnapshotService.createSnapshot(preUpgradeMessage(pluginCoordinates));

        PluginImageRepository pluginImageRepository = pluginImageRepositoryService.findByGroupId(pluginCoordinates);

        ArrayList<String> info = new ArrayList<>();

        if (imageRepoOverride != null) {
            pluginImageRepository.setImageRepositoryBase(imageRepoOverride);
            info.add("Image repo override: " + imageRepoOverride);
        }

        if (imagePullSecretOverride != null) {
            pluginImageRepository.setImagePullSecret(imagePullSecretOverride);
            info.add("Image pull secret override: " + imagePullSecretOverride);
        }

        DeployResult deployResult = deploy(pluginCoordinates, pluginImageRepository, info);
        publishEvent(pluginCoordinates, deployResult);
        return deployResult.detailedResult();
    }

    private void publishEvent(PluginCoordinates pluginCoordinates, DeployResult retval) {
        MarkdownBuilder markdownBuilder = new MarkdownBuilder(pluginCoordinates + "\n\n");
        if (retval.hasEvents()) {
            markdownBuilder.addTable("#### Events:", K8sEventUtil.EVENT_COLUMNS, retval.getEvents());
        }

        if (retval.getLogs() != null) {
            markdownBuilder.addJsonBlock("#### Plugin Logs:", retval.getLogs());
        }

        eventService.createEvent(event(pluginCoordinates, "Plugin installed: ", retval.isSuccess(), markdownBuilder));
    }

    @Override
    public Result uninstallPlugin(PluginCoordinates pluginCoordinates) {
        List<String> uninstallPreCheckErrors = pluginService.canBeUninstalled(pluginCoordinates);
        if (!uninstallPreCheckErrors.isEmpty()) {
            return Result.builder().success(false).errors(uninstallPreCheckErrors).build();
        }

        pluginService.uninstallPlugin(pluginCoordinates);
        Result retval = removePluginResources(pluginCoordinates);

        MarkdownBuilder markdownBuilder = new MarkdownBuilder(pluginCoordinates + "\n\n")
                .addList("Additional information:", retval.getInfo())
                .addList("Errors:", retval.getErrors());

        eventService.createEvent(event(pluginCoordinates, "Plugin uninstalled: ", retval.isSuccess(), markdownBuilder));
        return retval;
    }

    private Event event(PluginCoordinates pluginCoordinates, String summary, boolean success, MarkdownBuilder markdownBuilder) {
        return Event.builder()
                .summary(summary + pluginCoordinates.getArtifactId() + ":" + pluginCoordinates.getVersion())
                .timestamp(OffsetDateTime.now())
                .source("core")
                .notification(true)
                .severity(success ? Severity.SUCCESS : Severity.ERROR)
                .content(markdownBuilder.build())
                .build();
    }

    abstract DeployResult deploy(PluginCoordinates pluginCoordinates, PluginImageRepository pluginImageRepository, ArrayList<String> info);

    abstract Result removePluginResources(PluginCoordinates pluginCoordinates);

    private String preUpgradeMessage(PluginCoordinates pluginCoordinates) {
        String username = getUsername();
        String reason = "Deploying plugin: " + pluginCoordinates.toString();
        if (username != null) {
            reason += " triggered by " + username;
        }
        return reason;
    }

    private String getUsername() {
        return DeltaFiUserService.currentUsername();
    }
}
