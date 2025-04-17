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
package org.deltafi.core.services;

import com.netflix.graphql.dgs.InputArgument;
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.deltafi.common.action.documentation.DocumentationGenerator;
import org.deltafi.common.types.*;
import org.deltafi.common.types.integration.IntegrationTest;
import org.deltafi.common.util.ParameterUtil;
import org.deltafi.core.generated.types.Flows;
import org.deltafi.core.generated.types.SystemFlowPlans;
import org.deltafi.core.integration.IntegrationService;
import org.deltafi.core.repo.PluginRepository;
import org.deltafi.core.types.*;
import org.deltafi.core.types.snapshot.PluginSnapshot;
import org.deltafi.core.types.snapshot.Snapshot;
import org.deltafi.core.types.snapshot.SnapshotRestoreOrder;
import org.deltafi.core.validation.FlowPlanValidator;
import org.deltafi.core.validation.PluginValidator;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class PluginService implements Snapshotter {

    private final PluginRepository pluginRepo;
    private final PluginVariableService pluginVariableService;
    private final BuildProperties buildProperties;
    private final DataSinkService dataSinkService;
    private final RestDataSourceService restDataSourceService;
    private final TimedDataSourceService timedDataSourceService;
    private final TransformFlowService transformFlowService;
    private final Environment environment;
    private final PluginValidator pluginValidator;
    private final DataSinkPlanService dataSinkPlanService;
    private final RestDataSourcePlanService restDataSourcePlanService;
    private final TimedDataSourcePlanService timedDataSourcePlanService;
    private final TransformFlowPlanService transformFlowPlanService;
    private final List<PluginUninstallCheck> pluginUninstallChecks;
    private final List<PluginCleaner> pluginCleaners;

    private final ReentrantLock mapsUpdateLock = new ReentrantLock();

    private Map<String, ActionDescriptor> actionDescriptorMap;
    private Map<String, String> actionsToPlugin;

    public static final String SYSTEM_PLUGIN_GROUP_ID = "org.deltafi";
    public static final String SYSTEM_PLUGIN_ARTIFACT_ID = "system-plugin";
    public static final GroupIdArtifactId SYSTEM_PLUGIN_ID = new GroupIdArtifactId(SYSTEM_PLUGIN_GROUP_ID, SYSTEM_PLUGIN_ARTIFACT_ID);
    private static final String SYSTEM_PLUGIN_DISPLAY_NAME = "System Plugin";
    private static final String SYSTEM_PLUGIN_DESCRIPTION = "System Plugin that holds flows created within the system";

    @PostConstruct
    public void updateSystemPlugin() {
        if (Boolean.TRUE.equals(environment.getProperty("schedule.maintenance", Boolean.class, true))) {
            doUpdateSystemPlugin();
        }
    }

    public void flushToDB() {
        pluginRepo.flush();
    }

    public void acquireUpdateLock() {
        mapsUpdateLock.lock();
    }

    public void releaseUpdateLock() {
        mapsUpdateLock.unlock();
    }

    public void doUpdateSystemPlugin() {
        Optional<PluginEntity> maybeSystemPlugin = pluginRepo.findById(SYSTEM_PLUGIN_ID);

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
        acquireUpdateLock();
        try {
            updateActionMapsNoLockCheck();
        } finally {
            releaseUpdateLock();
        }
    }

    private void updateActionMapsNoLockCheck() {
         Map<String, ActionDescriptor> nextActionDescriptorMap = new HashMap<>();
         Map<String, String> nextActionsToPlugin = new HashMap<>();

        pluginRepo.findAll().stream()
                .filter(pluginEntity -> pluginEntity.getActions() != null)
                .forEach(entity -> updateMaps(entity, nextActionDescriptorMap, nextActionsToPlugin));

        actionDescriptorMap = nextActionDescriptorMap;
        actionsToPlugin = nextActionsToPlugin;
    }

    private static void updateMaps(PluginEntity pluginEntity,
                            Map<String, ActionDescriptor> actionDescriptorsMap,
                            Map<String, String> actionPluginMap) {
        for (ActionDescriptor actionDescriptor : pluginEntity.getActions()) {
            actionDescriptorsMap.put(actionDescriptor.getName(), actionDescriptor);
            actionPluginMap.put(actionDescriptor.getName(), pluginEntity.getPluginCoordinates().getArtifactId());
        }
    }

    public PluginEntity getSystemPlugin() {
        return pluginRepo.findById(SYSTEM_PLUGIN_ID).orElse(null);
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

    public <P extends FlowPlan> Flow addFlowPlanToSystemPlugin(P flowPlan, FlowService<P, ?, ?, ?> flowService,
            FlowPlanValidator<P> flowPlanValidator) {
        PluginCoordinates systemPluginCoordinates = getSystemPluginCoordinates();
        flowService.validateSystemPlanName(flowPlan.getName(), systemPluginCoordinates);
        flowPlan.setSourcePlugin(systemPluginCoordinates);
        addActionSchemas(flowPlan, Map.of());
        flowPlanValidator.validate(flowPlan);
        addFlowPlanToSystemPlugin(flowPlan);
        return flowService.buildAndSaveFlow(flowPlan);
    }

    private void addFlowPlanToSystemPlugin(FlowPlan flowPlan) {
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

        if (flowPlan.getType() == FlowType.DATA_SINK) {
            dataSinkService.removeByName(flowPlanName, systemPlugin.getPluginCoordinates());
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

    @Transactional
    public boolean setPluginVariableValues(@InputArgument PluginCoordinates pluginCoordinates, @InputArgument List<KeyValue> variables) {
        VariableUpdate update = pluginVariableService.setVariableValues(pluginCoordinates, variables);
        if (update.isUpdated()) {
            List<FlowPlan> flowPlans = getPlugin(pluginCoordinates).map(PluginEntity::getFlowPlans).orElse(List.of());
            dataSinkService.rebuildFlows(filterByType(flowPlans, FlowType.DATA_SINK), pluginCoordinates);
            transformFlowService.rebuildFlows(filterByType(flowPlans, FlowType.TRANSFORM), pluginCoordinates);
            restDataSourceService.rebuildFlows(filterByType(flowPlans, FlowType.REST_DATA_SOURCE), pluginCoordinates);
            timedDataSourceService.rebuildFlows(filterByType(flowPlans, FlowType.TIMED_DATA_SOURCE), pluginCoordinates);
        }

        return update.isUpdated();
    }

    private List<FlowPlan> filterByType(List<FlowPlan> flowPlans, FlowType flowType) {
        return flowPlans.stream().filter(fp -> fp.getType() == flowType).toList();
    }

    static String hashRegistration(PluginRegistration pluginRegistration) {
        return DigestUtils.md5Hex(pluginRegistration.toString());
    }

    /**
     * See if the plugin registration is new (a new plugin, a plugin upgrade, an edit
     * without a version update).
     *
     * @param pluginRegistration incoming registration message
     * @return true if this exact registration is a new in some way, false if it is already installed.
     */
    public boolean isRegistrationNew(PluginRegistration pluginRegistration) {
        // Search for this plugin with same version
        Optional<PluginEntity> existingPlugin = getPlugin(pluginRegistration.getPluginCoordinates());
        // If it is not installed, or the registration contents differ, its "new"
        return existingPlugin.map(pluginEntity -> !hashRegistration(pluginRegistration).equals(pluginEntity.getRegistrationHash())).orElse(true);
    }

    @Transactional
    public Result register(PluginRegistration pluginRegistration, IntegrationService integrationService) {
        log.info("{}", pluginRegistration);
        PluginEntity plugin = new PluginEntity(pluginRegistration.toPlugin());
        plugin.setRegistrationHash(hashRegistration(pluginRegistration));
        GroupedFlowPlans groupedFlowPlans = groupPlansByFlowType(pluginRegistration);

        // Validate everything before persisting changes, the plugin should not be considered installed if validation fails
        List<String> validationErrors = validate(plugin, groupedFlowPlans, pluginRegistration.getVariables());
        validationErrors.addAll(integrationService.validate(pluginRegistration.getIntegrationTests()));
        if (!validationErrors.isEmpty()) {
            return Result.builder().success(false).errors(validationErrors).build();
        }

        Optional<PluginEntity> existingPlugin = getPlugin(plugin.getPluginCoordinates());
        // if this plugin group/artifactId/version already exists, don't delete it before overwriting
        if (existingPlugin.isEmpty()) {
            pluginRepo.deleteById(new GroupIdArtifactId(plugin.getPluginCoordinates().getGroupId(), plugin.getPluginCoordinates().getArtifactId()));
        }

        Map<String, ActionDescriptor> newActionDescriptors = new HashMap<>();
        if (plugin.getActions() != null) {
            for (ActionDescriptor actionDescriptor : plugin.getActions()) {
                actionDescriptor.setDocsMarkdown(DocumentationGenerator.generateActionDocs(actionDescriptor));
                newActionDescriptors.put(actionDescriptor.getName(), actionDescriptor);
            }
        }

        // persist the action schemas in the plans
        groupedFlowPlans.allPlans().forEach(plan -> addActionSchemas(plan, newActionDescriptors));
        pluginRepo.save(plugin);

        updateActionMapsNoLockCheck();
        pluginVariableService.saveVariables(plugin.getPluginCoordinates(), pluginRegistration.getVariables());
        upgradeFlows(plugin, groupedFlowPlans);

        for (IntegrationTest integrationTest : pluginRegistration.getIntegrationTests()) {
            integrationService.save(integrationTest);
        }

        return Result.builder().success(true).build();
    }

    /**
     * Check the plugin info, dataSource plans and variables for any errors that should
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
        errors.addAll(dataSinkPlanService.validateFlowPlans(groupedFlowPlans.dataSinkPlans, existingFlowPlans));
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
        dataSinkService.upgradeFlows(sourcePlugin.getPluginCoordinates(), groupedFlowPlans.dataSinkPlans());
    }

    /**
     * Group the dataSource plans by the dataSource plan type. Run validation of each dataSource plan.
     * @param pluginRegistration registration object holding the dataSource plans to save
     * @return lists of each dataSource by type
     */
    private GroupedFlowPlans groupPlansByFlowType(PluginRegistration pluginRegistration) {
        List<TransformFlowPlan> transformFlowPlans = new ArrayList<>();
        List<DataSinkPlan> dataSinkPlans = new ArrayList<>();
        List<RestDataSourcePlan> restDataSourcePlans = new ArrayList<>();
        List<TimedDataSourcePlan> timedDataSourcePlans = new ArrayList<>();

        if (pluginRegistration.getFlowPlans() != null) {
            pluginRegistration.getFlowPlans().forEach(flowPlan -> {
                flowPlan.setSourcePlugin(pluginRegistration.getPluginCoordinates());
                switch (flowPlan) {
                    case TransformFlowPlan plan -> transformFlowPlans.add(plan);
                    case DataSinkPlan plan -> dataSinkPlans.add(plan);
                    case RestDataSourcePlan plan -> restDataSourcePlans.add(plan);
                    case TimedDataSourcePlan plan -> timedDataSourcePlans.add(plan);
                    default -> log.warn("Unknown flow plan type: {}", flowPlan.getClass());
                }
            });
        }

        return new GroupedFlowPlans(transformFlowPlans, dataSinkPlans, restDataSourcePlans, timedDataSourcePlans);
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
        Map<PluginCoordinates, List<DataSink>> dataSinks = dataSinkService.getFlowsGroupedByPlugin();
        Map<PluginCoordinates, List<TransformFlow>> transformFlows = transformFlowService.getFlowsGroupedByPlugin();
        Map<PluginCoordinates, List<RestDataSource>> restDataSources = restDataSourceService.getFlowsGroupedByPlugin();
        Map<PluginCoordinates, List<TimedDataSource>> timedDataSources = timedDataSourceService.getFlowsGroupedByPlugin();

        return getPluginsWithVariables().stream()
                .map(plugin -> toPluginFlows(plugin, dataSinks, transformFlows, restDataSources, timedDataSources))
                .toList();
    }

    private Flows toPluginFlows(PluginEntity plugin,
                                Map<PluginCoordinates, List<DataSink>> dataSinks,
                                Map<PluginCoordinates, List<TransformFlow>> transformFlows,
                                Map<PluginCoordinates, List<RestDataSource>> restDataSources,
                                Map<PluginCoordinates, List<TimedDataSource>> timedDataSources) {

        return Flows.newBuilder()
                .sourcePlugin(plugin.getPluginCoordinates())
                .variables(plugin.getVariables())
                .dataSinks(dataSinks.getOrDefault(plugin.getPluginCoordinates(), Collections.emptyList()))
                .transformFlows(transformFlows.getOrDefault(plugin.getPluginCoordinates(), Collections.emptyList()))
                .restDataSources(restDataSources.getOrDefault(plugin.getPluginCoordinates(), Collections.emptyList()))
                .timedDataSources(timedDataSources.getOrDefault(plugin.getPluginCoordinates(), Collections.emptyList()))
                .build();
    }

    public List<PluginEntity> getPluginsWithDependency(PluginCoordinates pluginCoordinates) {
        return pluginRepo.findPluginsWithDependency(pluginCoordinates);
    }

    public SystemFlowPlans getSystemFlowPlans() {
        SystemFlowPlans systemFlowPlans = new SystemFlowPlans(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        PluginEntity pluginEntity = getSystemPlugin();

        if (pluginEntity != null && pluginEntity.getFlowPlans() != null) {
            for (FlowPlan flowPlan : pluginEntity.getFlowPlans()) {
                switch (flowPlan) {
                    case RestDataSourcePlan p -> systemFlowPlans.getRestDataSources().add(p);
                    case TimedDataSourcePlan p -> systemFlowPlans.getTimedDataSources().add(p);
                    case TransformFlowPlan p -> systemFlowPlans.getTransformPlans().add(p);
                    case DataSinkPlan p -> systemFlowPlans.getDataSinkPlans().add(p);
                    default -> throw new IllegalStateException("Unexpected value: " + flowPlan);
                }
            }
        }
        return systemFlowPlans;
    }

    @Override
    public void updateSnapshot(Snapshot snapshot) {
        snapshot.setPlugins(getInstalledPlugins());
        snapshot.setSystemFlowPlans(getSystemFlowPlans());
    }

    @Override
    public Result resetFromSnapshot(Snapshot snapshot, boolean hardReset) {
        restoreSystemPlugin(snapshot.getSystemFlowPlans(), hardReset);
        return Result.successResult();
    }

    public void importSystemFlows(SystemFlowPlans flowPlans) {
        restoreSystemPlugin(flowPlans, false);
    }

    protected void restoreSystemPlugin(SystemFlowPlans flowPlans, boolean hardReset) {
        PluginEntity systemPlugin = getSystemPlugin();
        if (hardReset) {
            restDataSourceService.removeBySourcePlugin(systemPlugin.getPluginCoordinates());
            timedDataSourceService.removeBySourcePlugin(systemPlugin.getPluginCoordinates());
            transformFlowService.removeBySourcePlugin(systemPlugin.getPluginCoordinates());
            dataSinkService.removeBySourcePlugin(systemPlugin.getPluginCoordinates());
            pluginVariableService.removeVariables(systemPlugin.getPluginCoordinates());
            systemPlugin.setFlowPlans(new ArrayList<>());
        }

        systemPlugin.addOrReplaceFlowPlans(flowPlans.getRestDataSources());
        systemPlugin.addOrReplaceFlowPlans(flowPlans.getTimedDataSources());
        systemPlugin.addOrReplaceFlowPlans(flowPlans.getTransformPlans());
        systemPlugin.addOrReplaceFlowPlans(flowPlans.getDataSinkPlans());

        pluginRepo.save(systemPlugin);
        restDataSourcePlanService.rebuildFlowsForPlugin(systemPlugin.getPluginCoordinates());
        timedDataSourcePlanService.rebuildFlowsForPlugin(systemPlugin.getPluginCoordinates());
        transformFlowPlanService.rebuildFlowsForPlugin(systemPlugin.getPluginCoordinates());
        dataSinkPlanService.rebuildFlowsForPlugin(systemPlugin.getPluginCoordinates());
    }

    @Override
    public int getOrder() {
        return SnapshotRestoreOrder.SYSTEM_PLUGIN_ORDER;
    }

    private List<PluginSnapshot> getInstalledPlugins() {
        return getPlugins().stream().map(PluginSnapshot::new).toList();
    }

    public List<String> canBeUninstalled(PluginEntity plugin) {
        if (plugin == null) {
            return List.of("Plugin not found");
        }

        PluginCoordinates pluginCoordinates = plugin.getPluginCoordinates();
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
        acquireUpdateLock();

        try {
            PluginEntity plugin = getPlugin(pluginCoordinates).orElseThrow();
            pluginCleaners.forEach(pluginCleaner -> pluginCleaner.cleanupFor(plugin));
            pluginRepo.delete(plugin);
            updateActionMapsNoLockCheck();
            revalidateFlows();
        } finally {
            releaseUpdateLock();
        }
    }

    public Optional<ActionDescriptor> getByActionClass(String type) {
        ActionDescriptor actionDescriptor = actionDescriptorMap != null ? actionDescriptorMap.get(type) : null;
        return Optional.ofNullable(actionDescriptor);
    }

    public Collection<ActionDescriptor> getActionDescriptors() {
        return actionDescriptorMap.values();
    }

    public String getPluginWithAction(String clazz) {
        return actionsToPlugin.get(clazz);
    }

    private record GroupedFlowPlans(List<TransformFlowPlan> transformFlowPlans, List<DataSinkPlan> dataSinkPlans, List<RestDataSourcePlan> restDataSourcePlans, List<TimedDataSourcePlan> timedDataSourcePlans) {
        public List<FlowPlan> allPlans() {
            List<FlowPlan> flowPlans = new ArrayList<>();
            addIfNotNull(flowPlans, transformFlowPlans);
            addIfNotNull(flowPlans, dataSinkPlans);
            addIfNotNull(flowPlans, restDataSourcePlans);
            addIfNotNull(flowPlans, timedDataSourcePlans);
            return flowPlans;
        }

        void addIfNotNull(List<FlowPlan> flowPlans, List<? extends FlowPlan> flowPlansToAdd) {
            if (flowPlansToAdd != null) {
                flowPlans.addAll(flowPlansToAdd);
            }
        }
    }

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
        rebuildInvalidFlows(dataSinkService, FlowType.DATA_SINK);
        rebuildInvalidFlows(restDataSourceService, FlowType.REST_DATA_SOURCE);
        rebuildInvalidFlows(timedDataSourceService, FlowType.TIMED_DATA_SOURCE);
        rebuildInvalidFlows(transformFlowService, FlowType.TRANSFORM);

        dataSinkService.validateAllFlows();
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

    private void addActionSchemas(FlowPlan flowPlan, Map<String, ActionDescriptor> actionDescriptorMap) {
        flowPlan.allActionConfigurations().forEach(config -> addActionSchema(config, actionDescriptorMap));
    }

    private void addActionSchema(ActionConfiguration actionConfiguration, Map<String, ActionDescriptor> actionDescriptorMap) {
        String actionType = actionConfiguration.getType();

        // use the new actionDescriptor if it exists otherwise try to look it up
        ActionDescriptor actionDescriptor = actionDescriptorMap.containsKey(actionType) ?
                actionDescriptorMap.get(actionType) : getByActionClass(actionType).orElse(null);

        if (actionDescriptor != null) {
            actionConfiguration.setParameterSchema(ParameterUtil.toJsonNode(actionDescriptor.getSchema()));
        }
    }
}
