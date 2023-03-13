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
package org.deltafi.core.snapshot;

import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import org.deltafi.common.types.KeyValue;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.common.types.Variable;
import org.deltafi.common.types.VariableDataType;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.configuration.ui.Link;
import org.deltafi.core.plugin.deployer.image.PluginImageRepository;
import org.deltafi.core.types.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

public class SystemSnapshotDatafetcherTestHelper {

    public static SystemSnapshot importSystemSnapshot(DgsQueryExecutor dgsQueryExecutor) {

        TypeRef<SystemSnapshot> systemSnapshotTypeRef = new TypeRef<>() {
        };

        String mutation = IMPORT_SNAPSHOT.replace("$projection", PROJECTION);
        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                mutation,
                "data.importSnapshot",
                systemSnapshotTypeRef);
    }

    public static Result restoreSnapshot(DgsQueryExecutor dgsQueryExecutor) {
        TypeRef<Result> systemSnapshotTypeRef = new TypeRef<>() {
        };

        String mutation = "mutation { resetFromSnapshotWithId(snapshotId: \"63fe71a7d021eb040c97bda2\", hardReset: false) { success info errors } }";
        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                mutation,
                "data.resetFromSnapshotWithId",
                systemSnapshotTypeRef);
    }

    public static SystemSnapshot expectedSnapshot() {
        SystemSnapshot systemSnapshot = new SystemSnapshot();
        systemSnapshot.setId("63fe71a7d021eb040c97bda2");
        systemSnapshot.setReason("TEST");
        systemSnapshot.setCreated(OffsetDateTime.parse("2023-02-28T21:27:03.407Z"));
        setDeletePolicies(systemSnapshot);
        setFlowAssignmentRules(systemSnapshot);
        setDeltaFiProperties(systemSnapshot);
        setPluginVariables(systemSnapshot);
        setPluginImageRepositories(systemSnapshot);
        setInstalledPlugins(systemSnapshot);
        setFlowInfo(systemSnapshot);
        return systemSnapshot;
    }

    private static void setDeletePolicies(SystemSnapshot systemSnapshot) {
        DeletePolicies deletePolicies = new DeletePolicies();

        TimedDeletePolicy afterComplete = TimedDeletePolicy.newBuilder().id("afterComplete").name("afterComplete").enabled(true).afterComplete("P4D").deleteMetadata(true).build();
        TimedDeletePolicy deleteSmoke = TimedDeletePolicy.newBuilder().id("66e4572f-3c5f-45dc-b35d-b3ffd7f70245").name("deleteSmoke1MafterComplete").enabled(false).flow("smoke").afterComplete("PT2M").deleteMetadata(false).build();

        deletePolicies.setTimedPolicies(List.of(afterComplete, deleteSmoke));

        DiskSpaceDeletePolicy diskSpaceDeletePolicy = DiskSpaceDeletePolicy.newBuilder().id("percentDisk").name("percentDisk").enabled(true).maxPercent(75).build();
        deletePolicies.setDiskSpacePolicies(List.of(diskSpaceDeletePolicy));

        systemSnapshot.setDeletePolicies(deletePolicies);
    }

    private static void setFlowAssignmentRules(SystemSnapshot systemSnapshot) {
        FlowAssignmentRule smokey = new FlowAssignmentRule();
        smokey.setId("0d6b8146-53e8-43cc-b56d-f8812cb64252");
        smokey.setName("smokey");
        smokey.setFlow("smoke");
        smokey.setPriority(500);
        smokey.setRequiredMetadata(List.of(new KeyValue("smoke", "true")));

        FlowAssignmentRule testytest = new FlowAssignmentRule();
        testytest.setId("49361ab6-96bb-46cf-9f21-61826de2e013");
        testytest.setName("testytest");
        testytest.setFlow("decompress-and-merge");
        testytest.setPriority(500);
        testytest.setRequiredMetadata(List.of(new KeyValue("test", "test"), new KeyValue("test2", "test2")));

        systemSnapshot.setFlowAssignmentRules(List.of(smokey, testytest));
    }

    private static void setDeltaFiProperties(SystemSnapshot systemSnapshot) {
        DeltaFiProperties deltaFiProperties = new DeltaFiProperties();

        deltaFiProperties.getUi().setUseUTC(false);
        Link httpBin = new Link();
        httpBin.setName("View in HTTPBin");
        httpBin.setUrl("https://httpbin.org/anything/example?did=${did}");
        deltaFiProperties.getUi().setDeltaFileLinks(List.of(httpBin));
        deltaFiProperties.getChecks().setActionQueueSizeThreshold(1000);
        deltaFiProperties.getIngress().setDiskSpaceRequirementInMb(5000);
        deltaFiProperties.getIngress().setEnabled(true);
        deltaFiProperties.getDelete().setFrequency(Duration.ofSeconds(30));
        deltaFiProperties.getDelete().setPolicyBatchSize(5000);
        deltaFiProperties.setSetProperties(Set.of(PropertyType.UI_USE_UTC, PropertyType.ACTION_QUEUE_THRESHOLD, PropertyType.INGRESS_DISK_SPACE_REQUIRED, PropertyType.INGRESS_ENABLED, PropertyType.DELETE_FREQUENCY, PropertyType.DELETE_BATCH_SIZE));

        systemSnapshot.setDeltaFiProperties(deltaFiProperties);
    }

    private static void setPluginVariables(SystemSnapshot systemSnapshot) {
        PluginVariables pluginVariables = new PluginVariables();
        pluginVariables.setSourcePlugin(PluginCoordinates.builder().groupId("org.deltafi.passthrough").artifactId("deltafi-passthrough").version("0.102.1-SNAPSHOT").build());
        Variable indexedMetadata = Variable.newBuilder()
                .name("indexedMetadata")
                .description("Metadata that will be indexed in the DeltaFile")
                .dataType(VariableDataType.MAP)
                .required(false)
                .value("test_key: value, X: O").build();

        Variable sampleList = Variable.newBuilder()
                .name("sampleList")
                .description("Noop sample list variable")
                .dataType(VariableDataType.LIST)
                .required(false)
                .value("test, test").build();
        pluginVariables.setVariables(List.of(indexedMetadata, sampleList));
        systemSnapshot.setPluginVariables(List.of(pluginVariables));
    }

    private static void setPluginImageRepositories(SystemSnapshot systemSnapshot) {
        PluginImageRepository passthrough = new PluginImageRepository();
        passthrough.setImageRepositoryBase("registry.gitlab.com/systolic/deltafi/deltafi-passthrough/");
        passthrough.setPluginGroupIds(List.of("org.deltafi.passthrough"));
        passthrough.setImagePullSecret("docker-secret");

        PluginImageRepository json = new PluginImageRepository();
        json.setImageRepositoryBase("registry.gitlab.com/systolic/deltafi/deltafi-json-validation/");
        json.setPluginGroupIds(List.of("org.deltafi.jsonvalidations"));
        json.setImagePullSecret("docker-secret");

        PluginImageRepository stix = new PluginImageRepository();
        stix.setImageRepositoryBase("registry.gitlab.com/systolic/deltafi/deltafi-stix/");
        stix.setPluginGroupIds(List.of("org.deltafi.stix"));
        stix.setImagePullSecret("docker-secret");

        PluginImageRepository pythonPoc = new PluginImageRepository();
        pythonPoc.setImageRepositoryBase("registry.gitlab.com/systolic/deltafi/deltafi-python-poc/");
        pythonPoc.setPluginGroupIds(List.of("org.deltafi.python-poc"));
        pythonPoc.setImagePullSecret("docker-secret");

        systemSnapshot.setPluginImageRepositories(List.of(passthrough, json, stix, pythonPoc));
    }

    private static void setInstalledPlugins(SystemSnapshot systemSnapshot) {
        systemSnapshot.setInstalledPlugins(Set.of(
            PluginCoordinates.builder().groupId("org.deltafi.python-poc").artifactId("deltafi-python-poc").version("main-9cc5379d").build(),
            PluginCoordinates.builder().groupId("org.deltafi.stix").artifactId("deltafi-stix").version("main-fd1945b9").build(),
            PluginCoordinates.builder().groupId("org.deltafi.passthrough").artifactId("deltafi-passthrough").version("0.102.1-SNAPSHOT").build(),
            PluginCoordinates.builder().groupId("org.deltafi").artifactId("deltafi-core-actions").version("0.102.1-SNAPSHOT").build()
        ));
    }

    private static void setFlowInfo(SystemSnapshot systemSnapshot) {
        systemSnapshot.setRunningIngressFlows(List.of("passthrough", "smoke", "decompress-and-merge", "decompress-passthrough", "stix_attack", "stix1_x", "stix2_1", "hello-python"));
        systemSnapshot.setRunningEnrichFlows(List.of("artificial-enrichment", "stix2_1", "hello-python"));
        systemSnapshot.setRunningEgressFlows(List.of("smoke", "passthrough", "stix2_1", "stix1_x", "hello-python"));
        systemSnapshot.setTestIngressFlows(List.of());
        systemSnapshot.setTestEgressFlows(List.of("passthrough"));
    }

    private static final String IMPORT_SNAPSHOT = """
            mutation {
                importSnapshot(
                    snapshot: {
                        id: "63fe71a7d021eb040c97bda2"
                        reason: "TEST"
                        created: "2023-02-28T21:27:03.407Z"
                        deletePolicies: {
                            timedPolicies: [
                                {
                                    id: "afterComplete"
                                    name: "afterComplete"
                                    enabled: true
                                    flow: null
                                    afterCreate: null
                                    afterComplete: "P4D"
                                    minBytes: null
                                    deleteMetadata: true
                                }
                                {
                                    id: "66e4572f-3c5f-45dc-b35d-b3ffd7f70245"
                                    name: "deleteSmoke1MafterComplete"
                                    enabled: false
                                    flow: "smoke"
                                    afterCreate: null
                                    afterComplete: "PT2M"
                                    minBytes: null
                                    deleteMetadata: false
                                }
                            ]
                            diskSpacePolicies: [
                                {
                                    id: "percentDisk"
                                    name: "percentDisk"
                                    enabled: true
                                    flow: null
                                    maxPercent: 75
                                }
                            ]
                        }
                        resumePolicies: [
                            {
                                id: "88bc7429-7adf-4bb1-b23f-3922993e0a1a"
                                flow: "passthrough"
                                maxAttempts: 10
                                backOff:
                                    {
                                        delay: 100
                                        maxDelay: 260
                                        multiplier: 1
                                    }
                            }
                            {
                                id: "a2b08968-866a-4080-bc28-1d7e7c81ada8"
                                errorSubstring: "JsonException"
                                actionType: "ENRICH"
                                maxAttempts: 4
                                backOff:
                                    {
                                        delay: 60
                                        maxDelay: 120
                                        random: true
                                    }
                            }
                        ]
                        flowAssignmentRules: [
                            {
                                id: "0d6b8146-53e8-43cc-b56d-f8812cb64252"
                                name: "smokey"
                                flow: "smoke"
                                priority: 500
                                filenameRegex: null
                                requiredMetadata: [{ key: "smoke", value: "true" }]
                            }
                            {
                                id: "49361ab6-96bb-46cf-9f21-61826de2e013"
                                name: "testytest"
                                flow: "decompress-and-merge"
                                priority: 500
                                filenameRegex: null
                                requiredMetadata: [
                                    { key: "test", value: "test" }
                                    { key: "test2", value: "test2" }
                                ]
                            }
                        ]
                        deltaFiProperties: {
                            systemName: "DeltaFi"
                            requeueSeconds: 300
                            autoResumeCheckFrequency: "PT10M"
                            coreServiceThreads: 16
                            scheduledServiceThreads: 32
                            ui: {
                                useUTC: false
                                deltaFileLinks: [
                                    {
                                        name: "View in HTTPBin"
                                        url: "https://httpbin.org/anything/example?did=${did}"
                                        description: null
                                    }
                                ]
                                externalLinks: []
                                topBar: { textColor: null, backgroundColor: null }
                                securityBanner: {
                                    enabled: false
                                    text: null
                                    textColor: null
                                    backgroundColor: null
                                }
                            }
                            delete: { ageOffDays: 13, frequency: "PT30S", policyBatchSize: 5000 }
                            ingress: { enabled: true, diskSpaceRequirementInMb: 5000 }
                            metrics: { enabled: true }
                            plugins: {
                                imageRepositoryBase: "docker.io/deltafi/"
                                imagePullSecret: null
                            }
                            checks: {
                                actionQueueSizeThreshold: 1000
                                contentStoragePercentThreshold: 90
                            }
                            setProperties: [
                                UI_USE_UTC
                                ACTION_QUEUE_THRESHOLD
                                INGRESS_DISK_SPACE_REQUIRED
                                INGRESS_ENABLED
                                DELETE_FREQUENCY
                                DELETE_BATCH_SIZE
                            ]
                        }
                        pluginImageRepositories: [
                            {
                                imageRepositoryBase: "registry.gitlab.com/systolic/deltafi/deltafi-passthrough/"
                                pluginGroupIds: ["org.deltafi.passthrough"]
                                imagePullSecret: "docker-secret"
                            }
                            {
                                imageRepositoryBase: "registry.gitlab.com/systolic/deltafi/deltafi-json-validation/"
                                pluginGroupIds: ["org.deltafi.jsonvalidations"]
                                imagePullSecret: "docker-secret"
                            }
                            {
                                imageRepositoryBase: "registry.gitlab.com/systolic/deltafi/deltafi-stix/"
                                pluginGroupIds: ["org.deltafi.stix"]
                                imagePullSecret: "docker-secret"
                            }
                            {
                                imageRepositoryBase: "registry.gitlab.com/systolic/deltafi/deltafi-python-poc/"
                                pluginGroupIds: ["org.deltafi.python-poc"]
                                imagePullSecret: "docker-secret"
                            }
                        ]
                        installedPlugins: [
                            {
                                groupId: "org.deltafi.python-poc"
                                artifactId: "deltafi-python-poc"
                                version: "main-9cc5379d"
                            }
                            {
                                groupId: "org.deltafi.stix"
                                artifactId: "deltafi-stix"
                                version: "main-fd1945b9"
                            }
                            {
                                groupId: "org.deltafi.passthrough"
                                artifactId: "deltafi-passthrough"
                                version: "0.102.1-SNAPSHOT"
                            }
                            {
                                groupId: "org.deltafi"
                                artifactId: "deltafi-core-actions"
                                version: "0.102.1-SNAPSHOT"
                            }
                        ]
                        pluginVariables: [
                            {
                                sourcePlugin: {
                                    groupId: "org.deltafi.passthrough"
                                    artifactId: "deltafi-passthrough"
                                    version: "0.102.1-SNAPSHOT"
                                }
                                variables: [
                                    {
                                        name: "indexedMetadata"
                                        description: "Metadata that will be indexed in the DeltaFile"
                                        dataType: MAP
                                        required: false
                                        defaultValue: null
                                        value: "test_key: value, X: O"
                                    }
                                    {
                                        name: "sampleList"
                                        description: "Noop sample list variable"
                                        dataType: LIST
                                        required: false
                                        defaultValue: null
                                        value: "test, test"
                                    }
                                ]
                            }
                        ]
                        runningIngressFlows: [
                            "passthrough"
                            "smoke"
                            "decompress-and-merge"
                            "decompress-passthrough"
                            "stix_attack"
                            "stix1_x"
                            "stix2_1"
                            "hello-python"
                        ]
                        runningEnrichFlows: ["artificial-enrichment", "stix2_1", "hello-python"]
                        runningEgressFlows: [
                            "smoke"
                            "passthrough"
                            "stix2_1"
                            "stix1_x"
                            "hello-python"
                        ]
                        testIngressFlows: []
                        testEgressFlows: ["passthrough"]
                        pluginCustomizationConfigs: []
                    }
                ) {
                    $projection
                }
            }
            """;

    private static final String PROJECTION = """
            id
            reason
            created
            deltaFiProperties {
              systemName
              requeueSeconds
              coreServiceThreads
              scheduledServiceThreads
              delete {
                ageOffDays
                frequency
                policyBatchSize
              }
              ingress {
                enabled
                diskSpaceRequirementInMb
              }
              metrics {
                enabled
              }
              plugins {
                imageRepositoryBase
                imagePullSecret
              }
              checks {
                actionQueueSizeThreshold
                contentStoragePercentThreshold
              }
              ui {
                useUTC
                deltaFileLinks {
                  name
                  url
                  description
                }
                externalLinks {
                  name
                  url
                  description
                }
                topBar {
                  textColor
                  backgroundColor
                }
                securityBanner {
                  enabled
                  text
                  textColor
                  backgroundColor
                }
              }
              setProperties
            }
            deletePolicies {
              timedPolicies {
                id
                name
                enabled
                flow
                afterCreate
                afterComplete
                minBytes
                deleteMetadata
              }
              diskSpacePolicies {
                id
                name
                enabled
                flow
                maxPercent
              }
            }
            pluginImageRepositories {
              imageRepositoryBase
              pluginGroupIds
              imagePullSecret
            }
            installedPlugins {
              groupId
              artifactId
              version
            }
            pluginVariables {
              sourcePlugin {
                groupId
                artifactId
                version
              }
              variables {
                name
                description
                dataType
                required
                defaultValue
                value
              }
            }
            flowAssignmentRules {
              id
              name
              flow
              priority
              filenameRegex
              requiredMetadata {
                key
                value
              }
            }
            testIngressFlows
            testEgressFlows
            runningIngressFlows
            runningEnrichFlows
            runningEgressFlows
            pluginCustomizationConfigs {
              groupId
              artifactId
              urlTemplate
              secretName
            }
            """;
}
