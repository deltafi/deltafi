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

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ListImagesCmd;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Image;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.plugin.PluginRegistryService;
import org.deltafi.core.plugin.deployer.customization.PluginCustomization;
import org.deltafi.core.plugin.deployer.customization.PluginCustomizationService;
import org.deltafi.core.plugin.deployer.image.PluginImageRepository;
import org.deltafi.core.plugin.deployer.image.PluginImageRepositoryService;
import org.deltafi.core.services.EventService;
import org.deltafi.core.snapshot.SystemSnapshotService;
import org.deltafi.core.types.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.deltafi.core.services.DockerAppInfoService.DELTAFI_GROUP;

@Slf4j
public class DockerDeployerService extends BaseDeployerService implements DeployerService {

    public static final int IMAGE_PULL_TIMEOUT_SECONDS = 60;
    private final DockerClient dockerClient;

    private final List<String> environmentVariables;

    public DockerDeployerService(DockerClient dockerClient, PluginImageRepositoryService pluginImageRepositoryService, PluginRegistryService pluginRegistryService, PluginCustomizationService pluginCustomizationService, SystemSnapshotService systemSnapshotService, EventService eventService, EnvironmentVariableHelper environmentVariableHelper) {
        super(pluginImageRepositoryService, pluginRegistryService, pluginCustomizationService, systemSnapshotService, eventService);
        this.dockerClient = dockerClient;
        this.environmentVariables = environmentVariableHelper.getEnvVars();
    }

    @Override
    DeployResult deploy(PluginCoordinates pluginCoordinates, PluginImageRepository pluginImageRepository, PluginCustomization pluginCustomization, ArrayList<String> info) {
        try {
            String image = pluginImageRepository.getImageRepositoryBase() + pluginCoordinates.getArtifactId() + ":" + pluginCoordinates.getVersion();

            ListImagesCmd listImagesCmd = dockerClient.listImagesCmd();
            listImagesCmd.getFilters().put("reference", List.of(image));
            List<Image> images = listImagesCmd.exec();

            if (images.isEmpty()) {
                boolean pulled = dockerClient.pullImageCmd(image).exec(new PullImageResultCallback()).awaitCompletion(IMAGE_PULL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!pulled) {
                    return DeployResult.builder().success(false).info(info).errors(List.of("Could not pull image: " + image)).build();
                }
            }

            Map<String, String> containerLabels = Map.of(DELTAFI_GROUP, "deltafi-plugins", "logging", "promtail", "logging_jobname", "containerlogs");

            CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image)
                    .withName(pluginCoordinates.getArtifactId())
                    .withEnv(environmentVariables)
                    .withLabels(containerLabels)
                    .withHostConfig(HostConfig.newHostConfig().withNetworkMode("deltafi"));

            CreateContainerResponse containerResponse = containerCmd.exec();
            dockerClient.startContainerCmd(containerResponse.getId()).exec();
        } catch (Exception e) {
            log.error("Failed to start plugin", e);
            return DeployResult.builder().success(false).info(info).errors(List.of(e.getMessage())).build();
        }

        return new DeployResult();
    }

    @Override
    Result removePluginResources(PluginCoordinates pluginCoordinates) {
        List<Container> containers = dockerClient.listContainersCmd()
                .withShowAll(true)
                .withNameFilter(Set.of(pluginCoordinates.getArtifactId()))
                .exec();

        if (containers == null || containers.isEmpty()) {
            return Result.newBuilder().success(false).errors(List.of("No container was found for " + pluginCoordinates.groupAndArtifact())).build();
        }

        containers.forEach(this::stopAndRemoveContainer);
        return new Result();
    }

    void stopAndRemoveContainer(Container container) {
        dockerClient.stopContainerCmd(container.getId()).exec();
        dockerClient.removeContainerCmd(container.getId()).exec();
    }
}
