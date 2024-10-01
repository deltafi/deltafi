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
package org.deltafi.core.services;

import com.netflix.graphql.dgs.InputArgument;
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.*;
import org.deltafi.core.generated.types.Flows;
import org.deltafi.core.repo.PluginRepository;
import org.deltafi.core.types.*;
import org.deltafi.core.types.snapshot.SnapshotRestoreOrder;
import org.deltafi.core.types.snapshot.SystemSnapshot;
import org.deltafi.core.validation.PluginValidator;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
@Service
@Slf4j
public class PluginService implements Snapshotter {

    private final PluginRepository pluginRepo;
    private final PluginVariableService pluginVariableService;
    private final BuildProperties buildProperties;
    private final EgressFlowService egressFlowService;
    private final RestDataSourceService restDataSourceService;
    private final TimedDataSourceService timedDataSourceService;
    private final TransformFlowService transformFlowService;
    private final Environment environment;
    private final PluginValidator pluginValidator;
    private final EgressFlowPlanService egressFlowPlanService;
    private final RestDataSourcePlanService restDataSourcePlanService;
    private final TimedDataSourcePlanService timedDataSourcePlanService;
    private final TransformFlowPlanService transformFlowPlanService;
    private final List<PluginUninstallCheck> pluginUninstallChecks;
    private final List<PluginCleaner> pluginCleaners;

    private Map<String, ActionDescriptor> actionDescriptorMap;

    public static final String SYSTEM_PLUGIN_GROUP_ID = "org.deltafi";
    public static final String SYSTEM_PLUGIN_ARTIFACT_ID = "system-plugin";
    private static final String SYSTEM_PLUGIN_DISPLAY_NAME = "System Plugin";
    private static final String SYSTEM_PLUGIN_DESCRIPTION = "System Plugin that holds flows created within the system";

    @PostConstruct
    public void updateSystemPlugin() {
        if (environment.getProperty("schedule.maintenance", Boolean.class, true)) {
            doUpdateSystemPlugin();
        }
    }

    public void doUpdateSystemPlugin() {
        Optional<PluginEntity> maybeSystemPlugin = pluginRepo.findById(new GroupIdArtifactId(SYSTEM_PLUGIN_GROUP_ID, SYSTEM_PLUGIN_ARTIFACT_ID));

        if (maybeSystemPlugin.isPresent()) {
            PluginEntity systemPlugin = maybeSystemPlugin.get();
            if (!Objects.equals(systemPlugin.getPluginCoordinates().getVersion(), buildProperties.getVersion())) {
                systemPlugin.setVersion(buildProperties.getVersion());
                if (systemPlugin.getFlowPlans() != null) {
                    systemPlugin.getFlowPlans()
                            .forEach(fp -> fp.getSourcePlugin().setVersion(buildProperties.getVersion()));
                }

                pluginRepo.save(systemPlugin);
            }
        } else {
            pluginRepo.save(createSystemPlugin());
        }
        updateActionDescriptors();
    }

    public void updateActionDescriptors() {
        actionDescriptorMap = pluginRepo.findAll()
                .stream()
                .map(PluginEntity::getActions)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(ActionDescriptor::getName, Function.identity(), (a, b) -> a));
    }

    public PluginEntity getSystemPlugin() {
        return pluginRepo.findById(new GroupIdArtifactId(SYSTEM_PLUGIN_GROUP_ID, SYSTEM_PLUGIN_ARTIFACT_ID)).orElse(null);
    }

    public PluginEntity createSystemPlugin() {
        PluginEntity plugin = new PluginEntity();
        plugin.setPluginCoordinates(getSystemPluginCoordinates());
        plugin.setDisplayName(SYSTEM_PLUGIN_DISPLAY_NAME);
        plugin.setDescription(SYSTEM_PLUGIN_DESCRIPTION);
        plugin.setActionKitVersion(plugin.getPluginCoordinates().getVersion());
        plugin.setActions(List.of());
        plugin.setFlowPlans(new ArrayList<>());
        return plugin;
    }

    public void addFlowPlanToSystemPlugin(FlowPlan flowPlan) {
        PluginEntity systemPlugin = getSystemPlugin();
        if (systemPlugin.getFlowPlans() == null) {
            systemPlugin.setFlowPlans(new ArrayList<>());
        }

        systemPlugin.getFlowPlans().removeIf(fp -> fp.getName().equals(flowPlan.getName()) && fp.getType() == flowPlan.getType());
        systemPlugin.getFlowPlans().add(flowPlan);

        pluginRepo.save(systemPlugin);
    }

    public boolean removeFlowPlanFromSystemPlugin(String flowPlanName, FlowType flowPlanType) {
        PluginEntity systemPlugin = getSystemPlugin();
        if (systemPlugin.getFlowPlans() == null) {
            return false;
        }
        FlowPlan flowPlan = systemPlugin.getFlowPlans().stream().filter(fp -> fp.getName().equals(flowPlanName) && fp.getType() == flowPlanType).findFirst().orElse(null);
        if (flowPlan == null) {
            return false;
        }
        systemPlugin.getFlowPlans().remove(flowPlan);

        if (flowPlan.getType() == FlowType.EGRESS) {
            egressFlowService.removeByName(flowPlanName, systemPlugin.getPluginCoordinates());
        } else if (flowPlan.getType() == FlowType.REST_DATA_SOURCE) {
            restDataSourceService.removeByName(flowPlanName, systemPlugin.getPluginCoordinates());
        } else if (flowPlan.getType() == FlowType.TIMED_DATA_SOURCE) {
            timedDataSourceService.removeByName(flowPlanName, systemPlugin.getPluginCoordinates());
        } else if (flowPlan.getType() == FlowType.TRANSFORM) {
            transformFlowService.removeByName(flowPlanName, systemPlugin.getPluginCoordinates());
        }

        pluginRepo.save(systemPlugin);
        return true;
    }

    public PluginCoordinates getSystemPluginCoordinates() {
        return new PluginCoordinates(SYSTEM_PLUGIN_GROUP_ID, SYSTEM_PLUGIN_ARTIFACT_ID, buildProperties.getVersion());
    }

    public List<FlowPlan> getAllFlowPlans() {
        return pluginRepo.findAll().stream()
                .flatMap(plugin -> plugin.getFlowPlans() == null ? Stream.of() : plugin.getFlowPlans().stream())
                .toList();
    }

    public List<FlowPlan> getFlowPlansByType(FlowType flowType) {
        return pluginRepo.findAll().stream()
                .flatMap(plugin -> plugin.getFlowPlans() == null ? Stream.of() : plugin.getFlowPlans().stream())
                .filter(fp -> fp.getType() == flowType)
                .toList();
    }

    public Optional<FlowPlan> getFlowPlanByNameAndType(String flowPlanName, FlowType flowType) {
        return pluginRepo.findAll().stream()
                .flatMap(plugin -> plugin.getFlowPlans() == null ? Stream.of() : plugin.getFlowPlans().stream())
                .filter(fp -> fp.getType() == flowType && fp.getName().equals(flowPlanName))
                .findFirst();
    }

    public boolean setPluginVariableValues(@InputArgument PluginCoordinates pluginCoordinates, @InputArgument List<KeyValue> variables) {
        VariableUpdate update = pluginVariableService.setVariableValues(pluginCoordinates, variables);
        if (update.isUpdated()) {
            List<FlowPlan> flowPlans = getPlugin(pluginCoordinates).map(PluginEntity::getFlowPlans).orElse(List.of());
            egressFlowService.rebuildFlows(filterByType(flowPlans, FlowType.EGRESS), pluginCoordinates);
            transformFlowService.rebuildFlows(filterByType(flowPlans, FlowType.TRANSFORM), pluginCoordinates);
            restDataSourceService.rebuildFlows(filterByType(flowPlans, FlowType.REST_DATA_SOURCE), pluginCoordinates);
            timedDataSourceService.rebuildFlows(filterByType(flowPlans, FlowType.TIMED_DATA_SOURCE), pluginCoordinates);
        }

        return update.isUpdated();
    }

    private List<FlowPlan> filterByType(List<FlowPlan> flowPlans, FlowType flowType) {
        return flowPlans.stream().filter(fp -> fp.getType() == flowType).toList();
    }

    @Transactional
    public Result register(PluginRegistration pluginRegistration) {
        log.info("{}", pluginRegistration);
        PluginEntity plugin = new PluginEntity(pluginRegistration.toPlugin());
        GroupedFlowPlans groupedFlowPlans = groupPlansByFlowType(pluginRegistration);

        // Validate everything before persisting changes, the plugin should not be considered installed if validation fails
        List<String> validationErrors = validate(plugin, groupedFlowPlans, pluginRegistration.getVariables());
        if (!validationErrors.isEmpty()) {
            return Result.builder().success(false).errors(validationErrors).build();
        }

        Optional<PluginEntity> existingPlugin = getPlugin(plugin.getPluginCoordinates());
        // if this plugin group/artifactId/version already exists, don't delete it before overwriting
        if (existingPlugin.isEmpty()) {
            pluginRepo.deleteById(new GroupIdArtifactId(plugin.getPluginCoordinates().getGroupId(), plugin.getPluginCoordinates().getArtifactId()));
        }
        pluginRepo.save(plugin);

        updateActionDescriptors();
        pluginVariableService.saveVariables(plugin.getPluginCoordinates(), pluginRegistration.getVariables());
        upgradeFlows(plugin, groupedFlowPlans);

        try {
            revalidateFlows();
        } catch (Exception ignored) {
            // this mimics the old behavior where this happened asynchronously
            // presumably we don't want to error if a flow from another plugin no longer validates, or if we hit an exception
        }

        return Result.builder().success(true).build();
    }

    /**
     * Check the plugin info, flow plans and variables for any errors that should
     * prevent the plugin from successfully registering
     * @return the list of errors
     */
    private List<String> validate(PluginEntity plugin, GroupedFlowPlans groupedFlowPlans, List<Variable> variables) {
        List<FlowPlan> existingFlowPlans = getAllFlowPlans();
        List<String> errors = new ArrayList<>();
        errors.addAll(pluginValidator.validate(plugin));
        errors.addAll(transformFlowPlanService.validateFlowPlans(groupedFlowPlans.transformFlowPlans, existingFlowPlans));
        errors.addAll(restDataSourcePlanService.validateFlowPlans(groupedFlowPlans.restDataSourcePlans, existingFlowPlans));
        errors.addAll(timedDataSourcePlanService.validateFlowPlans(groupedFlowPlans.timedDataSourcePlans, existingFlowPlans));
        errors.addAll(egressFlowPlanService.validateFlowPlans(groupedFlowPlans.egressFlowPlans, existingFlowPlans));
        errors.addAll(pluginVariableService.validateVariables(variables));

        List<String> duplicateNames = findDuplicateDataSourceNames(groupedFlowPlans, plugin.getPluginCoordinates());
        if (!duplicateNames.isEmpty()) {
            errors.add("Duplicate data source names found: " + String.join(", ", duplicateNames));
        }

        return errors;
    }

    private void upgradeFlows(PluginEntity sourcePlugin, GroupedFlowPlans groupedFlowPlans) {
        transformFlowService.upgradeFlows(sourcePlugin.getPluginCoordinates(), groupedFlowPlans.transformFlowPlans());
        restDataSourceService.upgradeFlows(sourcePlugin.getPluginCoordinates(), groupedFlowPlans.restDataSourcePlans());
        timedDataSourceService.upgradeFlows(sourcePlugin.getPluginCoordinates(), groupedFlowPlans.timedDataSourcePlans());
        egressFlowService.upgradeFlows(sourcePlugin.getPluginCoordinates(), groupedFlowPlans.egressFlowPlans());
    }

    /**
     * Group the flow plans by the flow plan type. Run validation of each flow plan.
     * @param pluginRegistration registration object holding the flow plans to save
     * @return lists of each flow by type
     */
    private GroupedFlowPlans groupPlansByFlowType(PluginRegistration pluginRegistration) {
        List<TransformFlowPlan> transformFlowPlans = new ArrayList<>();
        List<EgressFlowPlan> egressFlowPlans = new ArrayList<>();
        List<RestDataSourcePlan> restDataSourcePlans = new ArrayList<>();
        List<TimedDataSourcePlan> timedDataSourcePlans = new ArrayList<>();

        if (pluginRegistration.getFlowPlans() != null) {
            pluginRegistration.getFlowPlans().forEach(flowPlan -> {
                flowPlan.setSourcePlugin(pluginRegistration.getPluginCoordinates());
                switch (flowPlan) {
                    case TransformFlowPlan plan -> transformFlowPlans.add(plan);
                    case EgressFlowPlan plan -> egressFlowPlans.add(plan);
                    case RestDataSourcePlan plan -> restDataSourcePlans.add(plan);
                    case TimedDataSourcePlan plan -> timedDataSourcePlans.add(plan);
                    default -> log.warn("Unknown flow plan type: {}", flowPlan.getClass());
                }
            });
        }

        return new GroupedFlowPlans(transformFlowPlans, egressFlowPlans, restDataSourcePlans, timedDataSourcePlans);
    }

    public Optional<PluginEntity> getPlugin(PluginCoordinates pluginCoordinates) {
        return pluginRepo.findByKeyGroupIdAndKeyArtifactIdAndVersion(pluginCoordinates.getGroupId(), pluginCoordinates.getArtifactId(), pluginCoordinates.getVersion());
    }

    public List<PluginEntity> getPlugins() {
        return pluginRepo.findAll();
    }

    public List<PluginEntity> getPluginsWithVariables() {
        List<PluginEntity> plugins = getPlugins();
        plugins.forEach(this::addVariables);
        return plugins;
    }

    private void addVariables(PluginEntity plugin) {
        List<Variable> variables = pluginVariableService.getVariablesByPlugin(plugin.getPluginCoordinates());

        if (!DeltaFiUserService.currentUserCanViewMasked()) {
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
        return pluginRepo.findPluginsWithDependency(pluginCoordinates);
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
        PluginEntity plugin = getPlugin(pluginCoordinates).orElseThrow();
        pluginCleaners.forEach(pluginCleaner -> pluginCleaner.cleanupFor(plugin));
        pluginRepo.delete(plugin);
        updateActionDescriptors();
        revalidateFlows();
    }

    public Optional<ActionDescriptor> getByActionClass(String type) {
        ActionDescriptor actionDescriptor = actionDescriptorMap != null ? actionDescriptorMap.get(type) : null;
        return Optional.ofNullable(actionDescriptor);
    }

    public Collection<ActionDescriptor> getActionDescriptors() {
        return actionDescriptorMap.values();
    }

    private record GroupedFlowPlans(List<TransformFlowPlan> transformFlowPlans, List<EgressFlowPlan> egressFlowPlans, List<RestDataSourcePlan> restDataSourcePlans, List<TimedDataSourcePlan> timedDataSourcePlans){}

    private List<String> findDuplicateDataSourceNames(GroupedFlowPlans groupedFlowPlans, PluginCoordinates incomingPluginCoordinates) {
        Set<String> incomingNames = new HashSet<>();
        Set<String> conflictingNames = new HashSet<>();

        // Check incoming plugin's data sources
        Stream.concat(groupedFlowPlans.restDataSourcePlans.stream(), groupedFlowPlans.timedDataSourcePlans.stream())
                .map(DataSourcePlan::getName)
                .forEach(name -> {
                    if (!incomingNames.add(name)) {
                        conflictingNames.add(name);
                    }
                });

        // Check against existing data sources, excluding those from the same plugin
        Stream.concat(restDataSourceService.getAll().stream(), timedDataSourceService.getAll().stream())
                .filter(existing -> !existing.getSourcePlugin().equalsIgnoreVersion(incomingPluginCoordinates))
                .map(DataSource::getName)
                .filter(incomingNames::contains)
                .forEach(conflictingNames::add);

        return new ArrayList<>(conflictingNames);
    }

    /**
     * Find the flow plan with the given name, or throw an exception if it does not exist
     * @param flowPlanName name of the plan to find
     * @return flow plan with the given name
     */
    public FlowPlan getPlanByName(String flowPlanName, FlowType flowType) {
        return getFlowPlanByNameAndType(flowPlanName, flowType)
                .orElseThrow(() -> new DgsEntityNotFoundException("Could not find a flow plan named " + flowPlanName));
    }

    public void revalidateFlows() {
        rebuildInvalidFlows(egressFlowService, FlowType.EGRESS);
        rebuildInvalidFlows(restDataSourceService, FlowType.REST_DATA_SOURCE);
        rebuildInvalidFlows(timedDataSourceService, FlowType.TIMED_DATA_SOURCE);
        rebuildInvalidFlows(transformFlowService, FlowType.TRANSFORM);

        egressFlowService.validateAllFlows();
        restDataSourceService.validateAllFlows();
        timedDataSourceService.validateAllFlows();
        transformFlowService.validateAllFlows();
    }

    public void rebuildInvalidFlows(FlowService<?, ?, ?, ?> flowService, FlowType flowType) {
        List<String> invalidFlows = flowService.getNamesOfInvalidFlows();

        Map<PluginCoordinates, List<FlowPlan>> flowPlans = invalidFlows.stream()
                .map(name -> getFlowPlanByNameAndType(name, flowType))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.groupingBy(FlowPlan::getSourcePlugin));

        flowPlans.forEach((pluginCoordinates, plans) -> flowService.rebuildFlows(plans, pluginCoordinates));
    }
}