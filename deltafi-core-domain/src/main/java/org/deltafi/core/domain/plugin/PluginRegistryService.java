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
package org.deltafi.core.domain.plugin;

import lombok.AllArgsConstructor;
import org.deltafi.core.domain.api.types.PluginCoordinates;
import org.deltafi.core.domain.generated.types.Flows;
import org.deltafi.core.domain.generated.types.Result;
import org.deltafi.core.domain.services.*;
import org.deltafi.core.domain.types.EgressFlow;
import org.deltafi.core.domain.types.EnrichFlow;
import org.deltafi.core.domain.types.IngressFlow;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class PluginRegistryService {

    private final IngressFlowService ingressFlowService;
    private final EnrichFlowService enrichFlowService;
    private final EgressFlowService egressFlowService;
    private final PluginVariableService pluginVariableService;
    private final PluginRepository pluginRepository;
    private final PluginValidator pluginValidator;
    private final ActionSchemaService actionSchemaService;

    private final List<PluginUninstallCheck> pluginUninstallChecks;
    private final List<PluginCleaner> pluginCleaners;

    public Result addPlugin(Plugin plugin) {
        List<String> validationErrors = pluginValidator.validate(plugin);
        if (!validationErrors.isEmpty()) {
            return new Result(false, validationErrors);
        }

        pluginRepository.deleteOlderVersions(plugin.getPluginCoordinates().getGroupId(), plugin.getPluginCoordinates().getArtifactId());
        pluginRepository.save(plugin);

        return Result.newBuilder().success(true).build();
    }

    public Optional<Plugin> getPlugin(PluginCoordinates pluginCoordinates) {
        return pluginRepository.findById(pluginCoordinates);
    }

    public List<Plugin> getPlugins() {
        return pluginRepository.findAll();
    }

    public List<Plugin> getPluginsWithVariables() {
        List<Plugin> plugins = getPlugins();
        plugins.forEach(this::addVariables);
        return plugins;
    }

    public void addVariables(Plugin plugin) {
        plugin.setVariables(pluginVariableService.getVariablesByPlugin(plugin.getPluginCoordinates()));
    }

    public List<Flows> getFlowsByPlugin() {
        Map<PluginCoordinates, List<IngressFlow>> ingressFlows = ingressFlowService.getFlowsGroupedByPlugin();
        Map<PluginCoordinates, List<EgressFlow>> egressFlows = egressFlowService.getFlowsGroupedByPlugin();
        Map<PluginCoordinates, List<EnrichFlow>> enrichFlows = enrichFlowService.getFlowsGroupedByPlugin();

        return getPluginsWithVariables().stream()
                .map(plugin -> toPluginFlows(plugin, ingressFlows, enrichFlows, egressFlows))
                .collect(Collectors.toList());
    }

    /**
     * Verify that all the actions listed in the plugin have registered themselves.
     * @param pluginCoordinates whose actions will be checked for registration
     * @return true if all the actions are registered
     */
    public boolean verifyActionsAreRegistered(PluginCoordinates pluginCoordinates) {
        Plugin plugin = getPlugin(pluginCoordinates).orElseThrow(() -> new IllegalArgumentException("No plugin is registered with coordinates of " + pluginCoordinates.toString()));
        return actionSchemaService.verifyActionsExist(plugin.actionNames());
    }

    private Flows toPluginFlows(Plugin plugin, Map<PluginCoordinates, List<IngressFlow>> ingressFlows, Map<PluginCoordinates, List<EnrichFlow>> enrichFlows, Map<PluginCoordinates, List<EgressFlow>> egressFlows) {
        return Flows.newBuilder()
                .sourcePlugin(plugin.getPluginCoordinates())
                .variables(plugin.getVariables())
                .ingressFlows(ingressFlows.getOrDefault(plugin.getPluginCoordinates(), Collections.emptyList()))
                .enrichFlows(enrichFlows.getOrDefault(plugin.getPluginCoordinates(), Collections.emptyList()))
                .egressFlows(egressFlows.getOrDefault(plugin.getPluginCoordinates(), Collections.emptyList()))
                .build();
    }

    public List<Plugin> getPluginsWithDependency(PluginCoordinates pluginCoordinates) {
        return pluginRepository.findPluginsWithDependency(pluginCoordinates);
    }

    private void removePlugin(Plugin plugin) {
        pluginRepository.deleteById(plugin.getPluginCoordinates());
    }

    public Result uninstallPlugin(boolean dryRun, PluginCoordinates pluginCoordinates) {
        Plugin plugin = getPlugin(pluginCoordinates).orElse(null);

        List<String> errors = canBeUninstalled(plugin);

        if (!errors.isEmpty()) {
            return Result.newBuilder().success(false).errors(errors).build();
        }

        if (!dryRun) {
            doUninstallPlugin(plugin);
        }

        return Result.newBuilder().success(true).build();
    }

    private List<String> canBeUninstalled(Plugin plugin) {
        if (Objects.isNull(plugin)) {
            return List.of("Plugin not found");
        }

        List<String> blockers = pluginUninstallChecks.stream()
                .map(pluginUninstallCheck -> pluginUninstallCheck.uninstallBlockers(plugin))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        PluginCoordinates pluginCoordinates = plugin.getPluginCoordinates();
        List<String> errors = new ArrayList<>(blockers);

        // If this plugin is the dependency of another plugin, then it cannot be removed.
        String dependents = getPluginsWithDependency(pluginCoordinates).stream()
                .map(dependent -> dependent.getPluginCoordinates().toString()).collect(Collectors.joining(", "));

        if (!dependents.isEmpty()) {
            errors.add("The following plugins depend on this plugin: " + dependents);
        }

        return errors;
    }

    private void doUninstallPlugin(Plugin plugin) {
        // TODO: TBD: remove plugin property sets
        pluginCleaners.forEach(pluginCleaner -> pluginCleaner.cleanupFor(plugin));
        // remove the plugin from the registry
        removePlugin(plugin);
    }

}
