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

import org.deltafi.common.types.FlowType;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.common.types.TransformFlowPlan;
import org.deltafi.common.types.Variable;
import org.deltafi.core.generated.types.FlowConfigError;
import org.deltafi.core.generated.types.FlowErrorType;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.generated.types.FlowStatus;
import org.deltafi.core.repo.TransformFlowRepo;
import org.deltafi.core.types.Flow;
import org.deltafi.core.types.Result;
import org.deltafi.core.types.TransformFlow;
import org.deltafi.core.types.snapshot.FlowSnapshot;
import org.deltafi.core.types.snapshot.Snapshot;
import org.deltafi.core.types.snapshot.TransformFlowSnapshot;
import org.deltafi.core.validation.FlowValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransformFlowServiceTest {

    @Mock
    TransformFlowRepo transformFlowRepo;

    @Mock
    FlowValidator flowValidator;

    @Mock
    BuildProperties buildProperties;

    @Mock
    PluginVariableService pluginVariableService;

    @InjectMocks
    TransformFlowService transformFlowService;

    @Captor
    ArgumentCaptor<Collection<TransformFlow>> flowCaptor;

    @Captor
    ArgumentCaptor<TransformFlow> singleFlowCaptor;

    @Mock
    FlowCacheService flowCacheService;

    @BeforeEach
    void setUp() {
        Mockito.lenient().when(buildProperties.getVersion()).thenReturn("1.0.0");
    }

    @Test
    void buildFlow() {
        TransformFlow running = transformFlow("running", FlowState.RUNNING, true);
        TransformFlow stopped = transformFlow("stopped", FlowState.STOPPED, false);
        Map<String, TransformFlow> existingFlows = Map.of(running.getName(), running, stopped.getName(), stopped);

        Mockito.when(flowValidator.validate(Mockito.any())).thenReturn(Collections.emptyList());

        TransformFlowPlan runningFlowPlan = new TransformFlowPlan("running", FlowType.TRANSFORM, "yep");
        TransformFlowPlan stoppedFlowPlan = new TransformFlowPlan("stopped", FlowType.TRANSFORM, "naw");

        TransformFlow runningTransformFlow = transformFlowService.buildFlow(existingFlows, runningFlowPlan, Collections.emptyList());
        TransformFlow stoppedTransformFlow = transformFlowService.buildFlow(existingFlows, stoppedFlowPlan, Collections.emptyList());

        assertThat(runningTransformFlow.isRunning()).isTrue();
        assertThat(runningTransformFlow.isTestMode()).isTrue();
        assertThat(stoppedTransformFlow.isRunning()).isFalse();
        assertThat(stoppedTransformFlow.isTestMode()).isFalse();
    }

    @Test
    void updateSnapshot() {
        List<Flow> flows = new ArrayList<>();
        flows.add(transformFlow("a", FlowState.RUNNING, false));
        flows.add(transformFlow("b", FlowState.STOPPED, false));
        flows.add(transformFlow("c", FlowState.STOPPED, true, false));

        Mockito.when(flowCacheService.flowsOfType(FlowType.TRANSFORM)).thenReturn(flows);

        Snapshot snapshot = new Snapshot();
        transformFlowService.updateSnapshot(snapshot);

        assertThat(snapshot.getTransformFlows()).hasSize(3);

        Map<String, TransformFlowSnapshot> transformFlowSnapshotMap = snapshot.getTransformFlows().stream()
                .collect(Collectors.toMap(FlowSnapshot::getName, Function.identity()));

        TransformFlowSnapshot aFlowSnapshot = transformFlowSnapshotMap.get("a");
        assertThat(aFlowSnapshot.isRunning()).isTrue();
        assertThat(aFlowSnapshot.isTestMode()).isFalse();

        TransformFlowSnapshot bFlowSnapshot = transformFlowSnapshotMap.get("b");
        assertThat(bFlowSnapshot.isRunning()).isFalse();
        assertThat(bFlowSnapshot.isTestMode()).isFalse();

        TransformFlowSnapshot cFlowSnapshot = transformFlowSnapshotMap.get("c");
        assertThat(cFlowSnapshot.isRunning()).isFalse();
        assertThat(cFlowSnapshot.isTestMode()).isTrue();
    }

    @Test
    void testResetFromSnapshot() {
        TransformFlow running = transformFlow("running", FlowState.RUNNING, true);
        TransformFlow stopped = transformFlow("stopped", FlowState.STOPPED, false);
        TransformFlow invalid = transformFlow("invalid", FlowState.STOPPED, false);

        Snapshot snapshot = new Snapshot();
        snapshot.setTransformFlows(List.of(
                new TransformFlowSnapshot("running", true, false),
                new TransformFlowSnapshot("stopped", true, true),
                new TransformFlowSnapshot("invalid", true, false),
                new TransformFlowSnapshot("missing", true, true)));

        Mockito.when(flowCacheService.flowsOfType(FlowType.TRANSFORM)).thenReturn(List.of(running, stopped, invalid));

        Result result = transformFlowService.resetFromSnapshot(snapshot, true);

        Mockito.verify(transformFlowRepo).saveAll(flowCaptor.capture());

        Map<String, TransformFlow> updatedFlows = flowCaptor.getValue().stream()
                .collect(Collectors.toMap(Flow::getName, Function.identity()));

        assertThat(updatedFlows).hasSize(3);

        // running is set back to running after state was reset
        assertThat(updatedFlows.get("running").isRunning()).isTrue();
        assertThat(updatedFlows.get("running").isTestMode()).isFalse();

        // stopped dataSource should be restarted since it was marked as running in the snapshot, it should also be in test mode
        assertThat(updatedFlows.get("stopped").isRunning()).isTrue();
        assertThat(updatedFlows.get("stopped").isTestMode()).isTrue();

        // flow named invalid is set back to a running state
        assertThat(updatedFlows.get("running").isRunning()).isTrue();
        assertThat(updatedFlows.get("running").isInvalid()).isTrue();
        assertThat(updatedFlows.get("running").isTestMode()).isFalse();

        // verify placeholder was upserted for missing flow (with system-plugin since no sourcePlugin in old snapshot)
        Mockito.verify(transformFlowRepo).upsertFlowFromSnapshot(singleFlowCaptor.capture());
        TransformFlow missingPlaceholder = singleFlowCaptor.getValue();
        assertThat(missingPlaceholder.getName()).isEqualTo("missing");
        assertThat(missingPlaceholder.getFlowStatus().getPlaceholder()).isTrue();
        assertThat(missingPlaceholder.isRunning()).isTrue();
        assertThat(missingPlaceholder.isTestMode()).isTrue();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getInfo()).hasSize(1)
                .anyMatch(info -> info.contains("Created placeholder for flow missing"));
    }

    @Test
    void testResetFromSnapshot_createsPlaceholderForMissingFlowWithSourcePlugin() {
        PluginCoordinates pluginCoordinates = PluginCoordinates.builder()
                .groupId("org.deltafi")
                .artifactId("test-plugin")
                .version("1.0.0")
                .build();

        TransformFlowSnapshot runningSnapshot = new TransformFlowSnapshot("pending-running", true, false);
        runningSnapshot.setSourcePlugin(pluginCoordinates);

        TransformFlowSnapshot stoppedSnapshot = new TransformFlowSnapshot("pending-stopped", false, true);
        stoppedSnapshot.setSourcePlugin(pluginCoordinates);

        Snapshot snapshot = new Snapshot();
        snapshot.setTransformFlows(List.of(runningSnapshot, stoppedSnapshot));

        Mockito.when(flowCacheService.flowsOfType(FlowType.TRANSFORM)).thenReturn(List.of());

        Result result = transformFlowService.resetFromSnapshot(snapshot, true);

        Mockito.verify(transformFlowRepo, Mockito.times(2)).upsertFlowFromSnapshot(singleFlowCaptor.capture());

        Map<String, TransformFlow> savedFlows = singleFlowCaptor.getAllValues().stream()
                .collect(Collectors.toMap(Flow::getName, Function.identity()));

        assertThat(savedFlows).hasSize(2);

        // Verify running placeholder
        TransformFlow runningPlaceholder = savedFlows.get("pending-running");
        assertThat(runningPlaceholder).isNotNull();
        assertThat(runningPlaceholder.isRunning()).isTrue();
        assertThat(runningPlaceholder.isTestMode()).isFalse();
        assertThat(runningPlaceholder.isInvalid()).isTrue();
        assertThat(runningPlaceholder.getSourcePlugin()).isEqualTo(pluginCoordinates);
        assertThat(runningPlaceholder.getFlowStatus().getErrors()).hasSize(1);
        assertThat(runningPlaceholder.getFlowStatus().getErrors().get(0).getErrorType()).isEqualTo(FlowErrorType.INVALID_CONFIG);
        assertThat(runningPlaceholder.getFlowStatus().getErrors().get(0).getMessage()).contains("Waiting for plugin");

        // Verify stopped placeholder with testMode
        TransformFlow stoppedPlaceholder = savedFlows.get("pending-stopped");
        assertThat(stoppedPlaceholder).isNotNull();
        assertThat(stoppedPlaceholder.isRunning()).isFalse();
        assertThat(stoppedPlaceholder.isTestMode()).isTrue();
        assertThat(stoppedPlaceholder.isInvalid()).isTrue();
        assertThat(stoppedPlaceholder.getSourcePlugin()).isEqualTo(pluginCoordinates);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getInfo()).hasSize(2)
                .anyMatch(info -> info.contains("Created placeholder for flow pending-running"))
                .anyMatch(info -> info.contains("Created placeholder for flow pending-stopped"));
    }

    @Test
    void validateSystemPlanName() {
        PluginCoordinates system = PluginCoordinates.builder().artifactId(PluginService.SYSTEM_PLUGIN_ARTIFACT_ID).groupId(PluginService.SYSTEM_PLUGIN_GROUP_ID).version("1").build();
        TransformFlow existing = transformFlow("my-flow", FlowState.STOPPED, false);
        existing.setSourcePlugin(PluginCoordinates.builder().artifactId("a").groupId("b").version("1").build());

        // mock an existing flow with a different plugin coord
        Mockito.when(transformFlowRepo.findByNameAndType("my-flow", FlowType.TRANSFORM, TransformFlow.class))
                .thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> transformFlowService.validateSystemPlanName("my-flow", system))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A transform named my-flow exists in plugin b:a:1");
    }

    @Test
    void validateSystemPlanName_isUnique() {
        PluginCoordinates system = PluginCoordinates.builder().artifactId(PluginService.SYSTEM_PLUGIN_ARTIFACT_ID).groupId(PluginService.SYSTEM_PLUGIN_GROUP_ID).version("1").build();

        // mock an existing flow with a different plugin coord
        Mockito.when(transformFlowRepo.findByNameAndType("my-flow", FlowType.TRANSFORM, TransformFlow.class))
                .thenReturn(Optional.empty());
        assertThatNoException().isThrownBy(() -> transformFlowService.validateSystemPlanName("my-flow", system));
    }

    @Test
    void validateSystemPlanName_existingSystemPlan() {
        PluginCoordinates system = PluginCoordinates.builder().artifactId(PluginService.SYSTEM_PLUGIN_ARTIFACT_ID).groupId(PluginService.SYSTEM_PLUGIN_GROUP_ID).version("2").build();

        TransformFlow transformFlow = transformFlow("my-flow", FlowState.RUNNING, false);
        transformFlow.setSourcePlugin(PluginCoordinates.builder().artifactId(PluginService.SYSTEM_PLUGIN_ARTIFACT_ID).groupId(PluginService.SYSTEM_PLUGIN_GROUP_ID).version("2").build());

        // mock an existing system flow (version doesn't matter)
        Mockito.when(transformFlowRepo.findByNameAndType("my-flow", FlowType.TRANSFORM, TransformFlow.class))
                .thenReturn(Optional.of(transformFlow));
        assertThatNoException().isThrownBy(() -> transformFlowService.validateSystemPlanName("my-flow", system));
    }

    TransformFlow transformFlow(String name, FlowState flowState, boolean testMode) {
        return transformFlow(name, flowState, testMode, true);
    }

    @Test
    void upgradeFlows_placeholderNotDeletedBeforeUpsert() {
        PluginCoordinates systemPlugin = PluginCoordinates.builder()
                .groupId(PluginService.SYSTEM_PLUGIN_GROUP_ID)
                .artifactId(PluginService.SYSTEM_PLUGIN_ARTIFACT_ID)
                .version("1.0.0")
                .build();
        PluginCoordinates realPlugin = PluginCoordinates.builder()
                .groupId("com.example")
                .artifactId("my-plugin")
                .version("2.0.0")
                .build();

        // Create a system-plugin placeholder (e.g., from a snapshot restore)
        TransformFlow placeholder = transformFlow("my-transform", FlowState.RUNNING, false);
        placeholder.setSourcePlugin(systemPlugin);
        placeholder.getFlowStatus().setPlaceholder(true);
        placeholder.getFlowStatus().setValid(false);
        placeholder.getFlowStatus().setErrors(new ArrayList<>(List.of(
                FlowConfigError.newBuilder()
                        .configName("my-transform")
                        .errorType(FlowErrorType.INVALID_CONFIG)
                        .message("Waiting for plugin")
                        .build())));

        // No existing flows for the real plugin (first registration)
        Mockito.when(transformFlowRepo.findBySourcePluginGroupIdAndSourcePluginArtifactIdAndType(
                realPlugin.getGroupId(), realPlugin.getArtifactId(), FlowType.TRANSFORM))
                .thenReturn(Collections.emptyList());

        // System-plugin has the placeholder
        Mockito.when(transformFlowRepo.findBySourcePluginGroupIdAndSourcePluginArtifactIdAndType(
                systemPlugin.getGroupId(), systemPlugin.getArtifactId(), FlowType.TRANSFORM))
                .thenReturn(List.of(placeholder));

        Mockito.when(flowValidator.validate(any())).thenReturn(Collections.emptyList());
        Mockito.when(pluginVariableService.getVariablesByPlugin(realPlugin)).thenReturn(Collections.emptyList());

        // Create the incoming flow plan
        TransformFlowPlan flowPlan = new TransformFlowPlan("my-transform", FlowType.TRANSFORM, "My transform");
        flowPlan.setSourcePlugin(realPlugin);

        // Execute upgradeFlows
        transformFlowService.upgradeFlows(realPlugin, List.of(flowPlan));

        // The placeholder should NOT be deleted via JPA deleteAll (it would interfere with the upsert)
        verify(transformFlowRepo, never()).deleteAll(Mockito.argThat(flows -> {
            for (Object flow : flows) {
                if (flow instanceof TransformFlow tf && tf.getName().equals("my-transform")) {
                    return true;
                }
            }
            return false;
        }));

        // The upsert should be called for the flow (ON CONFLICT handles the placeholder)
        verify(transformFlowRepo).upsertFlowFromPlugin(singleFlowCaptor.capture());
        TransformFlow upsertedFlow = singleFlowCaptor.getValue();
        assertThat(upsertedFlow.getName()).isEqualTo("my-transform");
        assertThat(upsertedFlow.getSourcePlugin()).isEqualTo(realPlugin);
        // State should be preserved from the placeholder (RUNNING)
        assertThat(upsertedFlow.isRunning()).isTrue();
    }

    TransformFlow transformFlow(String name, FlowState flowState, boolean testMode, boolean valid) {
        TransformFlow transformFlow = new TransformFlow();
        transformFlow.setName(name);
        FlowStatus flowStatus = new FlowStatus();
        flowStatus.setState(flowState);
        flowStatus.setTestMode(valid);
        flowStatus.setTestMode(testMode);
        transformFlow.setFlowStatus(flowStatus);
        return transformFlow;
    }
}