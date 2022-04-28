package org.deltafi.core.domain.plugin;

import lombok.AllArgsConstructor;
import org.deltafi.core.domain.api.types.PluginCoordinates;
import org.deltafi.core.domain.generated.types.ActionDescriptor;
import org.deltafi.core.domain.generated.types.Flows;
import org.deltafi.core.domain.generated.types.Result;
import org.deltafi.core.domain.services.*;
import org.deltafi.core.domain.types.EgressFlow;
import org.deltafi.core.domain.types.IngressFlow;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class PluginRegistryService {

    private final IngressFlowPlanService ingressFlowPlanService;
    private final EgressFlowPlanService egressFlowPlanService;
    private final IngressFlowService ingressFlowService;
    private final EgressFlowService egressFlowService;
    private final PluginVariableService pluginVariableService;
    private final ActionSchemaService actionSchemaService;
    private final PluginRepository pluginRepository;
    private final PluginValidator pluginValidator;
    private final RedisService redisService;

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

        return getPluginsWithVariables().stream()
                .map(plugin -> toPluginFlows(plugin, ingressFlows, egressFlows))
                .collect(Collectors.toList());
    }

    private Flows toPluginFlows(Plugin plugin, Map<PluginCoordinates, List<IngressFlow>> ingressFlows, Map<PluginCoordinates, List<EgressFlow>> egressFlows) {
        return Flows.newBuilder()
                .sourcePlugin(plugin.getPluginCoordinates())
                .variables(plugin.getVariables())
                .ingressFlows(ingressFlows.getOrDefault(plugin.getPluginCoordinates(), Collections.emptyList()))
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

        PluginCoordinates pluginCoordinates = plugin.getPluginCoordinates();
        List<String> errors = new ArrayList<>();

        List<String> ingressFlows = ingressFlowService.findRunningFromPlugin(pluginCoordinates);
        if (!ingressFlows.isEmpty()) {
            errors.add("The plugin has created the following ingress flows which are still running: " + String.join(", ", ingressFlows));
        }

        List<String> egressFlows = egressFlowService.findRunningFromPlugin(pluginCoordinates);
        if (!egressFlows.isEmpty()) {
            errors.add("The plugin has created the following egress flows which are still running: " + String.join(", ", egressFlows));
        }

        // If this plugin is the dependency of another plugin, then it cannot be removed.
        String dependents = getPluginsWithDependency(pluginCoordinates).stream()
                .map(dependent -> dependent.getPluginCoordinates().toString()).collect(Collectors.joining(", "));

        if (!dependents.isEmpty()) {
            errors.add("The following plugins depend on this plugin: " + dependents);
        }

        return errors;
    }

    private void doUninstallPlugin(Plugin plugin) {
        // remove flow plans where this is the source plugin
        removeFlowsAndFlowPlans(plugin.getPluginCoordinates());
        removeVariables(plugin.getPluginCoordinates());

        // remove action schema registrations
        List<String> actionNames = removeActionSchemas(plugin);

        if (!actionNames.isEmpty()) {
            // remove any redis queues
            redisService.dropQueues(actionNames);
        }

        // TODO: TBD: remove plugin property sets

        // remove the plugin from the registry
        removePlugin(plugin);
    }

    private void removeVariables(PluginCoordinates sourcePlugin) {
        pluginVariableService.removeVariables(sourcePlugin);
    }

    private void removeFlowsAndFlowPlans(PluginCoordinates sourcePlugin) {
        ingressFlowPlanService.removeFlowsAndPlansBySourcePlugin(sourcePlugin);
        egressFlowPlanService.removeFlowsAndPlansBySourcePlugin(sourcePlugin);
    }


    private List<String> removeActionSchemas(Plugin plugin) {
        if (!Objects.isNull(plugin.getActions())) {
            List<String> actionNames = plugin.getActions().stream()
                    .map(ActionDescriptor::getName)
                    .collect(Collectors.toList());
            actionSchemaService.removeAllInList(actionNames);
            return actionNames;
        }
        return Collections.emptyList();
    }
}
