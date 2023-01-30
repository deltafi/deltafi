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

import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.plugin.PluginRegistryService;
import org.deltafi.core.plugin.deployer.customization.PluginCustomizationConfig;
import org.deltafi.core.plugin.deployer.customization.PluginCustomizationService;
import org.deltafi.core.plugin.deployer.image.PluginImageRepository;
import org.deltafi.core.plugin.deployer.image.PluginImageRepositoryRepo;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.deltafi.core.services.EventService;
import org.deltafi.core.services.api.model.Event;
import org.deltafi.core.snapshot.SystemSnapshotService;
import org.deltafi.core.types.Result;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

import java.util.List;

@Slf4j
public abstract class BaseDeployerService implements DeployerService {

    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final PluginImageRepositoryRepo imageRepositoryRepo;
    private final PluginRegistryService pluginRegistryService;
    final PluginCustomizationService pluginCustomizationService;
    private final SystemSnapshotService systemSnapshotService;
    private final EventService eventService;

    public BaseDeployerService(DeltaFiPropertiesService deltaFiPropertiesService, PluginImageRepositoryRepo imageRepositoryRepo, PluginRegistryService pluginRegistryService, PluginCustomizationService pluginCustomizationService, SystemSnapshotService systemSnapshotService, EventService eventService) {
        this.deltaFiPropertiesService = deltaFiPropertiesService;
        this.imageRepositoryRepo = imageRepositoryRepo;
        this.pluginRegistryService = pluginRegistryService;
        this.pluginCustomizationService = pluginCustomizationService;
        this.systemSnapshotService = systemSnapshotService;
        this.eventService = eventService;
    }

    @Override
    public Result installOrUpgradePlugin(PluginCoordinates pluginCoordinates, String imageRepoOverride, String imagePullSecretOverride, String customDeploymentOverride) {
        systemSnapshotService.createSnapshot(preUpgradeMessage(pluginCoordinates));

        Result retval = deploy(pluginCoordinates, imageRepoOverride, imagePullSecretOverride, customDeploymentOverride);

        eventService.publishEvent(
                Event.builder("Plugin installed: " + pluginCoordinates.getArtifactId() + ":" + pluginCoordinates.getVersion())
                        .notification(true)
                        .severity(retval.isSuccess() ? Event.Severity.SUCCESS : Event.Severity.ERROR)
                        .content(pluginCoordinates.toString() + "\\n\\n")
                        .addList("Additional information:", retval.getInfo())
                        .addList("Errors:", retval.getErrors())
                        .build()
        );

        return retval;
    }

    @Override
    public Result uninstallPlugin(PluginCoordinates pluginCoordinates) {
        List<String> uninstallPreCheckErrors = pluginRegistryService.canBeUninstalled(pluginCoordinates);
        if (!uninstallPreCheckErrors.isEmpty()) {
            return Result.newBuilder().success(false).errors(uninstallPreCheckErrors).build();
        }

        pluginRegistryService.uninstallPlugin(pluginCoordinates);
        Result retval = removeDeployment(pluginCoordinates);

        eventService.publishEvent(
                Event.builder("Plugin uninstalled: " + pluginCoordinates.getArtifactId() + ":" + pluginCoordinates.getVersion())
                        .notification(true)
                        .severity(retval.isSuccess() ? Event.Severity.SUCCESS : Event.Severity.ERROR)
                        .content(pluginCoordinates.toString() + "\\n\\n")
                        .addList("Additional information:", retval.getInfo())
                        .addList("Errors:", retval.getErrors())
                        .build()
        );

        return retval;
    }

    abstract Result deploy(PluginCoordinates pluginCoordinates, String imageRepoOverride, String imagePullSecretOverride, String customDeploymentOverride);

    abstract Result removeDeployment(PluginCoordinates pluginCoordinates);

    @Override
    public List<PluginImageRepository> getPluginImageRepositories() {
        List<PluginImageRepository> pluginImageRepositories = imageRepositoryRepo.findAll();
        pluginImageRepositories.add(defaultPluginImageRepository());
        return pluginImageRepositories;
    }

    @Override
    public List<PluginCustomizationConfig> getPluginCustomizationConfigs() {
        return pluginCustomizationService.getAllConfiguration();
    }

    @Override
    public PluginImageRepository savePluginImageRepository(PluginImageRepository pluginImageRepository) {
        return imageRepositoryRepo.save(pluginImageRepository);
    }

    @Override
    public Result removePluginImageRepository(String id) {
        if (imageRepositoryRepo.existsById(id)) {
            imageRepositoryRepo.deleteById(id);
            return new Result();
        } else {
            return Result.newBuilder().success(false).errors(List.of("No plugin image repository config exists with an id of " + id)).build();
        }
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

    PluginImageRepository findByGroupId(PluginCoordinates pluginCoordinates) {
        return imageRepositoryRepo.findByPluginGroupIds(pluginCoordinates.getGroupId())
                .orElseGet(this::defaultPluginImageRepository);
    }

    private PluginImageRepository defaultPluginImageRepository() {
        PluginImageRepository pluginImageRepository = new PluginImageRepository();
        DeltaFiProperties deltaFiProperties = deltaFiPropertiesService.getDeltaFiProperties();
        if (deltaFiProperties.getPlugins() != null) {
            pluginImageRepository.setPluginGroupIds(List.of("SYSTEM_DEFAULT"));
            pluginImageRepository.setImageRepositoryBase(deltaFiProperties.getPlugins().getImageRepositoryBase());
            pluginImageRepository.setImagePullSecret(deltaFiProperties.getPlugins().getImagePullSecret());
        }
        return pluginImageRepository;
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
