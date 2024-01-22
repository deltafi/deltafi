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

import com.jayway.jsonpath.TypeRef;
import com.netflix.graphql.dgs.DgsQueryExecutor;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.common.types.Variable;
import org.deltafi.common.types.VariableDataType;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.configuration.ui.Link;
import org.deltafi.core.generated.types.BackOff;
import org.deltafi.core.plugin.deployer.image.PluginImageRepository;
import org.deltafi.core.types.*;
import org.intellij.lang.annotations.Language;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SystemSnapshotDatafetcherTestHelper {

    public static SystemSnapshot importSystemSnapshot(DgsQueryExecutor dgsQueryExecutor) {

        TypeRef<SystemSnapshot> systemSnapshotTypeRef = new TypeRef<>() {
        };

        @Language("GraphQL") String mutation = IMPORT_SNAPSHOT.replace("$projection", PROJECTION);
        return dgsQueryExecutor.executeAndExtractJsonPathAsObject(
                mutation,
                "data.importSnapshot",
                systemSnapshotTypeRef);
    }

    public static Result restoreSnapshot(DgsQueryExecutor dgsQueryExecutor) {
        TypeRef<Result> systemSnapshotTypeRef = new TypeRef<>() {
        };

        @Language("GraphQL") String mutation = "mutation { resetFromSnapshotWithId(snapshotId: \"63fe71a7d021eb040c97bda2\", hardReset: false) { success info errors } }";
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
        setResumePolicies(systemSnapshot);
        setDeletePolicies(systemSnapshot);
        setDeltaFiProperties(systemSnapshot);
        setPluginVariables(systemSnapshot);
        setPluginImageRepositories(systemSnapshot);
        setInstalledPlugins(systemSnapshot);
        return systemSnapshot;
    }

    private static void setResumePolicies(SystemSnapshot systemSnapshot) {
        ResumePolicy first = new ResumePolicy();
        first.setId("88bc7429-7adf-4bb1-b23f-3922993e0a1a");
        first.setName("auto-resume-passthrough");
        first.setFlow("passthrough");
        first.setMaxAttempts(10);
        first.setBackOff(BackOff.newBuilder().delay(100).maxDelay(200).multiplier(1).build());

        ResumePolicy second = new ResumePolicy();
        second.setId("a2b08968-866a-4080-bc28-1d7e7c81ada8");
        second.setName("resume-json-errors");
        second.setErrorSubstring("JsonException");
        second.setActionType("ENRICH");
        second.setMaxAttempts(4);
        second.setBackOff(BackOff.newBuilder().delay(60).maxDelay(120).random(true).build());

        systemSnapshot.setResumePolicies(List.of(first, second));
    }

    private static void setDeletePolicies(SystemSnapshot systemSnapshot) {
        DeletePolicies deletePolicies = new DeletePolicies();

        TimedDeletePolicy afterComplete = TimedDeletePolicy.builder().id("afterComplete").name("afterComplete").enabled(true).afterComplete("P4D").deleteMetadata(true).build();
        TimedDeletePolicy deleteSmoke = TimedDeletePolicy.builder().id("66e4572f-3c5f-45dc-b35d-b3ffd7f70245").name("deleteSmoke1MafterComplete").enabled(false).flow("smoke").afterComplete("PT2M").deleteMetadata(false).build();

        deletePolicies.setTimedPolicies(List.of(afterComplete, deleteSmoke));

        DiskSpaceDeletePolicy diskSpaceDeletePolicy = DiskSpaceDeletePolicy.builder().id("percentDisk").name("percentDisk").enabled(true).maxPercent(75).build();
        deletePolicies.setDiskSpacePolicies(List.of(diskSpaceDeletePolicy));

        systemSnapshot.setDeletePolicies(deletePolicies);
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
        deltaFiProperties.setAutoResumeCheckFrequency(Duration.ofMinutes(10));
        deltaFiProperties.setInMemoryQueueSize(10);
        Set<String> props = Stream.of(PropertyType.AUTO_RESUME_CHECK_FREQUENCY, PropertyType.CHECKS_ACTION_QUEUE_SIZE_THRESHOLD, PropertyType.DELETE_FREQUENCY, PropertyType.DELETE_POLICY_BATCH_SIZE, PropertyType.INGRESS_DISK_SPACE_REQUIREMENT_IN_MB, PropertyType.INGRESS_ENABLED, PropertyType.UI_USE_UTC)
                .map(Enum::name).collect(Collectors.toSet());
        deltaFiProperties.setSetProperties(props);

        systemSnapshot.setDeltaFiProperties(deltaFiProperties);
    }

    private static void setPluginVariables(SystemSnapshot systemSnapshot) {
        PluginVariables pluginVariables = new PluginVariables();
        pluginVariables.setSourcePlugin(PluginCoordinates.builder().groupId("org.deltafi.passthrough").artifactId("deltafi-passthrough").version("0.102.1-SNAPSHOT").build());
        Variable annotations = Variable.builder()
                .name("annotations")
                .description("Searchable annotations in the DeltaFile")
                .dataType(VariableDataType.MAP)
                .required(false)
                .value("test_key: value, X: O").build();

        Variable sampleList = Variable.builder()
                .name("sampleList")
                .description("Noop sample list variable")
                .dataType(VariableDataType.LIST)
                .required(false)
                .value("test, test").build();
        pluginVariables.setVariables(List.of(annotations, sampleList));
        systemSnapshot.setPluginVariables(List.of(pluginVariables));
    }

    private static void setPluginImageRepositories(SystemSnapshot systemSnapshot) {
        PluginImageRepository passthrough = new PluginImageRepository();
        passthrough.setImageRepositoryBase("registry.gitlab.com/deltafi/deltafi-passthrough/");
        passthrough.setPluginGroupIds(List.of("org.deltafi.passthrough"));
        passthrough.setImagePullSecret("docker-secret");

        PluginImageRepository json = new PluginImageRepository();
        json.setImageRepositoryBase("registry.gitlab.com/deltafi/deltafi-json-validation/");
        json.setPluginGroupIds(List.of("org.deltafi.jsonvalidations"));
        json.setImagePullSecret("docker-secret");

        PluginImageRepository stix = new PluginImageRepository();
        stix.setImageRepositoryBase("registry.gitlab.com/deltafi/deltafi-stix/");
        stix.setPluginGroupIds(List.of("org.deltafi.stix"));
        stix.setImagePullSecret("docker-secret");

        PluginImageRepository pythonPoc = new PluginImageRepository();
        pythonPoc.setImageRepositoryBase("registry.gitlab.com/deltafi/deltafi-python-poc/");
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
                                name: "auto-resume-passthrough"
                                flow: "passthrough"
                                maxAttempts: 10
                                backOff:
                                    {
                                        delay: 100
                                        maxDelay: 200
                                        multiplier: 1
                                    }
                            }
                            {
                                id: "a2b08968-866a-4080-bc28-1d7e7c81ada8"
                                name: "resume-json-errors"
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
                        deltaFiProperties: {
                            systemName: "DeltaFi"
                            requeueSeconds: 300
                            autoResumeCheckFrequency: "PT10M"
                            coreServiceThreads: 16
                            coreInternalQueueSize: 64
                            scheduledServiceThreads: 8
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
                            inMemoryQueueSize: 10
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
                                "AUTO_RESUME_CHECK_FREQUENCY"
                                "CHECKS_ACTION_QUEUE_SIZE_THRESHOLD"
                                "DELETE_FREQUENCY"
                                "DELETE_POLICY_BATCH_SIZE"
                                "INGRESS_DISK_SPACE_REQUIREMENT_IN_MB"
                                "INGRESS_ENABLED"
                                "UI_USE_UTC"
                            ]
                        }
                        pluginImageRepositories: [
                            {
                                imageRepositoryBase: "registry.gitlab.com/deltafi/deltafi-passthrough/"
                                pluginGroupIds: ["org.deltafi.passthrough"]
                                imagePullSecret: "docker-secret"
                            }
                            {
                                imageRepositoryBase: "registry.gitlab.com/deltafi/deltafi-json-validation/"
                                pluginGroupIds: ["org.deltafi.jsonvalidations"]
                                imagePullSecret: "docker-secret"
                            }
                            {
                                imageRepositoryBase: "registry.gitlab.com/deltafi/deltafi-stix/"
                                pluginGroupIds: ["org.deltafi.stix"]
                                imagePullSecret: "docker-secret"
                            }
                            {
                                imageRepositoryBase: "registry.gitlab.com/deltafi/deltafi-python-poc/"
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
                                        name: "annotations"
                                        description: "Searchable annotations in the DeltaFile"
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
              autoResumeCheckFrequency
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
              inMemoryQueueSize
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
            resumePolicies {
              id
              name
              flow
              errorSubstring
              action
              actionType
              maxAttempts
              backOff {
                delay
                maxDelay
                multiplier
                random
              }
            }
            pluginCustomizationConfigs {
              groupId
              artifactId
              urlTemplate
              secretName
            }
            """;
}
