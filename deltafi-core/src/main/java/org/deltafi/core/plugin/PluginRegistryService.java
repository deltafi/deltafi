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

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.*;
import org.deltafi.core.generated.types.Flows;
import org.deltafi.core.security.DeltaFiUserDetailsService;
import org.deltafi.core.services.*;
import org.deltafi.core.types.snapshot.SnapshotRestoreOrder;
import org.deltafi.core.services.Snapshotter;
import org.deltafi.core.types.snapshot.SystemSnapshot;
import org.deltafi.core.types.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PluginRegistryService implements Snapshotter {
    private final EgressFlowService egressFlowService;
    private final TransformFlowService transformFlowService;
    private final RestDataSourceService restDataSourceService;
    private final TimedDataSourceService timedDataSourceService;
    private final PluginRepository pluginRepository;
    private final PluginValidator pluginValidator;
    private final PluginVariableService pluginVariableService;
    private final EgressFlowPlanService egressFlowPlanService;
    private final TransformFlowPlanService transformFlowPlanService;
    private final RestDataSourcePlanService restDataSourcePlanService;
    private final TimedDataSourcePlanService timedDataSourcePlanService;
    private final SystemPluginService systemPluginService;
    private final FlowValidationService flowValidationService;
    private final List<PluginUninstallCheck> pluginUninstallChecks;
    private final List<PluginCleaner> pluginCleaners;
    private Map<String, ActionDescriptor> actionDescriptorMap;

    @PostConstruct
    public void initialize() {
        PluginEntity systemPlugin = systemPluginService.getSystemPlugin();
        pluginRepository.deleteByGroupIdAndArtifactId(systemPlugin.getPluginCoordinates().getGroupId(), systemPlugin.getPluginCoordinates().getArtifactId());
        pluginRepository.save(systemPlugin);
        updateActionDescriptors();
    }

    public void updateActionDescriptors() {
        actionDescriptorMap = pluginRepository.findAll()
                .stream()
                .map(PluginEntity::getActions)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(ActionDescriptor::getName, Function.identity(), (a,b) -> a));
    }

    public Result register(PluginRegistration pluginRegistration) {
        log.info("{}", pluginRegistration);
        PluginEntity plugin = PluginEntity.fromPlugin(pluginRegistration.toPlugin());
        GroupedFlowPlans groupedFlowPlans = groupPlansByFlowType(pluginRegistration);

        // Validate everything before persisting changes, the plugin should not be considered installed if validation fails
        List<String> validationErrors = validate(plugin, groupedFlowPlans, pluginRegistration.getVariables());
        if (!validationErrors.isEmpty()) {
            return Result.builder().success(false).errors(validationErrors).build();
        }

        pluginRepository.deleteByGroupIdAndArtifactId(plugin.getPluginCoordinates().getGroupId(), plugin.getPluginCoordinates().getArtifactId());
        pluginRepository.save(plugin);
        updateActionDescriptors();
        pluginVariableService.saveVariables(plugin.getPluginCoordinates(), pluginRegistration.getVariables());
        upgradeFlowPlans(plugin.getPluginCoordinates(), groupedFlowPlans);

        flowValidationService.asyncRevalidateFlows();
        return Result.builder().success(true).build();
    }

    /**
     * Check the plugin info, flow plans and variables for any errors that should
     * prevent the plugin from successfully registering
     * @return the list of errors
     */
    private List<String> validate(PluginEntity plugin, GroupedFlowPlans groupedFlowPlans, List<Variable> variables) {
        List<String> errors = new ArrayList<>();
        errors.addAll(pluginValidator.validate(plugin));
        errors.addAll(transformFlowPlanService.validateFlowPlans(groupedFlowPlans.transformFlowPlans));
        errors.addAll(restDataSourcePlanService.validateFlowPlans(groupedFlowPlans.restDataSourcePlans));
        errors.addAll(timedDataSourcePlanService.validateFlowPlans(groupedFlowPlans.timedDataSourcePlans));
        errors.addAll(egressFlowPlanService.validateFlowPlans(groupedFlowPlans.egressFlowPlans));
        errors.addAll(pluginVariableService.validateVariables(variables));
        return errors;
    }

    private void upgradeFlowPlans(PluginCoordinates sourcePlugin, GroupedFlowPlans groupedFlowPlans) {
        transformFlowPlanService.upgradeFlowPlans(sourcePlugin, groupedFlowPlans.transformFlowPlans());
        restDataSourcePlanService.upgradeFlowPlans(sourcePlugin, groupedFlowPlans.restDataSourcePlans());
        timedDataSourcePlanService.upgradeFlowPlans(sourcePlugin, groupedFlowPlans.timedDataSourcePlans());
        egressFlowPlanService.upgradeFlowPlans(sourcePlugin, groupedFlowPlans.egressFlowPlans());
    }

    /**
     * Group the flow plans by the flow plan type. Run validation of each flow plan.
     * @param pluginRegistration registration object holding the flow plans to save
     * @return lists of each flow by type
     */
    private GroupedFlowPlans groupPlansByFlowType(PluginRegistration pluginRegistration) {
        List<TransformFlowPlanEntity> transformFlowPlans = new ArrayList<>();
        List<EgressFlowPlanEntity> egressFlowPlans = new ArrayList<>();
        List<RestDataSourcePlanEntity> restDataSourcePlans = new ArrayList<>();
        List<TimedDataSourcePlanEntity> timedDataSourcePlans = new ArrayList<>();

        if (pluginRegistration.getFlowPlans() != null) {
            pluginRegistration.getFlowPlans().forEach(flowPlan -> {
                flowPlan.setSourcePlugin(pluginRegistration.getPluginCoordinates());
                switch (flowPlan) {
                    case TransformFlowPlan plan -> transformFlowPlans.add(TransformFlowPlanEntity.fromFlowPlan(plan));
                    case EgressFlowPlan plan -> egressFlowPlans.add(EgressFlowPlanEntity.fromFlowPlan(plan));
                    case RestDataSourcePlan plan -> restDataSourcePlans.add(RestDataSourcePlanEntity.fromFlowPlan(plan));
                    case TimedDataSourcePlan plan -> timedDataSourcePlans.add(TimedDataSourcePlanEntity.fromFlowPlan(plan));
                    default -> log.warn("Unknown flow plan type: {}", flowPlan.getClass());
                }
            });
        }

        return new GroupedFlowPlans(transformFlowPlans, egressFlowPlans, restDataSourcePlans, timedDataSourcePlans);
    }

    public Optional<PluginEntity> getPlugin(PluginCoordinates pluginCoordinates) {
        return pluginRepository.findById(pluginCoordinates);
    }

    public List<PluginEntity> getPlugins() {
        return pluginRepository.findAll();
    }

    // TODO: Maybe variables should be stored with plugins???
    public List<PluginEntity> getPluginsWithVariables() {
        List<PluginEntity> plugins = getPlugins();
        plugins.forEach(this::addVariables);
        return plugins;
    }

    private void addVariables(PluginEntity plugin) {
        List<Variable> variables = pluginVariableService.getVariablesByPlugin(plugin.getPluginCoordinates());

        if (!DeltaFiUserDetailsService.currentUserCanViewMasked()) {
            variables = variables.stream().map(Variable::maskIfSensitive).toList();
        }

        plugin.setVariables(variables);
    }

    public List<Flows> getFlowsByPlugin() {
        Map<PluginCoordinates, List<EgressFlow>> egressFlows = egressFlowService.getFlowsGroupedByPlugin();
        Map<PluginCoordinates, List<TransformFlow>> transformFlows = transformFlowService.getFlowsGroupedByPlugin();
        Map<PluginCoordinates, List<RestDataSource>> restDataSources = restDataSourceService.getFlowsGroupedByPlugin();
        Map<PluginCoordinates, List<TimedDataSource>> timedDataSources = timedDataSourceService.getFlowsGroupedByPlugin();

        return getPluginsWithVariables().stream()
                .map(plugin -> toPluginFlows(plugin, egressFlows, transformFlows, restDataSources, timedDataSources))
                .toList();
    }

    private Flows toPluginFlows(PluginEntity plugin,
                                Map<PluginCoordinates, List<EgressFlow>> egressFlows,
                                Map<PluginCoordinates, List<TransformFlow>> transformFlows,
                                Map<PluginCoordinates, List<RestDataSource>> restDataSources,
                                Map<PluginCoordinates, List<TimedDataSource>> timedDataSources) {

        return Flows.newBuilder()
                .sourcePlugin(plugin.getPluginCoordinates())
                .variables(plugin.getVariables())
                .egressFlows(egressFlows.getOrDefault(plugin.getPluginCoordinates(), Collections.emptyList()))
                .transformFlows(transformFlows.getOrDefault(plugin.getPluginCoordinates(), Collections.emptyList()))
                .restDataSources(restDataSources.getOrDefault(plugin.getPluginCoordinates(), Collections.emptyList()))
                .timedDataSources(timedDataSources.getOrDefault(plugin.getPluginCoordinates(), Collections.emptyList()))
                .build();
    }

    public List<PluginEntity> getPluginsWithDependency(PluginCoordinates pluginCoordinates) {
        return pluginRepository.findPluginsWithDependency(pluginCoordinates);
    }

    private void removePlugin(PluginEntity plugin) {
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
        return getPlugins().stream().map(PluginEntity::getPluginCoordinates).collect(Collectors.toSet());
    }

    public List<String> canBeUninstalled(PluginCoordinates pluginCoordinates) {
        PluginEntity plugin = getPlugin(pluginCoordinates).orElse(null);

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
        PluginEntity plugin = getPlugin(pluginCoordinates).orElseThrow();
        pluginCleaners.forEach(pluginCleaner -> pluginCleaner.cleanupFor(plugin));
        removePlugin(plugin);
        updateActionDescriptors();
        flowValidationService.revalidateFlows();
    }

    public Optional<ActionDescriptor> getByActionClass(String type) {
        ActionDescriptor actionDescriptor = actionDescriptorMap != null ? actionDescriptorMap.get(type) : null;
        return Optional.ofNullable(actionDescriptor);
    }

    public Collection<ActionDescriptor> getActionDescriptors() {
        return actionDescriptorMap.values();
    }

    private record GroupedFlowPlans(List<TransformFlowPlanEntity> transformFlowPlans, List<EgressFlowPlanEntity> egressFlowPlans, List<RestDataSourcePlanEntity> restDataSourcePlans, List<TimedDataSourcePlanEntity> timedDataSourcePlans){}

}
