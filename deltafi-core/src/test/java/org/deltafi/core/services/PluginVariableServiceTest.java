/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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

import org.assertj.core.api.Assertions;
import org.deltafi.common.types.KeyValue;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.common.types.Variable;
import org.deltafi.common.types.VariableDataType;
import org.deltafi.core.repo.PluginVariableRepo;
import org.deltafi.core.types.PluginVariables;
import org.deltafi.core.types.Result;
import org.deltafi.core.types.VariableUpdate;
import org.deltafi.core.types.snapshot.Snapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class PluginVariableServiceTest {

    private static final Variable KEEP_VARIABLE = Variable.builder().name("keep").value("value").build();
    private static final String UPDATE_VARIABLE = "update";
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
    void resetFromSnapshot_existingRow_mergesValues() {
        PluginVariables snapshotVariables = pluginVariables("a");
        snapshotVariables.setVariables(List.of(KEEP_VARIABLE));

        Snapshot snapshot = new Snapshot();
        snapshot.setPluginVariables(List.of(snapshotVariables));

        PluginVariables storedPluginA = pluginVariables("a");
        storedPluginA.setVariables(List.of(Variable.builder().name("keep").value("updated").build(), SKIP_VARIABLE));

        Mockito.when(pluginVariableRepo.findIgnoringVersion("a", "a")).thenReturn(List.of(storedPluginA));

        Result result = pluginVariableService.resetFromSnapshot(snapshot, true);

        Mockito.verify(pluginVariableRepo).resetAllUnmaskedVariableValues();

        PluginVariables expectedSaved = pluginVariables("a");
        expectedSaved.setVariables(List.of(KEEP_VARIABLE, SKIP_VARIABLE));
        Mockito.verify(pluginVariableRepo).save(expectedSaved);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getInfo()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void resetFromSnapshot_noExistingRow_writesSnapshotRowForLaterMerge() {
        // Bug fix: snapshot carries values for a plugin that has no plugin_variables row yet
        // (the deployer just queued it as PENDING). Persist the snapshot row so that when the
        // plugin registers, saveVariables / preserveValue picks up the values naturally.
        PluginVariables snapshotVariables = pluginVariables("a");
        snapshotVariables.setVariables(List.of(KEEP_VARIABLE));

        Snapshot snapshot = new Snapshot();
        snapshot.setPluginVariables(List.of(snapshotVariables));

        Mockito.when(pluginVariableRepo.findIgnoringVersion("a", "a")).thenReturn(List.of());

        Result result = pluginVariableService.resetFromSnapshot(snapshot, false);

        String json = PluginVariableService.toJsonString(List.of(KEEP_VARIABLE));
        Mockito.verify(pluginVariableRepo).upsertVariables(Mockito.any(UUID.class), Mockito.eq("a"), Mockito.eq("a"), Mockito.eq("1"), Mockito.eq(json));
        Mockito.verify(pluginVariableRepo, Mockito.never()).save(Mockito.any(PluginVariables.class));
        Mockito.verify(pluginVariableRepo, Mockito.never()).resetAllUnmaskedVariableValues();
        Mockito.verify(pluginVariableRepo, Mockito.never()).deleteBySourcePlugin(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void resetFromSnapshot_existingDifferentVersion_mergesValuesWithoutDestroyingRow() {
        // Soft reset must not delete or replace the live row when the snapshot's coords differ
        // from the installed version. Merge the snapshot's values into the existing row by name;
        // preserveValue handles any subsequent version transition at registration time.
        PluginVariables snapshotVariables = pluginVariables("a"); // version "1"
        snapshotVariables.setVariables(List.of(KEEP_VARIABLE));

        Snapshot snapshot = new Snapshot();
        snapshot.setPluginVariables(List.of(snapshotVariables));

        PluginVariables storedDifferentVersion = new PluginVariables();
        storedDifferentVersion.setSourcePlugin(new PluginCoordinates("a", "a", "2"));
        storedDifferentVersion.setVariables(List.of(Variable.builder().name("keep").value("old").build(), SKIP_VARIABLE));

        Mockito.when(pluginVariableRepo.findIgnoringVersion("a", "a")).thenReturn(List.of(storedDifferentVersion));

        Result result = pluginVariableService.resetFromSnapshot(snapshot, false);

        PluginVariables expectedSaved = new PluginVariables();
        expectedSaved.setSourcePlugin(new PluginCoordinates("a", "a", "2"));
        expectedSaved.setVariables(List.of(KEEP_VARIABLE, SKIP_VARIABLE));
        Mockito.verify(pluginVariableRepo).save(expectedSaved);
        Mockito.verify(pluginVariableRepo, Mockito.never()).deleteBySourcePlugin(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
        Mockito.verify(pluginVariableRepo, Mockito.never()).upsertVariables(Mockito.any(UUID.class), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString());

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void resetFromSnapshot_skipsMaskedPlaceholderForNewRow() {
        // Imported snapshots may carry Variable.MASK_STRING placeholders for masked variables.
        // When persisting a snapshot row for a plugin with no existing definitions, those
        // placeholders must not be written as if they were real values.
        Variable maskedPlaceholder = Variable.builder().name("secret").masked(true).value(Variable.MASK_STRING).build();
        Variable realValue = Variable.builder().name("plain").value("hello").build();
        PluginVariables snapshotVariables = pluginVariables("a");
        snapshotVariables.setVariables(List.of(maskedPlaceholder, realValue));

        Snapshot snapshot = new Snapshot();
        snapshot.setPluginVariables(List.of(snapshotVariables));

        Mockito.when(pluginVariableRepo.findIgnoringVersion("a", "a")).thenReturn(List.of());

        pluginVariableService.resetFromSnapshot(snapshot, false);

        String json = PluginVariableService.toJsonString(List.of(realValue));
        Mockito.verify(pluginVariableRepo).upsertVariables(Mockito.any(UUID.class), Mockito.eq("a"), Mockito.eq("a"), Mockito.eq("1"), Mockito.eq(json));
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

    @Test
    void testSetVariables() {
        PluginCoordinates coords = setupSetVariableTest();

        VariableUpdate updates = pluginVariableService.setVariableValues(coords, List.of(new KeyValue(UPDATE_VARIABLE, "new-value")));
        assertThat(updates.isUpdated()).isTrue();
        assertThat(updates.getVariables()).hasSize(3);
        assertThat(updates.getUpdatedVariables()).hasSize(1);
        VariableUpdate.Result result = updates.getUpdatedVariables().getFirst();
        assertThat(result.changed()).isTrue();
        assertThat(result.variable().getName()).isEqualTo(UPDATE_VARIABLE);
        assertThat(result.variable().getValue()).isEqualTo("new-value");
    }

    @Test
    void testSetVariables_noChanges() {
        PluginCoordinates coords = setupSetVariableTest();

        VariableUpdate updates = pluginVariableService.setVariableValues(coords, List.of(new KeyValue(UPDATE_VARIABLE, UPDATE_VARIABLE)));
        assertThat(updates.isUpdated()).isFalse();
        assertThat(updates.getVariables()).hasSize(3);
        assertThat(updates.getUpdatedVariables()).hasSize(1);
        VariableUpdate.Result result = updates.getUpdatedVariables().getFirst();
        assertThat(result.changed()).isFalse();
        assertThat(result.variable().getName()).isEqualTo(UPDATE_VARIABLE);
        assertThat(result.variable().getValue()).isEqualTo(UPDATE_VARIABLE);
    }

    @Test
    void testSetVariable_missingKey() {
        PluginCoordinates coords = setupSetVariableTest();

        List<KeyValue> updates = List.of(new KeyValue("missing", "value"));
        Assertions.assertThatThrownBy(() -> pluginVariableService.setVariableValues(coords, updates))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Variable named 'missing' was not found in the variables for plugin 'a:a:1'");
    }

    private PluginCoordinates setupSetVariableTest() {
        PluginCoordinates coords = new PluginCoordinates();
        PluginVariables pluginVariables = pluginVariables("a");

        pluginVariables.setVariables(List.of(KEEP_VARIABLE, Variable.builder().name(UPDATE_VARIABLE).value(UPDATE_VARIABLE).dataType(VariableDataType.STRING).build(), SKIP_VARIABLE));

        Mockito.when(pluginVariableRepo.findBySourcePlugin(coords)).thenReturn(Optional.of(pluginVariables));
        return coords;
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