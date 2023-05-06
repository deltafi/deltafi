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

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.*;
import org.deltafi.core.generated.types.Flows;
import org.deltafi.core.services.*;
import org.deltafi.core.snapshot.SnapshotRestoreOrder;
import org.deltafi.core.snapshot.Snapshotter;
import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.types.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Slf4j
public class PluginRegistryService implements Snapshotter {
    private final IngressFlowService ingressFlowService;
    private final EnrichFlowService enrichFlowService;
    private final EgressFlowService egressFlowService;
    private final TransformFlowService transformFlowService;
    private final PluginRepository pluginRepository;
    private final PluginValidator pluginValidator;
    private final ActionDescriptorService actionDescriptorService;
    private final PluginVariableService pluginVariableService;
    private final IngressFlowPlanService ingressFlowPlanService;
    private final EnrichFlowPlanService enrichFlowPlanService;
    private final EgressFlowPlanService egressFlowPlanService;
    private final TransformFlowPlanService transformFlowPlanService;

    private final List<PluginUninstallCheck> pluginUninstallChecks;
    private final List<PluginCleaner> pluginCleaners;

    public Result register(PluginRegistration pluginRegistration) {
        Plugin plugin = pluginRegistration.toPlugin();
        GroupedFlowPlans groupedFlowPlans = groupPlansByFlowType(pluginRegistration);

        // Validate everything before persisting changes, the plugin should not be considered installed if validation fails
        List<String> validationErrors = validate(plugin, groupedFlowPlans, pluginRegistration.getVariables());
        if (!validationErrors.isEmpty()) {
            return Result.newBuilder().success(false).errors(validationErrors).build();
        }

        pluginRepository.deleteOlderVersions(plugin.getPluginCoordinates().getGroupId(), plugin.getPluginCoordinates().getArtifactId());
        pluginRepository.save(plugin);
        actionDescriptorService.registerActions(plugin.getActions());
        pluginVariableService.saveVariables(plugin.getPluginCoordinates(), pluginRegistration.getVariables());
        upgradeFlowPlans(plugin.getPluginCoordinates(), groupedFlowPlans);

        return Result.newBuilder().success(true).build();
    }

    /**
     * Check the plugin info, flow plans and variables for any errors that should
     * prevent the plugin from successfully registering
     * @return the list of errors
     */
    private List<String> validate(Plugin plugin, GroupedFlowPlans groupedFlowPlans, List<Variable> variables) {
        List<String> errors = new ArrayList<>();
        errors.addAll(pluginValidator.validate(plugin));
        errors.addAll(ingressFlowPlanService.validateFlowPlans(groupedFlowPlans.ingressFlowPlans));
        errors.addAll(transformFlowPlanService.validateFlowPlans(groupedFlowPlans.transformFlowPlans));
        errors.addAll(enrichFlowPlanService.validateFlowPlans(groupedFlowPlans.enrichFlowPlans));
        errors.addAll(egressFlowPlanService.validateFlowPlans(groupedFlowPlans.egressFlowPlans));
        errors.addAll(pluginVariableService.validateVariables(variables));
        return errors;
    }

    private void upgradeFlowPlans(PluginCoordinates sourcePlugin, GroupedFlowPlans groupedFlowPlans) {
        ingressFlowPlanService.upgradeFlowPlans(sourcePlugin, groupedFlowPlans.ingressFlowPlans());
        transformFlowPlanService.upgradeFlowPlans(sourcePlugin, groupedFlowPlans.transformFlowPlans());
        enrichFlowPlanService.upgradeFlowPlans(sourcePlugin, groupedFlowPlans.enrichFlowPlans());
        egressFlowPlanService.upgradeFlowPlans(sourcePlugin, groupedFlowPlans.egressFlowPlans());
    }

    /**
     * Group the flow plans by the flow plan type. Run validation of each flow plan.
     * @param pluginRegistration registration object holding the flow plans to save
     * @return lists of each flow by type
     */
    private GroupedFlowPlans groupPlansByFlowType(PluginRegistration pluginRegistration) {
        List<IngressFlowPlan> ingressFlowPlans = new ArrayList<>();
        List<TransformFlowPlan> transformFlowPlans = new ArrayList<>();
        List<EnrichFlowPlan> enrichFlowPlans = new ArrayList<>();
        List<EgressFlowPlan> egressFlowPlans = new ArrayList<>();

        if (pluginRegistration.getFlowPlans() != null) {
            pluginRegistration.getFlowPlans().forEach(flowPlan -> {
                flowPlan.setSourcePlugin(pluginRegistration.getPluginCoordinates());
                if (flowPlan instanceof IngressFlowPlan plan) {
                    ingressFlowPlans.add(plan);
                } else if (flowPlan instanceof TransformFlowPlan plan) {
                    transformFlowPlans.add(plan);
                } else if (flowPlan instanceof EnrichFlowPlan plan) {
                    enrichFlowPlans.add(plan);
                } else if (flowPlan instanceof EgressFlowPlan plan) {
                    egressFlowPlans.add(plan);
                } else {
                    log.warn("Unknown flow plan type: {}", flowPlan.getClass());
                }
            });
        }

        return new GroupedFlowPlans(ingressFlowPlans, transformFlowPlans, enrichFlowPlans, egressFlowPlans);
    }

    public Optional<Plugin> getPlugin(PluginCoordinates pluginCoordinates) {
        return pluginRepository.findById(pluginCoordinates);
    }

    public List<Plugin> getPlugins() {
        return pluginRepository.findAll();
    }

    // TODO: Maybe variables should be stored with plugins???
    public List<Plugin> getPluginsWithVariables() {
        List<Plugin> plugins = getPlugins();
        plugins.forEach(this::addVariables);
        return plugins;
    }

    private void addVariables(Plugin plugin) {
        plugin.setVariables(pluginVariableService.getVariablesByPlugin(plugin.getPluginCoordinates()));
    }

    public List<Flows> getFlowsByPlugin() {
        Map<PluginCoordinates, List<IngressFlow>> ingressFlows = ingressFlowService.getFlowsGroupedByPlugin();
        Map<PluginCoordinates, List<EgressFlow>> egressFlows = egressFlowService.getFlowsGroupedByPlugin();
        Map<PluginCoordinates, List<EnrichFlow>> enrichFlows = enrichFlowService.getFlowsGroupedByPlugin();
        Map<PluginCoordinates, List<TransformFlow>> transformFlows = transformFlowService.getFlowsGroupedByPlugin();

        return getPluginsWithVariables().stream()
                .map(plugin -> toPluginFlows(plugin, ingressFlows, enrichFlows, egressFlows, transformFlows))
                .toList();
    }

    /**
     * Verify that all the actions listed in the plugin have registered themselves.
     * @param pluginCoordinates whose actions will be checked for registration
     * @return true if all the actions are registered
     */
    public boolean verifyActionsAreRegistered(PluginCoordinates pluginCoordinates) {
        Plugin plugin = getPlugin(pluginCoordinates).orElseThrow(() -> new IllegalArgumentException("No plugin is registered with coordinates of " + pluginCoordinates.toString()));
        return actionDescriptorService.verifyActionsExist(plugin.actionNames());
    }

    private Flows toPluginFlows(Plugin plugin, Map<PluginCoordinates, List<IngressFlow>> ingressFlows, Map<PluginCoordinates, List<EnrichFlow>> enrichFlows, Map<PluginCoordinates, List<EgressFlow>> egressFlows, Map<PluginCoordinates, List<TransformFlow>> transformFlows) {
        return Flows.newBuilder()
                .sourcePlugin(plugin.getPluginCoordinates())
                .variables(plugin.getVariables())
                .ingressFlows(ingressFlows.getOrDefault(plugin.getPluginCoordinates(), Collections.emptyList()))
                .enrichFlows(enrichFlows.getOrDefault(plugin.getPluginCoordinates(), Collections.emptyList()))
                .egressFlows(egressFlows.getOrDefault(plugin.getPluginCoordinates(), Collections.emptyList()))
                .transformFlows(transformFlows.getOrDefault(plugin.getPluginCoordinates(), Collections.emptyList()))
                .build();
    }

    public List<Plugin> getPluginsWithDependency(PluginCoordinates pluginCoordinates) {
        return pluginRepository.findPluginsWithDependency(pluginCoordinates);
    }

    private void removePlugin(Plugin plugin) {
        pluginRepository.deleteById(plugin.getPluginCoordinates());
    }

    @Override
    public void updateSnapshot(SystemSnapshot systemSnapshot) {
        systemSnapshot.setInstalledPlugins(getInstalledPluginCoordinates());
    }

    @Override
    public Result resetFromSnapshot(SystemSnapshot systemSnapshot, boolean hardReset) {
        Result result = new Result();
        Set<PluginCoordinates> installedPlugins = getInstalledPluginCoordinates();
        Set<PluginCoordinates> snapshotPlugins = systemSnapshot.getInstalledPlugins();

        Set<PluginCoordinates> missing = new HashSet<>(installedPlugins);

        for (PluginCoordinates installedPlugin : installedPlugins) {
            for (PluginCoordinates snapshotPlugin : snapshotPlugins) {
                if (snapshotPlugin.equals(installedPlugin)) {
                    missing.remove(installedPlugin);
                } else if (snapshotPlugin.equalsIgnoreVersion(installedPlugin)) {
                    missing.remove(installedPlugin);
                    result.getInfo().add("Installed plugin " + installedPlugin + " was a different version at the time of the snapshot: " + snapshotPlugin);
                }
            }
        }

        result.getInfo().addAll(missing.stream().map(installed -> "Installed plugin " + installed + " was not installed at the time of the snapshot").toList());

        missing = new HashSet<>(snapshotPlugins);
        for (PluginCoordinates snapshotPlugin: snapshotPlugins) {
            for (PluginCoordinates installedPlugin: installedPlugins) {
                if (snapshotPlugin.equals(installedPlugin) || snapshotPlugin.equalsIgnoreVersion(installedPlugin)) {
                    missing.remove(snapshotPlugin);
                }
            }
        }

        result.getInfo().addAll(missing.stream().map(snapshotPlugin -> "Plugin " + snapshotPlugin + " was installed at the time of the snapshot but is no longer installed").toList());
        return result;
    }

    @Override
    public int getOrder() {
        return SnapshotRestoreOrder.PLUGIN_REGISTRY_ORDER;
    }

    private Set<PluginCoordinates> getInstalledPluginCoordinates() {
        return getPlugins().stream().map(Plugin::getPluginCoordinates).collect(Collectors.toSet());
    }

    public List<String> canBeUninstalled(PluginCoordinates pluginCoordinates) {
        Plugin plugin = getPlugin(pluginCoordinates).orElse(null);

        if (Objects.isNull(plugin)) {
            return List.of("Plugin not found");
        }

        List<String> blockers = pluginUninstallChecks.stream()
                .map(pluginUninstallCheck -> pluginUninstallCheck.uninstallBlockers(plugin))
                .filter(Objects::nonNull)
                .toList();

        List<String> errors = new ArrayList<>(blockers);

        // If this plugin is the dependency of another plugin, then it cannot be removed.
        String dependents = getPluginsWithDependency(pluginCoordinates).stream()
                .map(dependent -> dependent.getPluginCoordinates().toString()).collect(Collectors.joining(", "));

        if (!dependents.isEmpty()) {
            errors.add("The following plugins depend on this plugin: " + dependents);
        }

        return errors;
    }

    public void uninstallPlugin(PluginCoordinates pluginCoordinates) {
        // TODO: TBD: remove plugin property sets
        Plugin plugin = getPlugin(pluginCoordinates).orElseThrow();
        pluginCleaners.forEach(pluginCleaner -> pluginCleaner.cleanupFor(plugin));
        // remove the plugin from the registry
        removePlugin(plugin);
    }

    private record GroupedFlowPlans(List<IngressFlowPlan> ingressFlowPlans, List<TransformFlowPlan> transformFlowPlans, List<EnrichFlowPlan> enrichFlowPlans, List<EgressFlowPlan> egressFlowPlans){}

}
