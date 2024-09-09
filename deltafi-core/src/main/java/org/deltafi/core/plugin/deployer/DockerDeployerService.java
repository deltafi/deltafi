/*
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

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.command.InspectContainerResponse.ContainerState;
import com.github.dockerjava.api.model.*;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.plugin.PluginRegistryService;
import org.deltafi.core.plugin.deployer.image.PluginImageRepository;
import org.deltafi.core.plugin.deployer.image.PluginImageRepositoryService;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.deltafi.core.services.EventService;
import org.deltafi.core.services.SystemSnapshotService;
import org.deltafi.core.types.Result;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.deltafi.core.services.DockerPlatformService.DELTAFI_GROUP;

@Slf4j
public class DockerDeployerService extends BaseDeployerService implements DeployerService {

    public static final int IMAGE_PULL_TIMEOUT_SECONDS = 60;

    // the times are all in nanoseconds
    private static final HealthCheck PLUGIN_HEALTH_CHECK = new HealthCheck()
                .withInterval(1_000_000_000L)
                .withRetries(30)
                .withTest(List.of("CMD-SHELL", "cat /tmp/running"))
                .withStartPeriod(5_000_000_000L)
                .withTimeout(1_000_000_000L);

    private static final HostConfig PLUGIN_HOST_CONFIG = HostConfig.newHostConfig()
            .withNetworkMode("deltafi")
            .withRestartPolicy(RestartPolicy.unlessStoppedRestart());

    private final DockerClient dockerClient;

    private final List<String> environmentVariables;
    private final DeltaFiPropertiesService deltaFiPropertiesService;

    public DockerDeployerService(DockerClient dockerClient, PluginImageRepositoryService pluginImageRepositoryService, PluginRegistryService pluginRegistryService,
                                 SystemSnapshotService systemSnapshotService, EventService eventService,
                                 EnvironmentVariableHelper environmentVariableHelper, DeltaFiPropertiesService deltaFiPropertiesService) {
        super(pluginImageRepositoryService, pluginRegistryService, systemSnapshotService, eventService);
        this.dockerClient = dockerClient;
        this.environmentVariables = environmentVariableHelper.getEnvVars();
        this.deltaFiPropertiesService = deltaFiPropertiesService;
    }

    @Override
    DeployResult deploy(PluginCoordinates pluginCoordinates, PluginImageRepository pluginImageRepository, ArrayList<String> info) {
        List<Container> existing;
        try {
            existing = findExisting(pluginCoordinates);
            if (existing.size() > 1) {
                return DeployResult.builder().success(false).info(info).errors(List.of("Multiple containers found for this plugin")).build();
            }
        } catch (Exception e) {
            log.error("Failed to start plugin", e);
            return DeployResult.builder().success(false).info(info).errors(List.of("Failed to list running plugins " + e.getMessage())).build();
        }

        String imageName = pluginImageRepository.getImageRepositoryBase() + pluginCoordinates.getArtifactId() + ":" + pluginCoordinates.getVersion();
        String imageCheckError = ensureImageExists(imageName);
        if (imageCheckError != null) {
            return DeployResult.builder().success(false).info(info).errors(List.of(imageCheckError)).build();
        }

        try {
            if (isEmpty(existing)) {
                install(pluginCoordinates, imageName);
            } else {
                upgrade(existing.getFirst(), pluginCoordinates, imageName);
            }

            return new DeployResult();
        } catch (Exception e) {
            log.error("Failed to install the  plugin with image: {}", imageName, e);
            return DeployResult.builder().success(false).info(info).errors(List.of(e.getMessage())).build();
        }
    }

    private void install(PluginCoordinates pluginCoordinates, String image) {
        Map<String, String> containerLabels = Map.of(DELTAFI_GROUP, "deltafi-plugins", "logging", "promtail", "logging_jobname", "containerlogs");

        CreateContainerResponse containerResponse = null;
        try (CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image)
                .withName(pluginCoordinates.getArtifactId())
                .withEnv(environmentVariables)
                .withLabels(containerLabels)
                .withHealthcheck(PLUGIN_HEALTH_CHECK)
                .withHostConfig(PLUGIN_HOST_CONFIG)) {

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

    private void upgrade(Container toUpgrade, PluginCoordinates pluginCoordinates, String imageName) {
        String originalName = toUpgrade.getNames().length > 0 ? toUpgrade.getNames()[0] : toUpgrade.getId();
        String newName = originalName + "-upgrading";
        dockerClient.renameContainerCmd(toUpgrade.getId()).withName(newName).exec();
        try {
            install(pluginCoordinates, imageName);
            stopAndRemoveContainer(toUpgrade.getId());
        } catch (Exception e) {
            if (deltaFiPropertiesService.getDeltaFiProperties().isPluginAutoRollback()) {
                dockerClient.renameContainerCmd(toUpgrade.getId()).withName(originalName).exec();
            }
            throw e;
        }
    }

    @Override
    Result removePluginResources(PluginCoordinates pluginCoordinates) {
        List<Container> containers = findExisting(pluginCoordinates);

        if (isEmpty(containers)) {
            return Result.builder().success(false).errors(List.of("No container was found for " + pluginCoordinates.groupAndArtifact())).build();
        }

        containers.stream().map(Container::getId).forEach(this::stopAndRemoveContainer);
        return new Result();
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

    private List<Container> findExisting(PluginCoordinates pluginCoordinates) {
        return dockerClient.listContainersCmd()
                .withShowAll(true)
                .withNameFilter(Set.of(pluginCoordinates.getArtifactId()))
                .exec();
    }

    private void awaitHealthy(String containerId) throws UnhealthyContainer {
        // wait up to 30 seconds for the health check to pass
        for (int i = 0; i < 30; i++) {
            if (isHealthy(containerId)) {
                return;
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
