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

import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.types.Result;

public interface DeployerService {

    /**
     * Install the plugin with custom settings
     * @param pluginCoordinates of the plugin to install
     * @param imageRepoOverride docker repository to use for this install
     * @param imagePullSecretOverride secret used to pull from that image repository
     * @param customDeploymentOverride yaml holding deployment customizations to apply to this deployment
     * @return result of the installation or upgrade process
     */
    Result installOrUpgradePlugin(PluginCoordinates pluginCoordinates, String imageRepoOverride, String imagePullSecretOverride, String customDeploymentOverride);

    /**
     * Uninstall the plugin with the given coordinates
     * @param pluginCoordinates coordinates of the plugin to uninstall
     * @return result of the uninstallation process
     */
    Result uninstallPlugin(PluginCoordinates pluginCoordinates);
}
