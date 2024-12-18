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
package org.deltafi.core.plugin.deployer;

import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.types.Result;

import java.util.Set;

public interface DeployerService {

    /**
     * Install the plugin using the given image and imagePullSecret
     * @param image used to install the plugin
     * @param imagePullSecret optional - name of the secret holding the credentials needed to pull the image
     * @return result of the installation or upgrade process
     */
    Result installOrUpgradePlugin(String image, String imagePullSecret);

    /**
     * Uninstall the plugin with the given coordinates
     * @param pluginCoordinates coordinates of the plugin to uninstall
     * @param force uninstall even if there are blockers
     * @return result of the uninstallation process
     */
    Result uninstallPlugin(PluginCoordinates pluginCoordinates, boolean force);

    /**
     * Restart or recreate the plugins with the given names
     * @param plugins name of the plugins to bounce
     */
    default void restartPlugins(Set<String> plugins) {
        plugins.forEach(this::restartPlugin);
    }

    /**
     * Restart or recreate the plugin with the given names
     * @param plugin name of the plugin to bounce
     */
    void restartPlugin(String plugin);
}
