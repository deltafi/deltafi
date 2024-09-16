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
package org.deltafi.core.plugin.deployer.image;

import lombok.AllArgsConstructor;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.deltafi.core.types.snapshot.SnapshotRestoreOrder;
import org.deltafi.core.services.Snapshotter;
import org.deltafi.core.types.snapshot.SystemSnapshot;
import org.deltafi.core.types.Result;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class PluginImageRepositoryService implements Snapshotter {

    private final DeltaFiPropertiesService deltaFiPropertiesService;
    private final PluginImageRepositoryRepo imageRepositoryRepo;

    /**
     * Get all repositories storing plugin images
     * @return list of plugin image repositories
     */
    public List<PluginImageRepository> getPluginImageRepositories() {
        return imageRepositoryRepo.findAll();
    }

    /**
     * Add the plugin image repository config item
     * @param pluginImageRepository image repository used for group(s) of plugins
     * @return copy of the saved item
     */
    public PluginImageRepository savePluginImageRepository(PluginImageRepository pluginImageRepository) {
        if (imageRepositoryRepo.otherExistsByAnyGroupId(pluginImageRepository.getImageRepositoryBase(), String.join(",", pluginImageRepository.getPluginGroupIds()))) {
            throw new RuntimeException("At least one group ID already exists in another image repository base.");
        }
        return imageRepositoryRepo.save(pluginImageRepository);
    }

    /**
     * Remove the plugin image repository with the given id
     * @param id of the image repository to remove
     * @return result of the operation
     */
    public Result removePluginImageRepository(String id) {
        if (imageRepositoryRepo.existsById(id)) {
            imageRepositoryRepo.deleteById(id);
            return new Result();
        } else {
            return Result.builder().success(false).errors(List.of("No plugin image repository config exists with an id of " + id)).build();
        }
    }

    /**
     * Find image repository using the groupId of the given pluginCoordinates
     * @param pluginCoordinates plugin coordinates containing the groupId to use
     * @return image repository used to install the given plugin
     */
    public PluginImageRepository findByGroupId(PluginCoordinates pluginCoordinates) {
        return imageRepositoryRepo.findByPluginGroupId(pluginCoordinates.getGroupId())
                .orElseGet(this::defaultPluginImageRepository);
    }

    @Override
    public void updateSnapshot(SystemSnapshot systemSnapshot) {
        systemSnapshot.setPluginImageRepositories(getPluginImageRepositories());
    }

    @Override
    public Result resetFromSnapshot(SystemSnapshot systemSnapshot, boolean hardReset) {
        if (hardReset) {
            imageRepositoryRepo.deleteAll();
        }

        imageRepositoryRepo.saveAll(systemSnapshot.getPluginImageRepositories());
        return new Result();
    }

    @Override
    public int getOrder() {
        return SnapshotRestoreOrder.PLUGIN_IMAGE_REPO_ORDER;
    }

    private PluginImageRepository defaultPluginImageRepository() {
        PluginImageRepository pluginImageRepository = new PluginImageRepository();
        DeltaFiProperties deltaFiProperties = deltaFiPropertiesService.getDeltaFiProperties();

        pluginImageRepository.setPluginGroupIds(List.of("SYSTEM_DEFAULT"));
        pluginImageRepository.setImageRepositoryBase(deltaFiProperties.getPluginImageRepositoryBase());
        pluginImageRepository.setImagePullSecret(deltaFiProperties.getPluginImagePullSecret());

        return pluginImageRepository;
    }
}
