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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.command.InspectContainerResponse.ContainerState;
import com.github.dockerjava.api.model.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.core.configuration.SslSecretNames;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.deltafi.core.services.EventService;
import org.deltafi.core.services.PluginService;
import org.deltafi.core.types.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.deltafi.core.services.DockerPlatformService.DELTAFI_GROUP;

@Slf4j
public class DockerDeployerService extends BaseDeployerService implements DeployerService {

    public static final int IMAGE_PULL_TIMEOUT_SECONDS = 60;
    private static final String APP_LABEL_KEY = "app";

    // the times are all in nanoseconds
    private static final HealthCheck PLUGIN_HEALTH_CHECK = new HealthCheck()
                .withInterval(1_000_000_000L)
                .withRetries(30)
                .withTest(List.of("CMD-SHELL", "cat /tmp/running"))
                .withStartPeriod(5_000_000_000L)
                .withTimeout(1_000_000_000L);

    private final DockerClient dockerClient;
    private final SslSecretNames sslSecretNames;

    private final List<String> environmentVariables;
    private final String dataDir;

    public DockerDeployerService(DockerClient dockerClient, PluginService pluginService, SslSecretNames sslSecretNames,
                                 EventService eventService, EnvironmentVariableHelper environmentVariableHelper,
                                 DeltaFiPropertiesService deltaFiPropertiesService) {
        super(pluginService, eventService, deltaFiPropertiesService);
        this.dockerClient = dockerClient;
        this.sslSecretNames = sslSecretNames;
        this.environmentVariables = environmentVariableHelper.getEnvVars();
        this.dataDir = environmentVariableHelper.getDataDir();
    }

    @Override
    DeployResult deploy(InstallDetails installDetails) {
        List<Container> existing;
        try {
            existing = findExisting(installDetails.appName());
            if (existing.size() > 1) {
                return DeployResult.builder().success(false).errors(List.of("Multiple containers found for this plugin")).build();
            }
        } catch (Exception e) {
            log.error("Failed to start plugin", e);
            return DeployResult.builder().success(false).errors(List.of("Failed to list running plugins " + e.getMessage())).build();
        }

        String imageCheckError = ensureImageExists(installDetails.image());
        if (imageCheckError != null) {
            return DeployResult.builder().success(false).errors(List.of(imageCheckError)).build();
        }

        try {
            if (isEmpty(existing)) {
                log.info("No existing container found for {}, installing fresh", installDetails.appName());
                install(installDetails);
            } else {
                log.info("Found {} existing container(s) for {}, upgrading", existing.size(), installDetails.appName());
                upgrade(existing.getFirst(), installDetails);
            }

            return new DeployResult();
        } catch (Exception e) {
            log.error("Failed to install the plugin with image: {}", installDetails.image(), e);
            return DeployResult.builder().success(false).errors(List.of(extractDockerError(e))).build();
        }
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private String extractDockerError(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return "Unknown error";
        }
        // Extract message from Docker JSON response: Status NNN: {"message":"..."}
        // Keep the status code but extract the readable message
        String prefix = "";
        String jsonPart = message;
        if (message.startsWith("Status ")) {
            int colonIdx = message.indexOf(": ");
            if (colonIdx != -1) {
                prefix = message.substring(0, colonIdx + 2);
                jsonPart = message.substring(colonIdx + 2);
            }
        }
        try {
            JsonNode json = OBJECT_MAPPER.readTree(jsonPart);
            JsonNode msgNode = json.get("message");
            if (msgNode != null && msgNode.isTextual()) {
                return prefix + msgNode.asText();
            }
        } catch (Exception ignored) {
            // Not valid JSON, return original message
        }
        return e.getMessage();
    }

    @Override
    public void restartPlugin(String plugin) {
        restartPlugin(plugin, true);
    }

    @Override
    public boolean restartPlugin(String plugin, boolean waitForSuccess) {
        List<Container> containers = findByLabel(plugin);
        if (isEmpty(containers)) {
            // no container found, so assume this is a container ID
            return restartById(plugin, waitForSuccess);
        } else {
            return restartContainers(containers, waitForSuccess);
        }
    }

    private boolean restartContainers(List<Container> containers, boolean waitForSuccess) {
        if (containers.size() == 1) {
            return restartById(containers.getFirst().getId(), waitForSuccess);
        }

        List<Callable<Boolean>> pendingHealthChecks = new ArrayList<>();
        for (Container container : containers) {
            dockerClient.restartContainerCmd(container.getId()).exec();
            if (waitForSuccess) {
                pendingHealthChecks.add(() -> awaitHealthy(container.getId()));
            }
        }

        return pendingHealthChecks.isEmpty() || awaitAllHealth(pendingHealthChecks);
    }

    private boolean restartById(String containerId, boolean waitForSuccess) {
        dockerClient.restartContainerCmd(containerId).exec();
        return !waitForSuccess || awaitHealthy(containerId);
    }

    private boolean awaitAllHealth(List<Callable<Boolean>> pendingHealthChecks) {
        try {
            long timeout = deltaFiPropertiesService.getDeltaFiProperties().getPluginDeployTimeout().toMillis();
            List<Future<Boolean>> healthChecks = executorService.invokeAll(pendingHealthChecks, timeout, TimeUnit.MILLISECONDS);
            boolean allHealthy = true;
            for (Future<Boolean> containerCheck : healthChecks) {
                if (containerCheck.isDone() && !containerCheck.isCancelled()) {
                    allHealthy &= containerCheck.get();
                }
            }
            return allHealthy;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting for health checks to complete", e);
            return false;
        } catch (ExecutionException e) {
            log.error("Health check failed during plugin restart", e);
            return false;
        }
    }

    @Override
    public void restartApp(String podOrContainer) {
        // reuse the general restartPlugin - in compose there is only one container per plugin to start
        restartPlugin(podOrContainer);
    }

    private void install(InstallDetails installDetails) {
        // Remove any existing container with the same name to handle orphaned containers
        removeContainerByName(installDetails.appName());

        Map<String, String> containerLabels = Map.of(APP_LABEL_KEY, installDetails.appName(), DELTAFI_GROUP, getGroupName(installDetails.appName()), "logging", "promtail", "logging_jobname", "containerlogs");

        List<String> envVars = new ArrayList<>(environmentVariables);
        envVars.add(IMAGE + "=" + installDetails.image());
        if (StringUtils.isNotBlank(installDetails.imagePullSecret())) {
            envVars.add(IMAGE_PULL_SECRET + "=" + installDetails.imagePullSecret());
        }
        envVars.add("APP_NAME=" + installDetails.appName());

        CreateContainerResponse containerResponse;
        try (CreateContainerCmd containerCmd = dockerClient.createContainerCmd(installDetails.image())
                .withName(installDetails.appName())
                .withEnv(envVars)
                .withLabels(containerLabels)
                .withHealthcheck(PLUGIN_HEALTH_CHECK)
                .withHostConfig(hostConfig())) {

            Optional.ofNullable(certsUidGid())
                    .ifPresent(containerCmd::withUser);

            containerResponse = containerCmd.exec();
            dockerClient.startContainerCmd(containerResponse.getId()).exec();
            awaitHealthy(containerResponse.getId());
        }
    }

    private void removeContainerByName(String containerName) {
        try {
            dockerClient.removeContainerCmd(containerName).withForce(true).exec();
            log.info("Removed existing container: {}", containerName);
        } catch (Exception e) {
            // Container doesn't exist or couldn't be removed - that's fine, we'll create a new one
            log.debug("No existing container to remove for {}: {}", containerName, e.getMessage());
        }
    }

    private void upgrade(Container toUpgrade, InstallDetails installDetails) {
        stopAndRemoveContainer(toUpgrade.getId());
        install(installDetails);
    }

    @Override
    Result removePluginResources(String appName) {
        List<Container> containers = findExisting(appName);

        if (isEmpty(containers)) {
            log.warn("No container found with name: {}", appName);
            return Result.builder().success(true).info(List.of("No container was found for " + appName)).build();
        }

        containers.stream().map(Container::getId).forEach(this::stopAndRemoveContainer);
        return new Result();
    }

    private HostConfig hostConfig() {
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withNetworkMode("deltafi")
                .withRestartPolicy(RestartPolicy.unlessStoppedRestart());

        if (StringUtils.isNotBlank(dataDir) && ensureSecretDirExists()) {
            String bindDir = Path.of(dataDir, "certs", sslSecretNames.pluginsSsl()).toString();
            hostConfig.withBinds(new Bind(bindDir, new Volume("/certs")));
        }

        return hostConfig;
    }

    private String ensureImageExists(String image) {
        List<Image> images = findImage(image);

        if (images.isEmpty()) {
            images = Optional.ofNullable(removeDomain(image)).map(this::findImage).orElse(images);
        }

        // if the image was not found locally attempt to pull it
        if (images.isEmpty()) {
            return pullImage(image);
        }

        return null;
    }

    private String pullImage(String image) {
        try {
            boolean completed = dockerClient.pullImageCmd(image)
                    .exec(new PullImageResultCallback())
                    .awaitCompletion(IMAGE_PULL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                return "Image pull timed out: " + image;
            }
            // Verify the image now exists (async errors may not throw)
            if (findImage(image).isEmpty()) {
                return "Image not found after pull (check registry/tag): " + image;
            }
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Interrupted while pulling image: " + image;
        } catch (Exception e) {
            return extractPullError(image, e);
        }
    }

    private String extractPullError(String image, Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return "Failed to pull image: " + image;
        }
        // Look for common Docker error patterns
        if (message.contains("manifest unknown")) {
            return "Image tag not found: " + image;
        }
        if (message.contains("unauthorized") || message.contains("403")) {
            return "Unauthorized to pull image (check credentials): " + image;
        }
        if (message.contains("not found") || message.contains("404")) {
            return "Image not found: " + image;
        }
        // For other errors, try to extract the meaningful part
        int jsonStart = message.indexOf("{\"message\":");
        if (jsonStart != -1) {
            int msgStart = message.indexOf("\"message\":\"", jsonStart) + 11;
            int msgEnd = message.indexOf("\"", msgStart);
            if (msgEnd > msgStart) {
                return "Failed to pull " + image + ": " + message.substring(msgStart, msgEnd);
            }
        }
        return "Failed to pull " + image + ": " + message;
    }

    private List<Image> findImage(String name) {
        return dockerClient.listImagesCmd().withReferenceFilter(name).exec();
    }

    private boolean isEmpty(List<Container> containers) {
        return containers == null || containers.isEmpty();
    }

    void stopAndRemoveContainer(String containerId) {
        try {
            dockerClient.stopContainerCmd(containerId).exec();
        } catch (Exception e) {
            log.warn("Failed to stop container {}: {}", containerId, e.getMessage());
        }
        try {
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
        } catch (Exception e) {
            log.error("Failed to remove container {}: {}", containerId, e.getMessage());
        }
    }

    private List<Container> findExisting(String appName) {
        return dockerClient.listContainersCmd()
                .withShowAll(true)
                .withNameFilter(Set.of("^/" + appName + "$"))
                .exec();
    }

    private List<Container> findByLabel(String appName) {
        return dockerClient.listContainersCmd()
                .withShowAll(true)
                .withLabelFilter(Map.of(APP_LABEL_KEY, appName, DELTAFI_GROUP, getGroupName(appName)))
                .exec();
    }

    private boolean awaitHealthy(String containerId) throws UnhealthyContainer {
        long timeoutSeconds = deltaFiPropertiesService.getDeltaFiProperties().getPluginDeployTimeout().toSeconds();
        for (int i = 0; i < timeoutSeconds; i++) {
            if (isHealthy(containerId)) {
                return true;
            }
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new UnhealthyContainer("Interrupted while waiting for the container to reach a healthy state");
            }
        }

        throw new UnhealthyContainer("Container did not reach healthy state");
    }

    private boolean isHealthy(String containerId) {
        InspectContainerResponse inspect = dockerClient.inspectContainerCmd(containerId).exec();
        return Optional.ofNullable(inspect.getState())
                .map(ContainerState::getHealth)
                .map(HealthState::getStatus)
                .map("healthy"::equals)
                .orElse(true);
    }

    // remove the domain portion of the image i.e. docker.io/deltafi/deltafi-passthrough:tag becomes deltafi/deltafi-passthrough:tag
    String removeDomain(String image) {
        int lastSlash = image.lastIndexOf("/");
        if (lastSlash != -1) {
            lastSlash = image.substring(0, lastSlash).lastIndexOf("/");
            if (lastSlash != -1) {
                return image.substring(lastSlash + 1);
            }
        }
        return null;
    }

    private boolean ensureSecretDirExists() {
        Path pluginSecret = Path.of("/certs", sslSecretNames.pluginsSsl());
        try {
            if (!Files.exists(pluginSecret)) {
                Files.createDirectory(pluginSecret);
            }
            return true;
        } catch (IOException e) {
            log.error("Cannot create directory for plugin secrets", e);
            return false;
        }
    }

    private static class UnhealthyContainer extends RuntimeException {
        public UnhealthyContainer(String message) {
            super(message);
        }
    }

    private String certsUidGid() {
        Path path = Paths.get("/certs");

        if (!Files.exists(path)) {
            return null;
        }
        try {
            int uid = (int) Files.getAttribute(path, "unix:uid", java.nio.file.LinkOption.NOFOLLOW_LINKS);
            int gid = (int) Files.getAttribute(path, "unix:gid", java.nio.file.LinkOption.NOFOLLOW_LINKS);
            return uid + ":" + gid;
        } catch (IOException e) {
            log.error("Could not determine the UID/GID of the certs directory", e);
            return null;
        }
    }

    @Override
    public boolean isPluginRunning(String imageName) {
        if (imageName == null) {
            return false;
        }
        InstallDetails installDetails = InstallDetails.from(imageName);
        List<Container> containers = findExisting(installDetails.appName());
        if (isEmpty(containers)) {
            return false;
        }
        // Check if any container is actually running (not just exists)
        return containers.stream().anyMatch(container -> "running".equalsIgnoreCase(container.getState()));
    }

    @Override
    public void removePlugin(String imageName) {
        if (imageName == null) {
            return;
        }
        InstallDetails installDetails = InstallDetails.from(imageName);
        List<Container> containers = findExisting(installDetails.appName());
        containers.stream().map(Container::getId).forEach(this::stopAndRemoveContainer);
    }
}
