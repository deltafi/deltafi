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

import lombok.AllArgsConstructor;
import org.deltafi.common.types.KeyValue;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.common.types.Variable;
import org.deltafi.core.types.*;
import org.deltafi.core.repo.PluginVariableRepo;
import org.deltafi.core.types.snapshot.SnapshotRestoreOrder;
import org.deltafi.core.types.snapshot.Snapshot;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.deltafi.core.services.PluginService.*;

@Service
@AllArgsConstructor
public class PluginVariableService implements PluginCleaner, Snapshotter {
    private final PluginVariableRepo pluginVariableRepo;
    private final BuildProperties buildProperties;

    /**
     * Find the variables with the given PluginCoordinates
     * @param pluginCoordinates identifier of the plugin these variables belong too
     * @return variables for the given plugin
     */
    public List<Variable> getVariablesByPlugin(PluginCoordinates pluginCoordinates) {
        return getVariablesByPlugin(pluginCoordinates, true);
    }

    /**
     * Find the variables with the given PluginCoordinates
     * @param pluginCoordinates identifier of the plugin these variables belong too
     * @param includeGlobal include the global parameters in the list
     * @return variables for the given plugin
     */
    public List<Variable> getVariablesByPlugin(PluginCoordinates pluginCoordinates, boolean includeGlobal) {
        List<Variable> pluginVariables = pluginVariableRepo.findBySourcePlugin(pluginCoordinates)
                .map(PluginVariables::getVariables).orElse(Collections.emptyList());

        return !includeGlobal ? pluginVariables : getCombinedVariables(pluginVariables, getGlobalVariables());
    }

    public List<Variable> getCombinedVariables(List<Variable> pluginSpecificVariables, List<Variable> globalVariables) {
        Map<String, Variable> variableMap = pluginSpecificVariables.stream()
                .collect(Collectors.toMap(Variable::getName, Function.identity(), (a,b) -> b, HashMap::new));

        for (Variable variable : globalVariables) {
            Variable pluginSpecificVariable = variableMap.get(variable.getName());
            // plugin specific value is populated use it
            if (pluginSpecificVariable != null && pluginSpecificVariable.hasValue()) {
                continue;
            }

            if (pluginSpecificVariable == null || variable.hasValue() || (!pluginSpecificVariable.hasDefaultValue() && variable.hasDefaultValue())) {
                variableMap.put(variable.getName(), variable);
            }
        }

        return new ArrayList<>(variableMap.values());
    }

    List<Variable> getGlobalVariables() {
        return pluginVariableRepo.findBySourcePlugin(SYSTEM_PLUGIN_GROUP_ID, SYSTEM_PLUGIN_ARTIFACT_ID, buildProperties.getVersion())
                .map(PluginVariables::getVariables).orElse(List.of());
    }

    public void validateAndSaveVariables(PluginCoordinates pluginCoordinates, List<Variable> variables) {
        List<String> errors = validateVariables(variables);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(",", errors));
        }
        saveVariables(pluginCoordinates, variables);
    }

    /**
     * Check if an older version of the variables exist. If they do upgrade the variables,
     * otherwise save the new variables as is.
     * @param variables variables to insert or update
     */
    public void saveVariables(PluginCoordinates pluginCoordinates, List<Variable> variables) {
        if (variables == null || variables.isEmpty()) {
            return;
        }

        findExisting(pluginCoordinates).ifPresentOrElse(
                existingVariables -> replaceVariables(pluginCoordinates, variables, existingVariables),
                () -> insertVariables(pluginCoordinates, variables));
    }

    public List<String> validateVariables(List<Variable> variables) {
        return variables != null ? checkNewDefaultValues(variables) : List.of();
    }

    private List<String> checkNewDefaultValues(List<Variable> variables) {
        List<String> errors = new ArrayList<>();

        for (Variable variable : variables) {
            String errMsg = variable.getDataType().validateValue(variable.getDefaultValue());
            if (null != errMsg) {
                errors.add("Variable named: " + variable.getName() + " has an invalid default value: " + errMsg);
            }
        }

        return errors;
    }

    private Optional<PluginVariables> findExisting(PluginCoordinates pluginId) {
        return pluginVariableRepo.findIgnoringVersion(pluginId.getGroupId(), pluginId.getArtifactId())
                .stream().findFirst();
    }

    /**
     * Replace the existing variables with the given set of variables.
     * Preserve any values that were set in the old variables.
     *
     * @param pluginCoordinates the plugin coordinates
     * @param variables variables to use in the update
     * @param existing plugin variables that were previously stored
     */
    private void replaceVariables(PluginCoordinates pluginCoordinates, List<Variable> variables, PluginVariables existing) {
        // save the new variables before deleting the old variables to guarantee other instances of the plugin get the existing values (either from the new set or old, doesn't matter which)
        insertVariables(pluginCoordinates, variables.stream()
                .map(variable -> preserveValue(variable, existing.getVariables()))
                .toList());

        // remove the old variables if they weren't replaced by the save above
        if (!existing.getSourcePlugin().equals(pluginCoordinates)) {
            pluginVariableRepo.deleteBySourcePlugin(existing.getSourcePlugin());
        }
    }

    private Variable preserveValue(Variable incoming, List<Variable> existingValues) {
        Variable variable = Variable.builder()
                .name(incoming.getName())
                .defaultValue(incoming.getDefaultValue())
                .dataType(incoming.getDataType())
                .required(incoming.isRequired())
                .masked(incoming.isMasked())
                .description(incoming.getDescription())
                .build();

        existingValues.stream()
                .filter(existing -> existing.getName().equals(incoming.getName()))
                .findFirst()
                .ifPresent(existingVariable -> copyPersistedValue(variable, existingVariable));

        return variable;
    }

    private void copyPersistedValue(Variable target, Variable source) {
        // Do not copy a previously masked value into an unmasked variable
        if (target.isNotMasked() && source.isMasked()) {
           return;
        }

        target.setValue(source.getValue());
    }

    private void insertVariables(PluginCoordinates pluginCoordinates, List<Variable> variables) {
        pluginVariableRepo.upsertVariables(pluginCoordinates, variables);
    }

    /**
     * Remove the variables for given plugin
     * @param pluginCoordinates plugin coordinates of the plugin whose variables should be removed
     */
    public void removeVariables(PluginCoordinates pluginCoordinates) {
        pluginVariableRepo.deleteBySourcePlugin(pluginCoordinates);
    }

    /**
     * Find the given plugin and set the variable values based on the given values
     * @param pluginCoordinates plugin whose variables need updated
     * @param values new values to use in the variables
     */
    public VariableUpdate setVariableValues(PluginCoordinates pluginCoordinates, List<KeyValue> values) {
        if (Objects.isNull(values) || values.isEmpty()) {
            return new VariableUpdate(Collections.emptyList());
        }

        PluginVariables pluginVariables = pluginVariableRepo.findBySourcePlugin(pluginCoordinates).orElseThrow();

        VariableUpdate variableUpdate = new VariableUpdate(pluginVariables.getVariables());
        for (KeyValue value : values) {
            if (setVariable(pluginVariables, value)) {
                variableUpdate.setUpdated(true);
            }
        }

        if (variableUpdate.isUpdated()) {
            pluginVariableRepo.save(pluginVariables);
        }

        return variableUpdate;
    }

    private boolean setVariable(PluginVariables pluginVariables, KeyValue keyValue) {
        Variable variable = pluginVariables.getVariables().stream()
                .filter(v1 -> nameMatches(v1, keyValue.getKey()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Variable name: " + keyValue.getKey() + " was not found in the variables for plugin: " + pluginVariables.getSourcePlugin()));

        String errorMsg = variable.getDataType().validateValue(keyValue.getValue());

        if (null != errorMsg) {
            throw new IllegalArgumentException("Variable named: " + keyValue.getKey() + " has an invalid value for the given type: " + errorMsg);
        }

        if (Objects.equals(variable.getValue(), keyValue.getValue())) {
            return false;
        }

        variable.setValue(keyValue.getValue());
        return true;
    }

    private boolean nameMatches(Variable variable, String key) {
        return key != null && key.equals(variable.getName());
    }

    @Override
    public void updateSnapshot(Snapshot snapshot) {
        // Only include PluginVariables that have the value set
        snapshot.setPluginVariables(pluginVariableRepo.findAll().stream()
                .map(this::filterSetValuesOnly)
                .filter(Objects::nonNull)
                .toList());
    }

    PluginVariables filterSetValuesOnly(PluginVariables pluginVariables) {
        PluginVariables result = new PluginVariables();
        PluginCoordinates pluginCoordinates = pluginVariables.getSourcePlugin();
        result.setSourcePlugin(pluginVariables.getSourcePlugin());

        if (SYSTEM_PLUGIN_ID.equals(new GroupIdArtifactId(pluginCoordinates.getGroupId(), pluginCoordinates.getArtifactId()))) {
            result.setVariables(pluginVariables.getVariables());
        } else {
            result.setVariables(filterSetValuesOnly(pluginVariables.getVariables()));
        }

        return !result.getVariables().isEmpty() ? result : null;
    }

    private List<Variable> filterSetValuesOnly(List<Variable> variables) {
        return null != variables ? variables.stream().filter(Variable::hasValue).toList() : List.of();
    }

    @Override
    public Result resetFromSnapshot(Snapshot snapshot, boolean hardReset) {
        if (hardReset) {
            // Unset all the values, a full deleteAll/replace could lead to problems if this snapshot is from a different version of DeltaFi
            // Masked variables are left in place b/c imported snapshots will not have those values
            pluginVariableRepo.resetAllUnmaskedVariableValues();
        }

        List<PluginVariables> storedPluginVariables = pluginVariableRepo.findAll();
        Map<PluginCoordinates, PluginVariables> snapshotVariables = snapshot.getPluginVariables().stream()
                .collect(Collectors.toMap(PluginVariables::getSourcePlugin, Function.identity()));

        storedPluginVariables.forEach(targetVariables -> rollbackValues(targetVariables, snapshotVariables.get(targetVariables.getSourcePlugin())));
        
        pluginVariableRepo.saveAll(storedPluginVariables);

        return new Result();
    }

    void rollbackValues(PluginVariables targetVariables, PluginVariables sourceVariables) {
        if (sourceVariables == null) {
            return;
        }

        Map<String, Variable> sourceVariableMap = sourceVariables.getVariables().stream().collect(Collectors.toMap(Variable::getName, Function.identity()));
        targetVariables.getVariables().forEach(targetVariable -> copySnapshotVariable(targetVariable, sourceVariableMap.get(targetVariable.getName())));
    }

    void copySnapshotVariable(Variable target, Variable source) {
        if (source == null) {
            return;
        }

        // ignore masked variables when resetting from a snapshot unless they have a real value
        // do not copy a variable that was masked into an unmasked target
        if (source.isMasked() && (Variable.MASK_STRING.equals(source.getValue()) || target.isNotMasked())) {
            return;
        }

        target.setValue(source.getValue());
    }

    @Override
    public void cleanupFor(PluginEntity plugin) {
        removeVariables(plugin.getPluginCoordinates());
    }

    @Override
    public int getOrder() {
        return SnapshotRestoreOrder.PLUGIN_VARIABLE_ORDER;
    }
}
