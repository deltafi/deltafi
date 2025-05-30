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

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.command.InspectContainerResponse.ContainerState;
import com.github.dockerjava.api.model.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.deltafi.core.services.EventService;
import org.deltafi.core.services.PluginService;
import org.deltafi.core.services.SystemSnapshotService;
import org.deltafi.core.types.Result;

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

    private final List<String> environmentVariables;
    private final String dataDir;

    public DockerDeployerService(DockerClient dockerClient,PluginService pluginService,
                                 EventService eventService, EnvironmentVariableHelper environmentVariableHelper,
                                 DeltaFiPropertiesService deltaFiPropertiesService) {
        super(pluginService, eventService, deltaFiPropertiesService);
        this.dockerClient = dockerClient;
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
                install(installDetails);
            } else {
                upgrade(existing.getFirst(), installDetails);
            }

            return new DeployResult();
        } catch (Exception e) {
            log.error("Failed to install the  plugin with image: {}", installDetails.image(), e);
            return DeployResult.builder().success(false).errors(List.of(e.getMessage())).build();
        }
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
        Map<String, String> containerLabels = Map.of(APP_LABEL_KEY, installDetails.appName(), DELTAFI_GROUP, "deltafi-plugins", "logging", "promtail", "logging_jobname", "containerlogs");

        List<String> envVars = new ArrayList<>(environmentVariables);
        envVars.add(IMAGE + "=" + installDetails.image());
        if (StringUtils.isNotBlank(installDetails.imagePullSecret())) {
            envVars.add(IMAGE_PULL_SECRET + "=" + installDetails.imagePullSecret());
        }
        envVars.add("APP_NAME=" + installDetails.appName());

        CreateContainerResponse containerResponse = null;
        try (CreateContainerCmd containerCmd = dockerClient.createContainerCmd(installDetails.image())
                .withName(installDetails.appName())
                .withEnv(envVars)
                .withLabels(containerLabels)
                .withHealthcheck(PLUGIN_HEALTH_CHECK)
                .withHostConfig(hostConfig())) {

            containerResponse = containerCmd.exec();
            dockerClient.startContainerCmd(containerResponse.getId()).exec();
            awaitHealthy(containerResponse.getId());
        } catch (Exception e) {
            if (containerResponse != null && deltaFiPropertiesService.getDeltaFiProperties().isPluginAutoRollback()) {
                stopAndRemoveContainer(containerResponse.getId());
            }
            throw e;
        }
    }

    private void upgrade(Container toUpgrade, InstallDetails installDetails) {
        String originalName = toUpgrade.getNames().length > 0 ? toUpgrade.getNames()[0] : toUpgrade.getId();
        String newName = originalName + "-upgrading";
        dockerClient.renameContainerCmd(toUpgrade.getId()).withName(newName).exec();
        try {
            install(installDetails);
            stopAndRemoveContainer(toUpgrade.getId());
        } catch (Exception e) {
            if (deltaFiPropertiesService.getDeltaFiProperties().isPluginAutoRollback()) {
                dockerClient.renameContainerCmd(toUpgrade.getId()).withName(originalName).exec();
            }
            throw e;
        }
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

        if (StringUtils.isNotBlank(dataDir)) {
            hostConfig.withBinds(new Bind(dataDir + "/certs", new Volume("/certs")));
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
            boolean pulled;
            try {
                pulled = dockerClient.pullImageCmd(image).exec(new PullImageResultCallback()).awaitCompletion(IMAGE_PULL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "Interrupted while trying to pull the image: " + image;
            }
            if (!pulled) {
                return "Could not pull image within image: " + image;
            }
        }

        return null;
    }

    private List<Image> findImage(String name) {
        return dockerClient.listImagesCmd().withReferenceFilter(name).exec();
    }

    private boolean isEmpty(List<Container> containers) {
        return containers == null || containers.isEmpty();
    }

    void stopAndRemoveContainer(String containerId) {
        dockerClient.stopContainerCmd(containerId).exec();
        dockerClient.removeContainerCmd(containerId).exec();
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
        // wait up to 30 seconds for the health check to pass
        for (int i = 0; i < 30; i++) {
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

    private static class UnhealthyContainer extends RuntimeException {
        public UnhealthyContainer(String message) {
            super(message);
        }
    }

}
