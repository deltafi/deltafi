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

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.common.util.MarkdownBuilder;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.deltafi.core.services.EventService;
import org.deltafi.core.services.PluginService;
import org.deltafi.core.types.Event;
import org.deltafi.core.types.Event.Severity;
import org.deltafi.core.types.PluginEntity;
import org.deltafi.core.types.Result;
import org.deltafi.core.types.snapshot.PluginSnapshot;
import org.deltafi.core.types.snapshot.Snapshot;
import org.deltafi.core.types.snapshot.SnapshotRestoreOrder;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.stream.Stream;

@Slf4j
public abstract class BaseDeployerService implements DeployerService {
    protected static final String IMAGE = "IMAGE";
    protected static final String IMAGE_PULL_SECRET = "IMAGE_PULL_SECRET";

    private final PluginService pluginService;

    private final EventService eventService;
    protected final DeltaFiPropertiesService deltaFiPropertiesService;
    protected final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    protected BaseDeployerService(PluginService pluginService, EventService eventService, DeltaFiPropertiesService deltaFiPropertiesService) {
        this.deltaFiPropertiesService = deltaFiPropertiesService;
        this.pluginService = pluginService;
        this.eventService = eventService;
    }

    @Override
    public Result installOrUpgradePlugin(String image, String imagePullSecret) {
        if (StringUtils.isBlank(imagePullSecret)) {
            imagePullSecret = deltaFiPropertiesService.getDeltaFiProperties().getPluginImagePullSecret();
        }

        InstallDetails installDetails = InstallDetails.from(image, imagePullSecret);

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
            markdownBuilder.append("#### Events:\n").addSimpleTable(K8sEventUtil.EVENT_COLUMNS, retval.getEvents());
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

        Result retval = uninstallPlugin(plugin);

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

    @Override
    public List<Result> installOrUpgradePlugins(List<InstallDetails> installDetailsList) {
        try {
            return invokeDeployments(installDetailsList.stream().map(this::toCallableDeploy).toList());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while deploying plugins", e);
            return List.of(DeployResult.builder().success(false).errors(List.of("Interrupted while deploying plugins")).build());
        }
    }

    /**
     * Update the SystemSnapshot with current system state
     *
     * @param snapshot snapshot that is used to capture the current system state
     */
    @Override
    public void updateSnapshot(Snapshot snapshot) {
        // nothing to do here
    }

    /**
     * Reset the system to the state in the Snapshot
     *
     * @param snapshot  snapshot that holds the system state at the time of the snapshot
     * @param hardReset when true reset all other custom settings before applying the system snapshot values
     * @return the Result of the reset that will hold any errors or information about the reset
     */
    @Override
    public Result resetFromSnapshot(Snapshot snapshot, boolean hardReset) {
        if (snapshot.getPlugins() == null) {
            return Result.builder().success(true).info(List.of("Skipping plugins section due to an older snapshot version")).build();
        }

        if (hardReset) {
            pluginService.getPlugins()
                    .stream().filter(this::isNotSystemOrCore)
                    .forEach(this::uninstallPlugin);
        }

        List<InstallDetails> installDetailsList = snapshot.getPlugins().stream()
                .filter(p -> StringUtils.isNotBlank(p.imageName()) && !p.imageName().contains("deltafi-core-actions"))
                .map(PluginSnapshot::toInstallDetails).toList();

        return Result.combine(installOrUpgradePlugins(installDetailsList).stream());
    }

    private boolean isNotSystemOrCore(PluginEntity plugin) {
        return !(PluginService.SYSTEM_PLUGIN_ARTIFACT_ID.equals(plugin.getPluginCoordinates().getArtifactId()) ||
                "deltafi-core-actions".equals(plugin.getPluginCoordinates().getArtifactId()));
    }

    @Override
    public int getOrder() {
        return SnapshotRestoreOrder.PLUGIN_INSTALL_ORDER;
    }

    protected Stream<InstallDetails> getAllPluginInstallInfo() {
        return this.pluginService.getPlugins().stream()
                .map(this::toInstallDetails)
                .filter(Objects::nonNull);
    }

    private InstallDetails toInstallDetails(PluginEntity pluginEntity) {
        String imageName = pluginEntity.getImageName();
        if (StringUtils.isBlank(imageName)) {
            return null;
        }

        String secretName = pluginEntity.getImagePullSecret();
        if (StringUtils.isBlank(secretName)) {
            secretName = deltaFiPropertiesService.getDeltaFiProperties().getPluginImagePullSecret();
        }

        if (StringUtils.isNotBlank(pluginEntity.getImageTag())) {
            imageName += ":" + pluginEntity.getImageTag();
        }

        return InstallDetails.from(imageName, secretName);
    }

    protected String getGroupName(String appName) {
        return "deltafi-core-actions".equals(appName) ? "deltafi-core" : "deltafi-plugins";
    }

    private Callable<Result> toCallableDeploy(InstallDetails installDetails) {
        return () -> installOrUpgradePlugin(installDetails.image(), installDetails.imagePullSecret());
    }

    private List<Result> invokeDeployments(List<Callable<Result>> deployTasks) throws InterruptedException {
        long timeout = deltaFiPropertiesService.getDeltaFiProperties().getPluginDeployTimeout().toMillis();
        List<Future<Result>> futures = executorService.invokeAll(deployTasks, timeout, TimeUnit.MILLISECONDS);

        List<Result> results = new ArrayList<>();
        // Process the results
        for (Future<Result> future : futures) {
            try {
                // isDone() will be true either if completed normally or timed out
                if (future.isDone() && !future.isCancelled()) {
                    results.add(future.get());
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                results.add(DeployResult.builder().success(false).errors(List.of("Deployment failed: " + e.getMessage())).build());
            }
        }
        return results;
    }

    private Result uninstallPlugin(PluginEntity plugin) {
        PluginCoordinates pluginCoordinates = plugin.getPluginCoordinates();
        pluginService.uninstallPlugin(pluginCoordinates);
        String appName = pluginCoordinates.getArtifactId();
        if (StringUtils.isNotBlank(plugin.getImageName())) {
            appName = InstallDetails.from(plugin.getImageName()).appName();
        }
        return removePluginResources(appName);
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
}
