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
// ABOUTME: Background service that reconciles plugin desired state vs actual state.
// ABOUTME: Installs pending plugins, restarts stopped plugins, removes plugins marked for removal.
package org.deltafi.core.services;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.plugin.deployer.DeployerService;
import org.deltafi.core.types.PluginEntity;
import org.deltafi.core.types.Result;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
@ConditionalOnProperty(value = "schedule.maintenance", havingValue = "true", matchIfMissing = true)
public class PluginReconciliationService {

    private static final Duration INSTALL_TIMEOUT = Duration.ofMinutes(5);
    private static final int MAX_CRASH_LOOP_COUNT = 3;
    private static final Duration CRASH_LOOP_WINDOW = Duration.ofMinutes(5);

    private final PluginService pluginService;
    private final DeployerService deployerService;
    private final FlowValidationService flowValidationService;
    private final SystemSnapshotService systemSnapshotService;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Set<String> deploysInProgress = ConcurrentHashMap.newKeySet();

    public PluginReconciliationService(PluginService pluginService, DeployerService deployerService,
                                       FlowValidationService flowValidationService,
                                       SystemSnapshotService systemSnapshotService) {
        this.pluginService = pluginService;
        this.deployerService = deployerService;
        this.flowValidationService = flowValidationService;
        this.systemSnapshotService = systemSnapshotService;
    }

    /**
     * Main reconciliation loop that runs on a schedule.
     * Compares desired state (DB) to actual state (containers/pods) and takes action.
     * Each plugin is reconciled independently - no global lock needed since operations are idempotent.
     */
    @Scheduled(fixedDelayString = "${deltafi.plugin.reconciliation.interval:5000}")
    public void reconcile() {
        pluginService.getPlugins().stream()
                .filter(p -> !PluginService.SYSTEM_PLUGIN_ID.equals(p.getKey()))
                .filter(p -> p.getImageName() != null)
                .forEach(this::reconcilePluginSafely);
    }

    private void reconcilePluginSafely(PluginEntity plugin) {
        try {
            if (plugin.isDisabled()) {
                handleDisabled(plugin);
            } else {
                reconcilePlugin(plugin);
            }
        } catch (Exception e) {
            log.error("Error reconciling plugin {}: {}", plugin.getPluginCoordinates(), e.getMessage());
        }
    }

    private void handleDisabled(PluginEntity plugin) {
        // Ensure disabled plugins are not running
        boolean containerRunning = deployerService.isPluginRunning(plugin.getImageName());
        if (containerRunning) {
            log.info("Stopping disabled plugin {}", plugin.getPluginCoordinates());
            deployerService.removePlugin(plugin.getImageName());
        }
    }

    private void reconcilePlugin(PluginEntity plugin) {
        switch (plugin.getInstallState()) {
            case PENDING -> handlePending(plugin);
            case INSTALLING -> handleInstalling(plugin);
            case INSTALLED -> handleInstalled(plugin);
            case FAILED -> handleFailed(plugin);
            case REMOVING -> handleRemoving(plugin);
        }
    }

    private void handlePending(PluginEntity plugin) {
        log.info("Starting installation of plugin: {}", plugin.getPluginCoordinates());
        systemSnapshotService.createSnapshot("Pre-install snapshot for plugin: " + plugin.imageAndTag());
        pluginService.markInstalling(plugin.getPluginCoordinates(), false);
        startInstall(plugin);
    }

    private void handleInstalling(PluginEntity plugin) {
        // Check if we've timed out
        if (plugin.getLastStateChange() != null) {
            Duration elapsed = Duration.between(plugin.getLastStateChange(), OffsetDateTime.now());
            if (elapsed.compareTo(INSTALL_TIMEOUT) > 0) {
                log.warn("Plugin {} install timed out after {}", plugin.getPluginCoordinates(), elapsed);
                pluginService.markFailed(plugin.getPluginCoordinates(), "Install timed out after " + INSTALL_TIMEOUT.toMinutes() + " minutes");
                return;
            }
        }

        // Check if container is running and plugin has registered
        boolean containerRunning = deployerService.isPluginRunning(plugin.getImageName());
        if (containerRunning && plugin.getRegistrationHash() != null) {
            // Verify the running container matches the expected image (user may have changed it)
            String expectedImage = plugin.imageAndTag();
            String runningImage = deployerService.getRunningPluginImage(plugin.getImageName());
            if (!imagesMatch(expectedImage, runningImage)) {
                log.info("Plugin {} registered but wrong image is running (expected {}, found {}), redeploying",
                        plugin.getPluginCoordinates(), expectedImage, runningImage);
                startInstall(plugin);
                return;
            }
            log.info("Plugin {} is now installed and registered", plugin.getPluginCoordinates());
            pluginService.markInstalled(plugin.getPluginCoordinates());
            flowValidationService.revalidateFlowsForPlugin(plugin.getPluginCoordinates());
        }
    }

    private void handleInstalled(PluginEntity plugin) {
        // Verify container is still running
        boolean containerRunning = deployerService.isPluginRunning(plugin.getImageName());
        if (!containerRunning) {
            log.warn("Plugin {} container is not running, attempting restart", plugin.getPluginCoordinates());

            // Check for crash loop
            if (isInCrashLoop(plugin)) {
                log.error("Plugin {} is in a crash loop, marking as failed", plugin.getPluginCoordinates());
                pluginService.markFailed(plugin.getPluginCoordinates(), "Crash loop detected - plugin stopped " + MAX_CRASH_LOOP_COUNT + " times in " + CRASH_LOOP_WINDOW.toMinutes() + " minutes");
                return;
            }

            pluginService.markInstalling(plugin.getPluginCoordinates(), true);
            startInstall(plugin);
            return;
        }

        // Verify the running container has the correct image
        String expectedImage = plugin.imageAndTag();
        String runningImage = deployerService.getRunningPluginImage(plugin.getImageName());
        if (runningImage != null && !imagesMatch(expectedImage, runningImage)) {
            log.warn("Plugin {} image mismatch: expected {} but found {}, redeploying",
                    plugin.getPluginCoordinates(), expectedImage, runningImage);
            pluginService.markInstalling(plugin.getPluginCoordinates(), false);
            startInstall(plugin);
        }
    }

    /**
     * Compare two image names for equality, ignoring registry prefixes.
     * Docker may return image names with or without registry prefix depending on how they were pulled.
     */
    private boolean imagesMatch(String expected, String running) {
        if (expected == null || running == null) {
            return expected == null && running == null;
        }
        if (expected.equals(running)) {
            return true;
        }
        // Normalize both images by extracting name:tag
        String expectedNameTag = extractNameAndTag(expected);
        String runningNameTag = extractNameAndTag(running);
        return expectedNameTag.equals(runningNameTag);
    }

    /**
     * Extract the image name and tag portion from a full image reference.
     * e.g., "registry.example.com/org/image:tag" -> "org/image:tag"
     * e.g., "docker.io/library/image:tag" -> "library/image:tag"
     */
    private String extractNameAndTag(String image) {
        // Find the last slash that's part of the registry (before the org/name)
        int firstSlash = image.indexOf('/');
        if (firstSlash == -1) {
            return image;
        }

        // Check if the part before the first slash looks like a registry (contains . or :)
        String beforeSlash = image.substring(0, firstSlash);
        if (beforeSlash.contains(".") || beforeSlash.contains(":")) {
            // This is a registry prefix, strip it
            return image.substring(firstSlash + 1);
        }

        return image;
    }

    private void handleFailed(PluginEntity plugin) {
        // Check if the container eventually became healthy and registered
        boolean containerRunning = deployerService.isPluginRunning(plugin.getImageName());
        if (containerRunning && plugin.getRegistrationHash() != null) {
            log.info("Plugin {} recovered - container is now running and registered", plugin.getPluginCoordinates());
            pluginService.markInstalled(plugin.getPluginCoordinates());
            flowValidationService.revalidateFlowsForPlugin(plugin.getPluginCoordinates());
        }
        // Otherwise, wait for user to retry or rollback via API
    }

    private void handleRemoving(PluginEntity plugin) {
        boolean containerRunning = deployerService.isPluginRunning(plugin.getImageName());
        if (!containerRunning) {
            log.info("Plugin {} container stopped, removing from database", plugin.getPluginCoordinates());
            pluginService.uninstallPlugin(plugin.getPluginCoordinates());
        } else {
            // Attempt to stop the container
            log.info("Stopping plugin {} container", plugin.getPluginCoordinates());
            deployerService.removePlugin(plugin.getImageName());
        }
    }

    private void startInstall(PluginEntity plugin) {
        String pluginKey = plugin.getKey().toString();

        // Prevent concurrent deploys of the same plugin
        if (!deploysInProgress.add(pluginKey)) {
            log.debug("Deploy already in progress for {}, skipping", plugin.getPluginCoordinates());
            return;
        }

        String imagePullSecret = plugin.getImagePullSecret();
        String image = plugin.imageAndTag();

        log.info("Deploying plugin {} from image {}", plugin.getPluginCoordinates(), image);

        executor.submit(() -> {
            try {
                Result result = deployerService.installOrUpgradePlugin(image, imagePullSecret);
                if (!result.isSuccess()) {
                    String error = result.getErrors() != null && !result.getErrors().isEmpty()
                            ? String.join("; ", result.getErrors())
                            : "Unknown installation error";
                    log.error("Failed to install plugin {}: {}", plugin.getPluginCoordinates(), error);
                    pluginService.markFailed(plugin.getPluginCoordinates(), error);
                }
                // On success, we stay in INSTALLING state until the plugin registers
            } finally {
                deploysInProgress.remove(pluginKey);
            }
        });
    }

    /**
     * Check if a plugin is in a crash loop by looking at recent state changes.
     * A crash loop is defined as MAX_CRASH_LOOP_COUNT transitions from INSTALLED in CRASH_LOOP_WINDOW.
     */
    private boolean isInCrashLoop(PluginEntity plugin) {
        // Simple heuristic: if we've had multiple install attempts in a short window
        // and we're back to needing a restart, we're probably crash looping
        if (plugin.getInstallAttempts() >= MAX_CRASH_LOOP_COUNT) {
            OffsetDateTime windowStart = OffsetDateTime.now().minus(CRASH_LOOP_WINDOW);
            return plugin.getLastStateChange() != null && plugin.getLastStateChange().isAfter(windowStart);
        }
        return false;
    }
}
