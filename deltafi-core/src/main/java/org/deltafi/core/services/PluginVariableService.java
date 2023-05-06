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
package org.deltafi.core.services;

import lombok.AllArgsConstructor;
import org.deltafi.common.types.KeyValue;
import org.deltafi.common.types.Plugin;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.common.types.Variable;
import org.deltafi.core.plugin.PluginCleaner;
import org.deltafi.core.repo.PluginVariableRepo;
import org.deltafi.core.snapshot.SnapshotRestoreOrder;
import org.deltafi.core.snapshot.Snapshotter;
import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.types.PluginVariables;
import org.deltafi.core.types.Result;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@AllArgsConstructor
public class PluginVariableService implements PluginCleaner, Snapshotter {
    private final PluginVariableRepo pluginVariableRepo;

    /**
     * Find the variables with the given PluginCoordinates
     * @param pluginCoordinates identifier of the plugin these variables belong too
     * @return variables for the given plugin
     */
    public List<Variable> getVariablesByPlugin(PluginCoordinates pluginCoordinates) {
        return pluginVariableRepo.findById(pluginCoordinates)
                .map(PluginVariables::getVariables)
                .orElse(Collections.emptyList());
    }

    public List<PluginVariables> getAll() {
        return pluginVariableRepo.findAll();
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
            pluginVariableRepo.deleteById(existing.getSourcePlugin());
        }
    }

    private Variable preserveValue(Variable incoming, List<Variable> existingValues) {
        Variable variable = Variable.newBuilder()
                .name(incoming.getName())
                .defaultValue(incoming.getDefaultValue())
                .dataType(incoming.getDataType())
                .required(incoming.isRequired())
                .description(incoming.getDescription())
                .build();

        existingValues.stream()
                .filter(existing -> existing.getName().equals(incoming.getName()))
                .findFirst().map(Variable::getValue)
                .ifPresent(variable::setValue);

        return variable;
    }

    private void insertVariables(PluginCoordinates pluginCoordinates, List<Variable> variables) {
        PluginVariables incoming = new PluginVariables();
        incoming.setSourcePlugin(pluginCoordinates);
        incoming.setVariables(variables);
        pluginVariableRepo.save(incoming);
    }

    /**
     * Remove the variables for given plugin
     * @param pluginCoordinates plugin coordinates of the plugin whose variables should be removed
     */
    public void removeVariables(PluginCoordinates pluginCoordinates) {
        pluginVariableRepo.deleteById(pluginCoordinates);
    }

    /**
     * Find the given plugin and set the variable values based on the given values
     * @param pluginCoordinates plugin whose variables need updated
     * @param values new values to use in the variables
     */
    public boolean setVariableValues(PluginCoordinates pluginCoordinates, List<KeyValue> values) {
        if (Objects.isNull(values) || values.isEmpty()) {
            return false;
        }

        PluginVariables pluginVariables = pluginVariableRepo.findById(pluginCoordinates).orElseThrow();

        values.forEach(keyValue -> setVariable(pluginVariables, keyValue));

        pluginVariableRepo.save(pluginVariables);
        return true;
    }

    private void setVariable(PluginVariables pluginVariables, KeyValue keyValue) {
        Variable variable = pluginVariables.getVariables().stream()
                .filter(v1 -> nameMatches(v1, keyValue.getKey()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Variable name: " + keyValue.getKey() + " was not found in the variables for plugin: " + pluginVariables.getSourcePlugin()));

        String errorMsg = variable.getDataType().validateValue(keyValue.getValue());

        if (null != errorMsg) {
            throw new IllegalArgumentException("Variable named: " + keyValue.getKey() + " has an invalid value for the given type: " + errorMsg);
        }

        variable.setValue(keyValue.getValue());
    }

    private boolean nameMatches(Variable variable, String key) {
        return key != null && key.equals(variable.getName());
    }

    @Override
    public void updateSnapshot(SystemSnapshot systemSnapshot) {
        // Only include PluginVariables that have the value set
        systemSnapshot.setPluginVariables(pluginVariableRepo.findAll().stream()
                .map(this::filterSetValuesOnly)
                .filter(Objects::nonNull)
                .toList());
    }

    PluginVariables filterSetValuesOnly(PluginVariables pluginVariables) {
        PluginVariables result = new PluginVariables();
        result.setSourcePlugin(pluginVariables.getSourcePlugin());
        result.setVariables(filterSetValuesOnly(pluginVariables.getVariables()));

        return !result.getVariables().isEmpty() ? result : null;
    }

    private List<Variable> filterSetValuesOnly(List<Variable> variables) {
        return null != variables ? variables.stream().filter(Variable::hasValue).toList() : List.of();
    }

    @Override
    public Result resetFromSnapshot(SystemSnapshot systemSnapshot, boolean hardReset) {
        if (hardReset) {
            // Unset all the values, a full deleteAll/replace could lead to problems if this snapshot is from a different version of DeltaFi
            pluginVariableRepo.resetAllVariableValues();
        }

        List<PluginVariables> variablesToSet = pluginVariableRepo.findAll();
        List<PluginVariables> snapshotVariables = systemSnapshot.getPluginVariables();

        List<PluginVariables> resetPluginVariables = variablesToSet.stream().map(rollbackVariables -> rollbackValues(rollbackVariables, snapshotVariables)).toList();
        
        pluginVariableRepo.saveAll(resetPluginVariables);

        return new Result();
    }

    PluginVariables rollbackValues(PluginVariables variablesToSet, List<PluginVariables> valuesToUse) {
        Optional<PluginVariables> valuesT = valuesToUse.stream()
                .filter(snapshot -> snapshot.getSourcePlugin().equals(variablesToSet.getSourcePlugin()))
                .findFirst();

        if (valuesT.isPresent()) {
            PluginVariables valueHolder = valuesT.get();
            valueHolder.getVariables().forEach(rollbackVariable -> rollbackValues(variablesToSet, rollbackVariable));
        }

        return variablesToSet;
    }

    void rollbackValues(PluginVariables pluginVariables, Variable rollbackValue) {
        pluginVariables.getVariables().stream()
                .filter(variable -> variable.getName().equals(rollbackValue.getName()))
                .findFirst().ifPresent(variable -> variable.setValue(rollbackValue.getValue()));
    }

    @Override
    public void cleanupFor(Plugin plugin) {
        removeVariables(plugin.getPluginCoordinates());
    }

    @Override
    public int getOrder() {
        return SnapshotRestoreOrder.PLUGIN_VARIABLE_ORDER;
    }
}
