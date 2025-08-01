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

import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.common.types.Variable;
import org.deltafi.core.repo.PluginVariableRepo;
import org.deltafi.core.types.snapshot.Snapshot;
import org.deltafi.core.types.PluginVariables;
import org.deltafi.core.types.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class PluginVariableServiceTest {

    private static final Variable KEEP_VARIABLE = Variable.builder().name("keep").value("value").build();
    private static final Variable SKIP_VARIABLE = Variable.builder().name("skip").build();

    @InjectMocks
    PluginVariableService pluginVariableService;

    @Mock
    PluginVariableRepo pluginVariableRepo;

    @Test
    void updateSnapshot() {
        // one value is set and should be included in the snapshot
        PluginVariables pluginVariablesToKeep = pluginVariables("a");
        pluginVariablesToKeep.setVariables(List.of(KEEP_VARIABLE, SKIP_VARIABLE));

        // no custom values so this will not be in the snapshot
        PluginVariables pluginVariablesToSkip = pluginVariables("b");
        pluginVariablesToSkip.setVariables(List.of(SKIP_VARIABLE));

        Mockito.when(pluginVariableRepo.findAll()).thenReturn(List.of(pluginVariablesToKeep, pluginVariablesToSkip));

        Snapshot snapshot = new Snapshot();
        pluginVariableService.updateSnapshot(snapshot);

        assertThat(snapshot.getPluginVariables()).hasSize(1);
        PluginVariables fromSnapshot = snapshot.getPluginVariables().getFirst();
        assertThat(fromSnapshot.getSourcePlugin()).isEqualTo(pluginVariablesToKeep.getSourcePlugin());
        assertThat(fromSnapshot.getVariables()).hasSize(1).contains(KEEP_VARIABLE);
    }

    @Test
    void resetFromSnapshot() {
        PluginVariables snapshotVariables = pluginVariables("a");
        snapshotVariables.setVariables(List.of(KEEP_VARIABLE));

        Snapshot snapshot = new Snapshot();
        snapshot.setPluginVariables(List.of(snapshotVariables));

        PluginVariables storedPluginA = pluginVariables("a");
        // keep value should be restored to 'value'
        storedPluginA.setVariables(List.of(Variable.builder().name("keep").value("updated").build(), SKIP_VARIABLE));

        // nothing stored in snapshot shouldn't change
        PluginVariables storedPluginB = pluginVariables("b");
        storedPluginB.setVariables(List.of(SKIP_VARIABLE));

        PluginVariables restoredPluginA = pluginVariables("a");
        restoredPluginA.setVariables(List.of(KEEP_VARIABLE, SKIP_VARIABLE));

        Mockito.when(pluginVariableRepo.findAll()).thenReturn(List.of(storedPluginA, storedPluginB));

        Result result = pluginVariableService.resetFromSnapshot(snapshot, true);

        Mockito.verify(pluginVariableRepo).resetAllUnmaskedVariableValues();

        Mockito.verify(pluginVariableRepo).saveAll(List.of(restoredPluginA, storedPluginB));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getInfo()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void combineVars_usePluginSpecific() {
        Variable pluginVar = Variable.builder().name("var").value("pluginValue").build();
        Variable globalVar = Variable.builder().name("var").value("globalValue").build();

        assertThat(testCombine(pluginVar, globalVar)).hasSize(1).containsExactly(pluginVar);
    }

    @Test
    void combineVars_useGlobal() {
        Variable pluginVar = Variable.builder().name("var").defaultValue("defaultValue").build();
        Variable globalVar = Variable.builder().name("var").value("globalValue").defaultValue("globalDefaultValue").build();

        assertThat(testCombine(pluginVar, globalVar)).hasSize(1).containsExactly(globalVar);
    }

    @Test
    void combineVars_usePluginDefault() {
        Variable pluginVar = Variable.builder().name("var").defaultValue("pluginDefaultValue").build();
        Variable globalVar = Variable.builder().name("var").defaultValue("globalDefaultValue").build();

        assertThat(testCombine(pluginVar, globalVar)).hasSize(1).containsExactly(pluginVar);
    }

    @Test
    void combineVars_useGlobalDefault() {
        Variable pluginVar = Variable.builder().name("var").build();
        Variable globalVar = Variable.builder().name("var").defaultValue("globalDefaultValue").build();

        assertThat(testCombine(pluginVar, globalVar)).hasSize(1).containsExactly(globalVar);
    }

    private List<Variable> testCombine(Variable pluginVar, Variable globalVar) {
        return pluginVariableService.getCombinedVariables(List.of(pluginVar), List.of(globalVar));
    }

    PluginVariables pluginVariables(String artifact) {
        PluginVariables pluginVariablesToKeep = new PluginVariables();
        pluginVariablesToKeep.setVariables(new ArrayList<>());
        pluginVariablesToKeep.setSourcePlugin(new PluginCoordinates("a", artifact, "1"));
        return pluginVariablesToKeep;
    }
}