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
package org.deltafi.core.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AllArgsConstructor;
import org.deltafi.common.types.KeyValue;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.common.types.Variable;
import org.deltafi.core.generated.types.PluginVariablesInput;
import org.deltafi.core.plugin.Plugin;
import org.deltafi.core.plugin.PluginCleaner;
import org.deltafi.core.repo.PluginVariableRepo;
import org.deltafi.core.snapshot.SnapshotRestoreOrder;
import org.deltafi.core.snapshot.Snapshotter;
import org.deltafi.core.snapshot.SystemSnapshot;
import org.deltafi.core.types.PluginVariables;
import org.deltafi.core.types.Result;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class PluginVariableService implements PluginCleaner, Snapshotter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

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

    /**
     * Check if an older version of the variables exist. If they do upgrade the variables,
     * otherwise save the new variables as is.
     * @param pluginVariablesInput variables to insert or update
     */
    public void saveVariables(PluginVariablesInput pluginVariablesInput) {
        String errors = checkNewDefaultValues(pluginVariablesInput);

        if (null != errors) {
            throw new IllegalArgumentException(errors);
        }

        findExisting(pluginVariablesInput.getSourcePlugin())
                .ifPresentOrElse(existingVariables -> replaceVariables(existingVariables, pluginVariablesInput),
                        () -> insertVariables(pluginVariablesInput));
    }

    /**
     * Remove the variables for given plugin
     * @param pluginCoordinates plugin coordinates of the plugin whose variables should be removed
     * @return true if the variables were removed for this plugin
     */
    public boolean removeVariables(PluginCoordinates pluginCoordinates) {
        if (exists(pluginCoordinates)) {
            pluginVariableRepo.deleteById(pluginCoordinates);
            return true;
        }
        return false;
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

        values.forEach(keyValue -> setVariableFromKeyValue(pluginVariables, keyValue));

        pluginVariableRepo.save(pluginVariables);
        return true;
    }

    @Override
    public void updateSnapshot(SystemSnapshot systemSnapshot) {
        // Only include PluginVariables that have the value set
        systemSnapshot.setPluginVariables(pluginVariableRepo.findAll().stream()
                .map(this::filterSetValuesOnly)
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
    }

    PluginVariables filterSetValuesOnly(PluginVariables pluginVariables) {
        PluginVariables result = new PluginVariables();
        result.setSourcePlugin(pluginVariables.getSourcePlugin());
        result.setVariables(filterSetValuesOnly(pluginVariables.getVariables()));

        return !result.getVariables().isEmpty() ? result : null;
    }

    private List<Variable> filterSetValuesOnly(List<Variable> variables) {
        return null != variables ? variables.stream().filter(Variable::hasValue).collect(Collectors.toList()) : List.of();
    }

    @Override
    public Result resetFromSnapshot(SystemSnapshot systemSnapshot, boolean hardReset) {
        if (hardReset) {
            // Unset all the values, a full deleteAll/replace could lead to problems if this snapshot is from a different version of DeltaFi
            pluginVariableRepo.resetAllVariableValues();
        }

        List<PluginVariables> variablesToSet = pluginVariableRepo.findAll();
        List<PluginVariables> snapshotVariables = systemSnapshot.getPluginVariables();

        List<PluginVariables> resetPluginVariables = variablesToSet.stream().map(rollbackVariables -> rollbackValues(rollbackVariables, snapshotVariables)).collect(Collectors.toList());
        
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

    /**
     * Persist the new set of variables
     * @param pluginVariablesInput new set of variables to save
     */
    void insertVariables(PluginVariablesInput pluginVariablesInput) {
        pluginVariableRepo.save(mapFromInput(pluginVariablesInput));
    }

    /**
     * Replace the existing variables with the given set of variables.
     * Preserve any values that were set in the old variables.
     * @param existing plugin variables that were previously stored
     * @param pluginVariablesInput variables to use in the update
     */
    void replaceVariables(PluginVariables existing, PluginVariablesInput pluginVariablesInput) {
        PluginVariables incoming = new PluginVariables();
        incoming.setSourcePlugin(pluginVariablesInput.getSourcePlugin());
        incoming.setVariables(pluginVariablesInput
                .getVariables().stream().map(variableInput -> preserveValue(variableInput, existing.getVariables()))
                .collect(Collectors.toList()));

        pluginVariableRepo.deleteById(existing.getSourcePlugin());
        pluginVariableRepo.save(incoming);
    }

    Variable preserveValue(Variable incoming, List<Variable> existingValues) {
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

    Optional<PluginVariables> findExisting(PluginCoordinates pluginId) {
        return pluginVariableRepo.findIgnoringVersion(pluginId.getGroupId(), pluginId.getArtifactId());
    }

    boolean exists(PluginCoordinates pluginCoordinates) {
        return pluginVariableRepo.existsById(pluginCoordinates);
    }


    private void setVariableFromKeyValue(PluginVariables pluginVariables, KeyValue keyValue) {
        Variable variable = pluginVariables.getVariables().stream()
                .filter(v1 -> nameMatches(v1, keyValue))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Variable name: " + keyValue.getKey() + " was not found in the variables for plugin: " + pluginVariables.getSourcePlugin()));

        String errorMsg = variable.getDataType().validateValue(keyValue.getValue());

        if (null != errorMsg) {
            throw new IllegalArgumentException("Variable named: " + keyValue.getKey() + " has an invalid value for the given type: " + errorMsg);
        }

        variable.setValue(keyValue.getValue());
    }

    boolean nameMatches(Variable variable, KeyValue keyValue) {
        return Objects.nonNull(keyValue) && Objects.nonNull(keyValue.getKey()) && keyValue.getKey().equals(variable.getName());
    }

    private PluginVariables mapFromInput(PluginVariablesInput pluginVariablesInput) {
        return OBJECT_MAPPER.convertValue(pluginVariablesInput, PluginVariables.class);
    }

    private String checkNewDefaultValues(PluginVariablesInput pluginVariablesInput) {
        if (null == pluginVariablesInput || null == pluginVariablesInput.getVariables()) {
            return null;
        }

        List<String> errors = new ArrayList<>();
        for (Variable variable : pluginVariablesInput.getVariables()) {
            String errMsg = variable.getDataType().validateValue(variable.getDefaultValue());
            if (null != errMsg) {
                errors.add("Variable named: " + variable.getName() + " has an invalid default value: " + errMsg);
            }
        }

        return errors.isEmpty() ? null : String.join(",", errors);
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
