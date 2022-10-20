/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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

import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.plugin.deployer.customization.PluginCustomizationConfig;
import org.deltafi.core.plugin.deployer.image.PluginImageRepository;
import org.deltafi.core.types.Result;

import java.util.List;

public interface DeployerService {

    /**
     * Install the plugin with given coordinates
     * @param pluginCoordinates of the plugin to install
     * @return result of the operation
     */
    Result installOrUpgradePlugin(PluginCoordinates pluginCoordinates);

    /**
     * Install the plugin with custom settings
     * @param pluginCoordinates of the plugin to install
     * @param imageRepoOverride docker repository to use for this install
     * @param imagePullSecretOverride secret used to pull from that image repository
     * @param customDeploymentOverride yaml holding deployment customizations to apply to this deployment
     * @return
     */
    Result installOrUpgradePlugin(PluginCoordinates pluginCoordinates, String imageRepoOverride, String imagePullSecretOverride, String customDeploymentOverride);

    /**
     * Uninstall the plugin with the given coordinates
     * @param pluginCoordinates coordinates of the plugin to uninstall
     * @return result of the uninstall process
     */
    Result uninstallPlugin(PluginCoordinates pluginCoordinates);

    /**
     * Get all repositories storing plugin images
     * @return list of plugin image repositories
     */
    List<PluginImageRepository> getPluginImageRepositories();

    /**
     * Get all plugin customization configs
     * @return list of plugin customization configs
     */
    List<PluginCustomizationConfig> getPluginCustomizationConfigs();

    /**
     * Add the plugin image repository config item
     * @param pluginImageRepository image repository used for group(s) of plugins
     * @return copy of the saved item
     */
    PluginImageRepository savePluginImageRepository(PluginImageRepository pluginImageRepository);

    /**
     * Add the plugin customization config item
     * @param pluginCustomizationConfigInput plugin customization config to save
     * @return copy of the saved item
     */
    PluginCustomizationConfig savePluginCustomizationConfig(PluginCustomizationConfig pluginCustomizationConfigInput);

    /**
     * Remove the plugin image repository with the given id
     * @param id of the image repository to remove
     * @return result of the operation
     */
    Result removePluginImageRepository(String id);

    /**
     * Remove the plugin customization config with the given id
     * @param id of the plugin customization config to remove
     * @return result of the operation
     */
    Result removePluginCustomizationConfig(String id);
}
