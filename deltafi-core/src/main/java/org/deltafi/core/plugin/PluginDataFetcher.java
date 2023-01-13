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
import org.deltafi.core.security.NeedsPermission;
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
    @NeedsPermission.PluginsView
    public Collection<Plugin> plugins() {
        return pluginRegistryService.getPluginsWithVariables();
    }

    @DgsQuery
    @NeedsPermission.PluginsView
    public boolean verifyActionsAreRegistered(PluginCoordinates pluginCoordinates) {
        return pluginRegistryService.verifyActionsAreRegistered(pluginCoordinates);
    }

    @DgsQuery
    @NeedsPermission.PluginImageRepoView
    public List<PluginImageRepository> getPluginImageRepositories() {
        return deployerService.getPluginImageRepositories();
    }

    @DgsQuery
    @NeedsPermission.PluginCustomizationConfigView
    public List<PluginCustomizationConfig> getPluginCustomizationConfigs() {
        return deployerService.getPluginCustomizationConfigs();
    }

    @DgsMutation
    @NeedsPermission.PluginImageRepoWrite
    public PluginImageRepository savePluginImageRepository(PluginImageRepository pluginImageRepository) {
        return deployerService.savePluginImageRepository(pluginImageRepository);
    }

    @DgsMutation
    @NeedsPermission.PluginCustomizationConfigWrite
    public PluginCustomizationConfig savePluginCustomizationConfig(PluginCustomizationConfig pluginCustomizationConfigInput) {
        return deployerService.savePluginCustomizationConfig(pluginCustomizationConfigInput);
    }

    @DgsMutation
    @NeedsPermission.PluginInstall
    public Result installPlugin(PluginCoordinates pluginCoordinates) {
        return deployerService.installOrUpgradePlugin(pluginCoordinates, null, null, null);
    }

    @DgsMutation
    @NeedsPermission.PluginInstall
    public Result installPluginWithSettings(PluginCoordinates pluginCoordinates, String imageRepositoryOverride, String imagePullSecretOverride, String customDeploymentYaml) {
        return deployerService.installOrUpgradePlugin(pluginCoordinates, imageRepositoryOverride, imagePullSecretOverride, customDeploymentYaml);
    }

    @DgsMutation
    @NeedsPermission.PluginUninstall
    public Result uninstallPlugin(PluginCoordinates pluginCoordinates) {
        return deployerService.uninstallPlugin(pluginCoordinates);
    }

    @DgsMutation
    @NeedsPermission.PluginCustomizationConfigWrite
    public Result addBasicCredential(String sourceName, String username, String password) {
        return credentialProvider.createCredentials(sourceName, username, password);
    }

    @DgsMutation
    @NeedsPermission.PluginImageRepoDelete
    public Result removePluginImageRepository(String id) {
        return deployerService.removePluginImageRepository(id);
    }

    @DgsMutation
    @NeedsPermission.PluginCustomizationConfigDelete
    public Result removePluginCustomizationConfig(String id) {
        return deployerService.removePluginCustomizationConfig(id);
    }

}
