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
package org.deltafi.core.domain.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AllArgsConstructor;
import org.deltafi.core.domain.api.types.KeyValue;
import org.deltafi.core.domain.api.types.PluginCoordinates;
import org.deltafi.core.domain.generated.types.PluginVariablesInput;
import org.deltafi.core.domain.generated.types.Variable;
import org.deltafi.core.domain.generated.types.VariableInput;
import org.deltafi.core.domain.plugin.Plugin;
import org.deltafi.core.domain.plugin.PluginCleaner;
import org.deltafi.core.domain.repo.PluginVariableRepo;
import org.deltafi.core.domain.types.PluginVariables;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class PluginVariableService implements PluginCleaner {

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

    Variable preserveValue(VariableInput incoming, List<Variable> existingValues) {
        Variable variable = Variable.newBuilder()
                .name(incoming.getName())
                .defaultValue(incoming.getDefaultValue())
                .dataType(incoming.getDataType())
                .required(incoming.getRequired())
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
        variable.setValue(keyValue.getValue());
    }

    boolean nameMatches(Variable variable, KeyValue keyValue) {
        return Objects.nonNull(keyValue) && Objects.nonNull(keyValue.getKey()) && keyValue.getKey().equals(variable.getName());
    }

    private PluginVariables mapFromInput(PluginVariablesInput pluginVariablesInput) {
        return OBJECT_MAPPER.convertValue(pluginVariablesInput, PluginVariables.class);
    }

    @Override
    public void cleanupFor(Plugin plugin) {
        removeVariables(plugin.getPluginCoordinates());
    }
}
