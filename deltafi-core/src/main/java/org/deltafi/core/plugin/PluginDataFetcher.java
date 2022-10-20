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
package org.deltafi.core.plugin;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import lombok.RequiredArgsConstructor;
import org.deltafi.common.types.Plugin;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.plugin.deployer.DeployerService;
import org.deltafi.core.plugin.deployer.credential.CredentialProvider;
import org.deltafi.core.plugin.deployer.customization.PluginCustomizationConfig;
import org.deltafi.core.plugin.deployer.image.PluginImageRepository;
import org.deltafi.core.types.Result;

import java.util.Collection;
import java.util.List;

@DgsComponent
@RequiredArgsConstructor
public class PluginDataFetcher {
    private final PluginRegistryService pluginRegistryService;
    private final DeployerService deployerService;
    private final CredentialProvider credentialProvider;

    @DgsQuery
    public Collection<Plugin> plugins() {
        return pluginRegistryService.getPluginsWithVariables();
    }

    @DgsQuery
    public boolean verifyActionsAreRegistered(PluginCoordinates pluginCoordinates) {
        return pluginRegistryService.verifyActionsAreRegistered(pluginCoordinates);
    }

    @DgsQuery
    public List<PluginImageRepository> getPluginImageRepositories() {
        return deployerService.getPluginImageRepositories();
    }

    @DgsQuery
    public List<PluginCustomizationConfig> getPluginCustomizationConfigs() {
        return deployerService.getPluginCustomizationConfigs();
    }

    @DgsMutation
    public PluginImageRepository savePluginImageRepository(PluginImageRepository pluginImageRepository) {
        return deployerService.savePluginImageRepository(pluginImageRepository);
    }

    @DgsMutation
    public PluginCustomizationConfig savePluginCustomizationConfig(PluginCustomizationConfig pluginCustomizationConfigInput) {
        return deployerService.savePluginCustomizationConfig(pluginCustomizationConfigInput);
    }

    @DgsMutation
    public Result installPlugin(PluginCoordinates pluginCoordinates) {
        return deployerService.installOrUpgradePlugin(pluginCoordinates);
    }

    @DgsMutation
    public Result installPluginWithSettings(PluginCoordinates pluginCoordinates, String imageRepositoryOverride, String imagePullSecretOverride, String customDeploymentYaml) {
        return deployerService.installOrUpgradePlugin(pluginCoordinates, imageRepositoryOverride, imagePullSecretOverride, customDeploymentYaml);
    }

    @DgsMutation
    public Result uninstallPlugin(PluginCoordinates pluginCoordinates) {
        return deployerService.uninstallPlugin(pluginCoordinates);
    }

    @DgsMutation
    public Result addBasicCredential(String sourceName, String username, String password) {
        return credentialProvider.createCredentials(sourceName, username, password);
    }

    @DgsMutation
    public Result removePluginImageRepository(String id) {
        return deployerService.removePluginImageRepository(id);
    }

    @DgsMutation
    public Result removePluginCustomizationConfig(String id) {
        return deployerService.removePluginCustomizationConfig(id);
    }

}
