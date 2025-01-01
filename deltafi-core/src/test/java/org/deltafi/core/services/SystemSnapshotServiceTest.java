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

import lombok.SneakyThrows;
import org.deltafi.common.types.*;
import org.deltafi.core.configuration.ui.Link;
import org.deltafi.core.configuration.ui.Link.LinkType;
import org.deltafi.core.generated.types.BackOff;
import org.deltafi.core.generated.types.SystemFlowPlans;
import org.deltafi.core.repo.SystemSnapshotRepo;
import org.deltafi.core.types.*;
import org.deltafi.core.types.snapshot.*;
import org.deltafi.core.util.Util;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        originalSnapshot.setSnapshot(Map.of("pluginVariables", List.of(pluginVariables, pluginVariables2)));

        UUID uuid = UUID.randomUUID();
        Mockito.when(systemSnapshotRepo.findById(uuid)).thenReturn(Optional.of(originalSnapshot));
        SystemSnapshot systemSnapshot = systemSnapshotService.getWithMaskedVariables(uuid);

        List<PluginVariables> maskedPluginVars = systemSnapshotService.mapSnapshotData(systemSnapshot).getPluginVariables();
        assertThat(maskedPluginVars).hasSize(2);
        assertThat(maskedPluginVars.getFirst().getVariables()).hasSize(2);
        assertThat(maskedPluginVars.getFirst().getVariables()).contains(one);
        assertThat(maskedPluginVars.getFirst().getVariables()).doesNotContain(two);
        assertThat(maskedPluginVars.getFirst().getVariables()).contains(afterMask);

        assertThat(maskedPluginVars.get(1).getVariables()).hasSize(2);
        assertThat(maskedPluginVars.get(1).getVariables()).contains(three);
        assertThat(maskedPluginVars.get(1).getVariables()).doesNotContain(four);
        assertThat(maskedPluginVars.get(1).getVariables()).contains(afterMask);
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
        systemSnapshot.setSnapshot(Map.of("pluginVariables", List.of(pluginVariables, pluginVariables2, pluginVariables3)));

        Mockito.when(systemSnapshotRepo.save(systemSnapshot)).thenAnswer(a -> a.getArgument(0));
        SystemSnapshot imported = systemSnapshotService.importSnapshot(systemSnapshot);

        List<PluginVariables> importedVariables = systemSnapshotService.mapSnapshotData(imported).getPluginVariables();

        // pluginVariables3 was pruned because it was empty after the masked variable is removed
        assertThat(importedVariables).hasSize(2);

        assertThat(importedVariables.getFirst().getVariables()).hasSize(1).contains(one);
        assertThat(importedVariables.get(1).getVariables()).hasSize(1).contains(three);
    }

    @Test
    void testMapSnapshotDataV1() {
        Snapshot expectedSnapshot = new Snapshot();
        setPluginVariables(expectedSnapshot);
        setDeletePolicies(expectedSnapshot);
        setDeltaFiProperties(expectedSnapshot);
        setLinks(expectedSnapshot);
        setRestDataSources(expectedSnapshot);
        setTimedDataSources(expectedSnapshot);
        setTransformFlows(expectedSnapshot);
        setDataSinks(expectedSnapshot);
        setInstalledPlugins(expectedSnapshot);
        setResumePolicies(expectedSnapshot);
        setSystemFlowPlans(expectedSnapshot);
        setUsersAndRoles(expectedSnapshot);

        Snapshot actualSnapshot = systemSnapshotService.mapSnapshotData(readSnapshotsFile("snapshot_v1.json"));
        assertThat(actualSnapshot).isEqualTo(expectedSnapshot);
    }

    @Test
    void testMapSnapshotDataV2() {
        SystemSnapshot input = readSnapshotsFile("snapshot_v2.json");
        assertThatThrownBy(() -> systemSnapshotService.mapSnapshotData(input))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid system snapshot schema version '2' in snapshot with id '00000000-0000-0000-0000-000000000000' with reason: 'snapshot v2'");
    }

    private void setUsersAndRoles(Snapshot snapshot) {
        OffsetDateTime now = OffsetDateTime.of(LocalDate.of(2000, 1, 1), LocalTime.of(0, 0, 0), ZoneOffset.UTC);
        Role role = new Role();
        role.setId(new UUID(0, 0));
        role.setName("role");
        role.setPermissions(List.of("permission"));
        role.setCreatedAt(now);
        role.setUpdatedAt(now);

        RoleSnapshot roleSnapshot = new RoleSnapshot(role);
        snapshot.setRoles(List.of(roleSnapshot));

        UserSnapshot user = new UserSnapshot();
        user.setId(new UUID(0,0));
        user.setName("user");
        user.setDn("CN=user");
        user.setUsername("user");
        user.setPassword("password");
        user.setRoles(Set.of(roleSnapshot));
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        snapshot.setUsers(List.of(user));
    }

    private static void setTimedDataSources(Snapshot snapshot) {
        TimedDataSourceSnapshot timedDataSource = new TimedDataSourceSnapshot();
        timedDataSource.setName("timedDataSource");
        timedDataSource.setCronSchedule("5m");
        timedDataSource.setTestMode(true);
        timedDataSource.setTopic("topic");
        timedDataSource.setRunning(true);
        timedDataSource.setMaxErrors(3);
        snapshot.setTimedDataSources(List.of(timedDataSource));
    }

    private static void setRestDataSources(Snapshot snapshot) {
        RestDataSourceSnapshot restDataSource = new RestDataSourceSnapshot();
        restDataSource.setName("restDataSource");
        restDataSource.setTestMode(true);
        restDataSource.setTopic("topic");
        restDataSource.setRunning(true);
        restDataSource.setMaxErrors(3);
        snapshot.setRestDataSources(List.of(restDataSource));
    }

    private static void setTransformFlows(Snapshot snapshot) {
        TransformFlowSnapshot transformFlowSnapshot = new TransformFlowSnapshot();
        transformFlowSnapshot.setName("restDataSource");
        transformFlowSnapshot.setTestMode(true);
        transformFlowSnapshot.setRunning(true);
        snapshot.setTransformFlows(List.of(transformFlowSnapshot));
    }

    private static void setDataSinks(Snapshot snapshot) {
        DataSinkSnapshot dataSink = new DataSinkSnapshot();
        dataSink.setName("dataSink");
        dataSink.setTestMode(true);
        dataSink.setTestMode(true);
        dataSink.setExpectedAnnotations(Set.of("annotation"));
        snapshot.setDataSinks(List.of(dataSink));
    }

    private static void setSystemFlowPlans(Snapshot snapshot) {
        SystemFlowPlans systemFlowPlans = new SystemFlowPlans();
        TimedDataSourcePlan timedDataSourcePlan = new TimedDataSourcePlan("timedIngressPlan", FlowType.TIMED_DATA_SOURCE, "description", "topic",
                new ActionConfiguration("timedIngress", ActionType.TIMED_INGRESS, "type"),  "*/5 * * * * *");
        systemFlowPlans.setTimedDataSources(List.of(timedDataSourcePlan));

        systemFlowPlans.setRestDataSources(List.of(new RestDataSourcePlan("restDataSource", FlowType.REST_DATA_SOURCE, "description", "topic")));

        DataSinkPlan dataSinkPlan = new DataSinkPlan("dataSinkPlan", FlowType.DATA_SINK, "description", new ActionConfiguration("egress", ActionType.EGRESS, "type"));
        dataSinkPlan.setSubscribe(Set.of(new Rule("subscribe", "a = b")));
        systemFlowPlans.setDataSinkPlans(List.of(dataSinkPlan));

        TransformFlowPlan transformFlowPlan = new TransformFlowPlan("transformPlan", FlowType.TRANSFORM, "description");
        ActionConfiguration action = new ActionConfiguration("transform", ActionType.TRANSFORM, "type");
        action.setJoin(new JoinConfiguration(Duration.ofMinutes(5), 5, 10, "meta-key"));

        transformFlowPlan.setTransformActions(List.of(action));
        transformFlowPlan.setSubscribe(Set.of(new Rule("subscribe", "a == a")));
        PublishRules publishRules = new PublishRules();
        publishRules.setMatchingPolicy(MatchingPolicy.FIRST_MATCHING);
        publishRules.setDefaultRule(new DefaultRule(DefaultBehavior.PUBLISH, "default"));
        publishRules.setRules(List.of(new Rule("publish", "a == a")));
        transformFlowPlan.setPublish(publishRules);
        systemFlowPlans.setTransformPlans(List.of(transformFlowPlan));

        snapshot.setSystemFlowPlans(systemFlowPlans);
    }

    private static void setResumePolicies(Snapshot snapshot) {
        ResumePolicy resumePolicy = new ResumePolicy();
        resumePolicy.setId(UUID.fromString("88bc7429-7adf-4bb1-b23f-3922993e0a1a"));
        resumePolicy.setName("auto-resume-passthrough");
        resumePolicy.setErrorSubstring("Exception");
        resumePolicy.setDataSource("passthrough");
        resumePolicy.setAction("action");
        resumePolicy.setMaxAttempts(10);
        resumePolicy.setMaxAttempts(1);
        resumePolicy.setPriority(3);
        resumePolicy.setBackOff(BackOff.newBuilder().delay(60).maxDelay(120).multiplier(1).random(true).build());

        snapshot.setResumePolicies(List.of(resumePolicy));
    }

    private static void setDeletePolicies(Snapshot snapshot) {
        DeletePolicies deletePolicies = new DeletePolicies();
        TimedDeletePolicy deleteSmoke = TimedDeletePolicy.builder().id(UUID.fromString("66e4572f-3c5f-45dc-b35d-b3ffd7f70245")).name("deleteSmoke1MafterComplete").enabled(false).flow("smoke").afterComplete("PT2M").minBytes(10L).deleteMetadata(false).build();
        deletePolicies.setTimedPolicies(List.of(deleteSmoke));

        DiskSpaceDeletePolicy diskSpaceDeletePolicy = DiskSpaceDeletePolicy.builder().id(UUID.fromString("66e4572f-3c5f-45dc-b35d-b3ffd7f70246")).name("percentDisk").enabled(true).maxPercent(75).build();
        deletePolicies.setDiskSpacePolicies(List.of(diskSpaceDeletePolicy));

        snapshot.setDeletePolicies(deletePolicies);
    }

    private static void setDeltaFiProperties(Snapshot snapshot) {
        List<KeyValue> deltaFiProperties = List.of(
                new KeyValue("uiUseUTC", "false"),
                new KeyValue("checkActionQueueSizeThreshold", "1000"));

        snapshot.setDeltaFiProperties(deltaFiProperties);
    }

    private static void setLinks(Snapshot snapshot) {
        Link httpBin = new Link();
        httpBin.setId(new UUID(0, 0));
        httpBin.setName("View in HTTPBin");
        httpBin.setUrl("https://httpbin.org/anything/example?did=${did}");
        httpBin.setLinkType(LinkType.EXTERNAL);
        httpBin.setDescription("View in HTTPBin");
        snapshot.setLinks(List.of(httpBin));
    }

    private static void setPluginVariables(Snapshot snapshot) {
        PluginVariables pluginVariables = new PluginVariables();
        pluginVariables.setSourcePlugin(PluginCoordinates.builder().groupId("org.deltafi.passthrough").artifactId("deltafi-passthrough").version("0.102.1-SNAPSHOT").build());
        Variable annotations = Variable.builder()
                .name("annotations")
                .description("Searchable annotations in the DeltaFile")
                .dataType(VariableDataType.MAP)
                .defaultValue("a: b")
                .required(false)
                .value("test_key: value, X: O").build();
        pluginVariables.setVariables(List.of(annotations));
        snapshot.setPluginVariables(List.of(pluginVariables));
    }

    private static void setInstalledPlugins(Snapshot snapshot) {
        snapshot.setInstalledPlugins(List.of(
                PluginCoordinates.builder().groupId("org.deltafi.python-poc").artifactId("deltafi-python-poc").version("main-9cc5379d").build()
        ));
    }

    @SneakyThrows
    private SystemSnapshot readSnapshotsFile(String file) {
        return SystemSnapshotService.OBJECT_MAPPER.readValue( new ClassPathResource("snapshots/" + file).getInputStream(), SystemSnapshot.class);
    }
}