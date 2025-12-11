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
    private final CoreAuditLogger auditLogger;
    private final FlowValidationService flowValidationService;

    @DgsQuery
    @NeedsPermission.PluginsView
    public Collection<Plugin> plugins() {
        return pluginService.getPluginsWithVariables().stream().map(PluginEntity::toPlugin).toList();
    }

    @DgsMutation
    @NeedsPermission.PluginInstall
    public Result installPlugin(@InputArgument String image, @InputArgument String imagePullSecret) {
        auditLogger.audit("queued plugin install {}", image);
        pluginService.createPendingPlugin(image, imagePullSecret);
        return Result.builder()
                .success(true)
                .info(List.of("Plugin queued for installation."))
                .build();
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

    @DgsMutation
    @NeedsPermission.PluginInstall
    public boolean retryPluginInstall(@InputArgument PluginCoordinates pluginCoordinates) {
        auditLogger.audit("retried plugin install {}", pluginCoordinates);
        return pluginService.requestRetry(pluginCoordinates);
    }

    @DgsMutation
    @NeedsPermission.PluginInstall
    public boolean rollbackPlugin(@InputArgument PluginCoordinates pluginCoordinates) {
        auditLogger.audit("rolled back plugin {}", pluginCoordinates);
        return pluginService.rollbackPlugin(pluginCoordinates);
    }

    @DgsMutation
    @NeedsPermission.PluginUninstall
    public boolean disablePlugin(@InputArgument PluginCoordinates pluginCoordinates) {
        auditLogger.audit("disabled plugin {}", pluginCoordinates);
        boolean disabled = pluginService.disablePlugin(pluginCoordinates);
        if (disabled) {
            flowValidationService.revalidateFlowsForPlugin(pluginCoordinates);
        }
        return disabled;
    }

    @DgsMutation
    @NeedsPermission.PluginInstall
    public boolean enablePlugin(@InputArgument PluginCoordinates pluginCoordinates) {
        auditLogger.audit("enabled plugin {}", pluginCoordinates);
        return pluginService.enablePlugin(pluginCoordinates);
    }

    @DgsQuery
    @NeedsPermission.PluginsView
    public PluginService.PluginStateSummary pluginInstallStatus() {
        return pluginService.getInstallSummary();
    }
}
