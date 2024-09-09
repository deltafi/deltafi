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
package org.deltafi.core.plugin;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import lombok.RequiredArgsConstructor;
import org.deltafi.common.types.ActionDescriptor;
import org.deltafi.common.types.Plugin;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.plugin.deployer.DeployerService;
import org.deltafi.core.plugin.deployer.credential.CredentialProvider;
import org.deltafi.core.plugin.deployer.image.PluginImageRepository;
import org.deltafi.core.plugin.deployer.image.PluginImageRepositoryService;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.types.Result;

import java.util.Collection;
import java.util.List;

@DgsComponent
@RequiredArgsConstructor
public class PluginDataFetcher {
    private final PluginRegistryService pluginRegistryService;
    private final PluginImageRepositoryService pluginImageRepositoryService;
    private final DeployerService deployerService;
    private final CredentialProvider credentialProvider;
    private final CoreAuditLogger auditLogger;

    @DgsQuery
    @NeedsPermission.PluginsView
    public Collection<Plugin> plugins() {
        return pluginRegistryService.getPluginsWithVariables().stream().map(PluginEntity::toPlugin).toList();
    }

    @DgsQuery
    @NeedsPermission.PluginImageRepoView
    public List<PluginImageRepository> getPluginImageRepositories() {
        return pluginImageRepositoryService.getPluginImageRepositories();
    }

    @DgsMutation
    @NeedsPermission.PluginImageRepoWrite
    public PluginImageRepository savePluginImageRepository(@InputArgument PluginImageRepository pluginImageRepository) {
        auditLogger.audit("saved plugin image repository {}", pluginImageRepository);
        return pluginImageRepositoryService.savePluginImageRepository(pluginImageRepository);
    }

    @DgsMutation
    @NeedsPermission.PluginInstall
    public Result installPlugin(@InputArgument PluginCoordinates pluginCoordinates) {
        auditLogger.audit("installed plugin {}", pluginCoordinates);
        return deployerService.installOrUpgradePlugin(pluginCoordinates, null, null, null);
    }

    @DgsMutation
    @NeedsPermission.PluginInstall
    public Result installPluginWithSettings(@InputArgument PluginCoordinates pluginCoordinates, @InputArgument String imageRepositoryOverride, @InputArgument String imagePullSecretOverride, @InputArgument String customDeploymentYaml) {
        auditLogger.audit("installed plugin {} with image repo {}, pull secret name {}, and customization of {}", pluginCoordinates, imageRepositoryOverride, imagePullSecretOverride, customDeploymentYaml);
        return deployerService.installOrUpgradePlugin(pluginCoordinates, imageRepositoryOverride, imagePullSecretOverride, customDeploymentYaml);
    }

    @DgsMutation
    @NeedsPermission.PluginUninstall
    public Result uninstallPlugin(@InputArgument PluginCoordinates pluginCoordinates) {
        auditLogger.audit("uninstalled plugin {}", pluginCoordinates);
        return deployerService.uninstallPlugin(pluginCoordinates);
    }

    @DgsMutation
    @NeedsPermission.PluginImageRepoDelete
    public Result removePluginImageRepository(@InputArgument String id) {
        auditLogger.audit("removed plugin image repository {}", id);
        return pluginImageRepositoryService.removePluginImageRepository(id);
    }

    @DgsQuery
    @NeedsPermission.PluginsView
    public Collection<ActionDescriptor> actionDescriptors() {
        return pluginRegistryService.getActionDescriptors();
    }

}
