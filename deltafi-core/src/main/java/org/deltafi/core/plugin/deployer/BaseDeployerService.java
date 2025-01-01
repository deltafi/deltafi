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
package org.deltafi.core.plugin.deployer;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.services.*;
import org.deltafi.core.types.Event;
import org.deltafi.core.types.Event.Severity;
import org.deltafi.core.types.PluginEntity;
import org.deltafi.core.types.Result;
import org.deltafi.core.util.MarkdownBuilder;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class BaseDeployerService implements DeployerService {
    protected static final String IMAGE = "IMAGE";
    protected static final String IMAGE_PULL_SECRET = "IMAGE_PULL_SECRET";

    private final PluginService pluginService;
    private final SystemSnapshotService systemSnapshotService;
    private final EventService eventService;
    protected final DeltaFiPropertiesService deltaFiPropertiesService;

    @Override
    public Result installOrUpgradePlugin(String image, String imagePullSecret) {
        if (StringUtils.isBlank(imagePullSecret)) {
            imagePullSecret = deltaFiPropertiesService.getDeltaFiProperties().getPluginImagePullSecret();
        }

        InstallDetails installDetails = InstallDetails.from(image, imagePullSecret);
        systemSnapshotService.createSnapshot(preUpgradeMessage(image));

        List<String> info = new ArrayList<>();
        info.add("Installing from image: " + image);

        if (StringUtils.isNotBlank(imagePullSecret)) {
            info.add("Using image pull secret: " + imagePullSecret);
        }

        DeployResult deployResult = deploy(installDetails);
        deployResult.setInfo(info);
        publishEvent(installDetails, deployResult);
        return deployResult.detailedResult();
    }

    private void publishEvent(InstallDetails installDetails, DeployResult retval) {
        MarkdownBuilder markdownBuilder = new MarkdownBuilder(installDetails.appName() + "\n\n");
        if (retval.hasEvents()) {
            markdownBuilder.addTable("#### Events:", K8sEventUtil.EVENT_COLUMNS, retval.getEvents());
        }

        if (retval.getLogs() != null) {
            markdownBuilder.addJsonBlock("#### Plugin Logs:", retval.getLogs());
        }

        String summary = "Plugin installed from image: " + installDetails.image();
        eventService.createEvent(event(summary, retval.isSuccess(), markdownBuilder));
    }

    @Override
    public Result uninstallPlugin(PluginCoordinates pluginCoordinates, boolean force) {
        PluginEntity plugin = pluginService.getPlugin(pluginCoordinates).orElse(null);
        List<String> uninstallPreCheckErrors = pluginService.canBeUninstalled(plugin);
        if (plugin == null || (!force && !uninstallPreCheckErrors.isEmpty())) {
            return Result.builder().success(false).errors(uninstallPreCheckErrors).build();
        }

        pluginService.uninstallPlugin(pluginCoordinates);
        String appName = pluginCoordinates.getArtifactId();
        if (StringUtils.isNotBlank(plugin.getImageName())) {
            appName = InstallDetails.from(plugin.getImageName()).appName();
        }
        Result retval = removePluginResources(appName);

        MarkdownBuilder markdownBuilder = new MarkdownBuilder(pluginCoordinates + "\n\n")
                .addList("Additional information:", retval.getInfo())
                .addList("Errors:", retval.getErrors());

        if (force && !uninstallPreCheckErrors.isEmpty()) {
            // Avoid immutable list exception
            retval.setInfo(new ArrayList<>(retval.getInfo()));
            retval.getInfo().add("Forced uninstall ignored the following checks:");
            retval.getInfo().addAll(uninstallPreCheckErrors);
            markdownBuilder.addList("Ignored:", uninstallPreCheckErrors);
        }

        String summary = "Plugin uninstalled: " + pluginCoordinates.getArtifactId() + ":" + pluginCoordinates.getVersion();
        eventService.createEvent(event(summary, retval.isSuccess(), markdownBuilder));
        return retval;
    }

    private Event event(String summary, boolean success, MarkdownBuilder markdownBuilder) {
        return Event.builder()
                .summary(summary)
                .timestamp(OffsetDateTime.now())
                .source("core")
                .notification(true)
                .severity(success ? Severity.SUCCESS : Severity.ERROR)
                .content(markdownBuilder.build())
                .build();
    }

    abstract DeployResult deploy(InstallDetails installDetails);

    abstract Result removePluginResources(String appName);

    private String preUpgradeMessage(String imageName) {
        String username = getUsername();
        String reason = "Deploying plugin from image: " + imageName;
        if (username != null) {
            reason += " triggered by " + username;
        }
        return reason;
    }

    private String getUsername() {
        return DeltaFiUserService.currentUsername();
    }
}
