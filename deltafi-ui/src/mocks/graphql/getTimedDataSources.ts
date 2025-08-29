/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

const generateData = () => {
  return {
    timedDataSource: [
      {
        name: "gen-data",
        type: "TIMED_DATA_SOURCE",
        description: "Create DeltaFiles of random mediaTypes",
        maxErrors: -1,
        flowStatus: {
          state: "RUNNING",
          valid: true,
          errors: [],
          testMode: false
        },
        sourcePlugin: {
          groupId: "org.deltafi.testjig",
          artifactId: "deltafi-testjig",
          version: "2.30.2-SNAPSHOT"
        },
        annotationConfig: {
          annotations: {},
          metadataPatterns: [],
          discardPrefix: null
        },
        metadata: {},
        __typename: "TimedDataSource",
        topic: "generated-data",
        cronSchedule: "*/5 * * * * *",
        lastRun: "2025-08-29T17:46:30.337Z",
        nextRun: "2025-08-29T17:46:35.000Z",
        memo: "1288745",
        currentDid: null,
        executeImmediate: false,
        ingressStatus: "HEALTHY",
        ingressStatusMessage: "Successfully created gen-data-1288745",
        timedIngressAction: {
          name: "GenerateData",
          apiVersion: null,
          actionType: "TIMED_INGRESS",
          type: "org.deltafi.testjig.action.GenerateDataIngressAction",
          parameters: {
            maxRandomMeta: 0
          }
        },
        variables: []
      },
      {
        name: "hello-world-python-timed-data-source",
        type: "TIMED_DATA_SOURCE",
        description: "Hello World Python timed data source",
        maxErrors: -1,
        flowStatus: {
          state: "PAUSED",
          valid: true,
          errors: [],
          testMode: false
        },
        sourcePlugin: {
          groupId: "org.deltafi.python-hello-world",
          artifactId: "deltafi-python-hello-world",
          version: "2.30.2.dev3+g1e6812931"
        },
        annotationConfig: {
          annotations: {
            extraAnnot1: "valueA",
            extraAnnot2: "valueB"
          },
          metadataPatterns: [
            "metaKey.*"
          ],
          discardPrefix: "meta"
        },
        metadata: {
          metaKey3: "python",
          helloKey1: "timed1",
          helloKey2: "timed2"
        },
        __typename: "TimedDataSource",
        topic: "hello-world-python-data",
        cronSchedule: "*/5 * * * * *",
        lastRun: null,
        nextRun: "2025-03-20T17:40:10.000Z",
        memo: null,
        currentDid: null,
        executeImmediate: false,
        ingressStatus: "HEALTHY",
        ingressStatusMessage: null,
        timedIngressAction: {
          name: "HelloWorldTimedIngressAction",
          apiVersion: null,
          actionType: "TIMED_INGRESS",
          type: "org.deltafi.python-hello-world.HelloWorldTimedIngressAction",
          parameters: {}
        },
        variables: []
      },
      {
        name: "gen-data-clone",
        type: "TIMED_DATA_SOURCE",
        description: "Create DeltaFiles of random mediaTypes",
        maxErrors: -1,
        flowStatus: {
          state: "STOPPED",
          valid: true,
          errors: [],
          testMode: false
        },
        sourcePlugin: {
          groupId: "org.deltafi",
          artifactId: "system-plugin",
          version: "2.30.2-SNAPSHOT"
        },
        annotationConfig: null,
        metadata: null,
        __typename: "TimedDataSource",
        topic: "generated-data",
        cronSchedule: "*/5 * * * * *",
        lastRun: null,
        nextRun: "2025-03-20T17:43:35.000Z",
        memo: null,
        currentDid: null,
        executeImmediate: false,
        ingressStatus: "HEALTHY",
        ingressStatusMessage: null,
        timedIngressAction: {
          name: "GenerateData",
          apiVersion: null,
          actionType: "TIMED_INGRESS",
          type: "org.deltafi.testjig.action.GenerateDataIngressAction",
          parameters: null
        },
        variables: []
      },
      {
        name: "smoke-timed-data-source",
        type: "TIMED_DATA_SOURCE",
        description: "Create smoke DeltaFiles",
        maxErrors: -1,
        flowStatus: {
          state: "RUNNING",
          valid: true,
          errors: [],
          testMode: false
        },
        sourcePlugin: {
          groupId: "org.deltafi",
          artifactId: "deltafi-core-actions",
          version: "2.30.2-SNAPSHOT"
        },
        annotationConfig: {
          annotations: {},
          metadataPatterns: [],
          discardPrefix: null
        },
        metadata: {},
        __typename: "TimedDataSource",
        topic: "smoke-transform",
        cronSchedule: "*/5 * * * * *",
        lastRun: "2025-08-29T17:46:30.331Z",
        nextRun: "2025-08-29T17:46:35.000Z",
        memo: "1816181",
        currentDid: null,
        executeImmediate: false,
        ingressStatus: "HEALTHY",
        ingressStatusMessage: "Successfully created smoke-timed-data-source-1816181",
        timedIngressAction: {
          name: "SmokeTestIngressAction",
          apiVersion: null,
          actionType: "TIMED_INGRESS",
          type: "org.deltafi.core.action.ingress.SmokeTestIngress",
          parameters: {
            delayMS: 0,
            metadata: {
              smoke: "test"
            },
            mediaType: "application/text",
            contentSize: 500,
            delayChance: 0,
            triggerImmediateChance: 0
          }
        },
        variables: []
      },
      {
        name: "hello-world-java-timed-data-source",
        type: "TIMED_DATA_SOURCE",
        description: "Hello World Java timed data source",
        maxErrors: -1,
        flowStatus: {
          state: "STOPPED",
          valid: true,
          errors: [],
          testMode: false
        },
        sourcePlugin: {
          groupId: "org.deltafi.helloworld",
          artifactId: "deltafi-java-hello-world",
          version: "2.30.2-SNAPSHOT"
        },
        annotationConfig: {
          annotations: {
            extraAnnot1: "valueA",
            extraAnnot2: "valueB"
          },
          metadataPatterns: [
            "metaKey.*"
          ],
          discardPrefix: "meta"
        },
        metadata: {
          metaKey3: "java",
          helloKey1: "timed1",
          helloKey2: "timed2"
        },
        __typename: "TimedDataSource",
        topic: "hello-world-java-data",
        cronSchedule: "*/5 * * * * *",
        lastRun: null,
        nextRun: "2025-03-20T17:40:50.000Z",
        memo: null,
        currentDid: null,
        executeImmediate: false,
        ingressStatus: "HEALTHY",
        ingressStatusMessage: null,
        timedIngressAction: {
          name: "HelloWorldTimedIngressAction",
          apiVersion: null,
          actionType: "TIMED_INGRESS",
          type: "org.deltafi.helloworld.actions.HelloWorldTimedIngressAction",
          parameters: {}
        },
        variables: []
      },
      {
        name: "Not-cloned",
        type: "TIMED_DATA_SOURCE",
        description: "Wheeeeee",
        maxErrors: -1,
        flowStatus: {
          state: "STOPPED",
          valid: true,
          errors: [],
          testMode: false
        },
        sourcePlugin: {
          groupId: "org.deltafi",
          artifactId: "system-plugin",
          version: "2.30.2-SNAPSHOT"
        },
        annotationConfig: null,
        metadata: null,
        __typename: "TimedDataSource",
        topic: "generated-data",
        cronSchedule: "* 0 * * * *",
        lastRun: null,
        nextRun: "2025-03-20T18:00:00.000Z",
        memo: null,
        currentDid: null,
        executeImmediate: false,
        ingressStatus: "HEALTHY",
        ingressStatusMessage: null,
        timedIngressAction: {
          name: "GenerateDataIngressAction",
          apiVersion: null,
          actionType: "TIMED_INGRESS",
          type: "org.deltafi.testjig.action.GenerateDataIngressAction",
          parameters: null
        },
        variables: []
      }
    ]
  };
};

export default () => {
  return {
    getAllFlows: generateData(),
  };
};