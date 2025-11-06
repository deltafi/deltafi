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
package org.deltafi.core.datafetchers;

import com.netflix.graphql.dgs.*;
import lombok.RequiredArgsConstructor;
import org.deltafi.common.types.*;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.plugin.deployer.DeployerService;
import org.deltafi.core.security.NeedsPermission;
import org.deltafi.core.services.*;
import org.deltafi.core.types.*;

import java.util.Collection;
import java.util.List;

@DgsComponent
@RequiredArgsConstructor
public class PluginDataFetcher {
    private final PluginService pluginService;
    private final DeployerService deployerService;
    private final SystemSnapshotService systemSnapshotService;
    private final CoreAuditLogger auditLogger;

    @DgsQuery
    @NeedsPermission.PluginsView
    public Collection<Plugin> plugins() {
        return pluginService.getPluginsWithVariables().stream().map(PluginEntity::toPlugin).toList();
    }

    @DgsMutation
    @NeedsPermission.PluginInstall
    public Result installPlugin(@InputArgument String image, @InputArgument String imagePullSecret) {
        auditLogger.audit("installed plugin {}", image);
        systemSnapshotService.createSnapshot(preUpgradeMessage(image));
        return deployerService.installOrUpgradePlugin(image, imagePullSecret);
    }

    @DgsMutation
    @NeedsPermission.PluginUninstall
    public Result uninstallPlugin(@InputArgument PluginCoordinates pluginCoordinates) {
        auditLogger.audit("uninstalled plugin {}", pluginCoordinates);
        return deployerService.uninstallPlugin(pluginCoordinates, false);
    }

    @DgsMutation
    @NeedsPermission.Admin
    public Result forcePluginUninstall(@InputArgument PluginCoordinates pluginCoordinates) {
        auditLogger.audit("forced uninstall of plugin {}", pluginCoordinates);
        return deployerService.uninstallPlugin(pluginCoordinates, true);
    }

    @DgsMutation
    @NeedsPermission.Admin
    public boolean restartPlugin(@InputArgument String pluginName, @InputArgument Boolean waitForSuccess) {
        auditLogger.audit("restarted plugin {}", pluginName);
        // waitForSuccess is false by default
        return deployerService.restartPlugin(pluginName, Boolean.TRUE.equals(waitForSuccess));
    }

    @DgsQuery
    @NeedsPermission.PluginsView
    public Collection<ActionDescriptor> actionDescriptors() {
        return pluginService.getActionDescriptors();
    }

    private String preUpgradeMessage(String imageName) {
        String username = DeltaFiUserService.currentUsername();
        String reason = "Deploying plugin from image: " + imageName;
        if (username != null) {
            reason += " triggered by " + username;
        }
        return reason;
    }

    @DgsMutation
    @NeedsPermission.PluginVariableUpdate
    public boolean savePluginVariables(@InputArgument List<Variable> variables) {
        auditLogger.audit("saved plugin variables {}", CoreAuditLogger.listToString(variables, Variable::getName));
        pluginService.saveSystemVariables(variables);
        return true;
    }

    @DgsMutation
    @NeedsPermission.PluginVariableUpdate
    public boolean removePluginVariables() {
        auditLogger.audit("removed system plugin variables");
        pluginService.removeSystemVariables();
        return true;
    }

    @DgsMutation
    @NeedsPermission.PluginVariableUpdate
    public boolean setPluginVariableValues(@InputArgument PluginCoordinates pluginCoordinates, @InputArgument List<KeyValue> variables) {
        VariableUpdate variableUpdate = pluginService.setPluginVariableValues(pluginCoordinates, variables);
        auditLogger.audit("updated plugin variables: {}", CoreAuditLogger.listToString(variableUpdate.getUpdatedVariables(), VariableUpdate.Result::nameAndValue));
        return variableUpdate.isUpdated();
    }
}
