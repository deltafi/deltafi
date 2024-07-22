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
package org.deltafi.core.snapshot;

import org.deltafi.common.types.Variable;
import org.deltafi.core.repo.SystemSnapshotRepo;
import org.deltafi.core.services.SystemSnapshotService;
import org.deltafi.core.types.PluginVariables;
import org.deltafi.core.types.snapshot.SystemSnapshot;
import org.deltafi.core.util.Util;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SystemSnapshotServiceTest {

    @InjectMocks
    SystemSnapshotService systemSnapshotService;

    @Mock
    SystemSnapshotRepo systemSnapshotRepo;

    @Test
    void testGetWithMaskedVariables() {
        PluginVariables pluginVariables = new PluginVariables();
        Variable one = Util.buildOriginalVariable("notMasked");
        Variable two = Util.buildOriginalVariable("masked");
        two.setMasked(true);
        pluginVariables.setVariables(List.of(one, two));

        PluginVariables pluginVariables2 = new PluginVariables();
        Variable three = Util.buildOriginalVariable("notMasked");
        Variable four = Util.buildOriginalVariable("masked");
        four.setMasked(true);
        pluginVariables2.setVariables(List.of(three, four));

        Variable afterMask = Util.buildOriginalVariable("masked");
        afterMask.setMasked(true);
        afterMask.setValue(Variable.MASK_STRING);
        afterMask.setDefaultValue(Variable.MASK_STRING);

        SystemSnapshot originalSnapshot = new SystemSnapshot();
        originalSnapshot.setPluginVariables(List.of(pluginVariables, pluginVariables2));

        Mockito.when(systemSnapshotRepo.findById("abc")).thenReturn(Optional.of(originalSnapshot));
        SystemSnapshot systemSnapshot = systemSnapshotService.getWithMaskedVariables("abc");

        assertThat(systemSnapshot.getPluginVariables()).hasSize(2);
        assertThat(systemSnapshot.getPluginVariables().getFirst().getVariables()).hasSize(2);
        assertThat(systemSnapshot.getPluginVariables().getFirst().getVariables()).contains(one);
        assertThat(systemSnapshot.getPluginVariables().getFirst().getVariables()).doesNotContain(two);
        assertThat(systemSnapshot.getPluginVariables().getFirst().getVariables()).contains(afterMask);

        assertThat(systemSnapshot.getPluginVariables().get(1).getVariables()).hasSize(2);
        assertThat(systemSnapshot.getPluginVariables().get(1).getVariables()).contains(three);
        assertThat(systemSnapshot.getPluginVariables().get(1).getVariables()).doesNotContain(four);
        assertThat(systemSnapshot.getPluginVariables().get(1).getVariables()).contains(afterMask);
    }

    @Test
    void testImportSnapshot() {
        PluginVariables pluginVariables = new PluginVariables();
        Variable one = Util.buildOriginalVariable("notMasked");
        Variable two = Util.buildOriginalVariable("masked");
        two.setMasked(true);
        two.setValue(Variable.MASK_STRING);
        pluginVariables.setVariables(List.of(one, two));

        PluginVariables pluginVariables2 = new PluginVariables();
        Variable three = Util.buildOriginalVariable("notMasked");
        Variable four = Util.buildOriginalVariable("masked");
        four.setValue(Variable.MASK_STRING);
        four.setValue("clear");
        four.setMasked(true);
        pluginVariables2.setVariables(List.of(three, four));

        PluginVariables pluginVariables3 = new PluginVariables();
        Variable five = Util.buildOriginalVariable("masked");
        five.setValue(Variable.MASK_STRING);
        five.setMasked(true);
        pluginVariables3.setVariables(List.of(five));

        SystemSnapshot systemSnapshot = new SystemSnapshot();
        systemSnapshot.setPluginVariables(List.of(pluginVariables, pluginVariables2, pluginVariables3));

        Mockito.when(systemSnapshotRepo.save(systemSnapshot)).thenAnswer(a -> a.getArgument(0));
        SystemSnapshot imported = systemSnapshotService.importSnapshot(systemSnapshot);


        // pluginVariables3 was pruned because it was empty after the masked variable is removed
        assertThat(imported.getPluginVariables()).hasSize(2);

        assertThat(imported.getPluginVariables().getFirst().getVariables()).hasSize(1).contains(one);
        assertThat(imported.getPluginVariables().get(1).getVariables()).hasSize(1).contains(three);
    }
}