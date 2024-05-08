/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>

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
    dataSource: [
      {
        name: "mock-passthrough-rest-data-source",
        type: "REST_DATA_SOURCE",
        description: "Mock create DeltaFiles that will pass through the system unchanged",
        flowStatus: {
          state: "RUNNING",
          errors: [],
          testMode: false,
        },
        __typename: "RestDataSource",
        topic: "mock-passthrough",
        sourcePlugin: {
          groupId: "mock.org.deltafi",
          artifactId: "mock-deltafi-core-actions",
          version: "mock-version-2.0",
        },
      },
      {
        name: "mock-unarchive-passthrough-rest-data-source",
        type: "REST_DATA_SOURCE",
        description: "Mock receive files to unarchive and publish extracted files to passthrough",
        flowStatus: {
          state: "RUNNING",
          errors: [],
          testMode: false,
        },
        __typename: "RestDataSource",
        topic: "mock-unarchive-passthrough",
        sourcePlugin: {
          groupId: "mock.org.deltafi",
          artifactId: "mock-deltafi-core-actions",
          version: "mock-version-2.0",
        },
      },
      {
        name: "mock-decompress-passthrough-rest-data-source",
        type: "REST_DATA_SOURCE",
        description: "Mock receive files to decompress and publish to passthrough",
        flowStatus: {
          state: "RUNNING",
          errors: [],
          testMode: false,
        },
        __typename: "RestDataSource",
        topic: "mock-decompress-passthrough",
        sourcePlugin: {
          groupId: "mock.org.deltafi",
          artifactId: "mock-deltafi-core-actions",
          version: "mock-version-2.0",
        },
      },
      {
        name: "mock-unarchive-and-decompress-passthrough-rest-data-source",
        type: "REST_DATA_SOURCE",
        description: "mock receive files to unarchive, decompress extracted files, and publish each to passthrough",
        flowStatus: {
          state: "RUNNING",
          errors: [],
          testMode: false,
        },
        __typename: "RestDataSource",
        topic: "mock-unarchive-and-decompress-passthrough",
        sourcePlugin: {
          groupId: "mock.org.deltafi",
          artifactId: "mock-deltafi-core-actions",
          version: "mock-version-2.0",
        },
      },
      {
        name: "mock-unarchive-and-merge-passthrough-rest-data-source",
        type: "REST_DATA_SOURCE",
        description: "Mock receive files to unarchive, merge into a single file, and publish to passthrough",
        flowStatus: {
          state: "RUNNING",
          errors: [],
          testMode: false,
        },
        __typename: "RestDataSource",
        topic: "mock-unarchive-and-merge-passthrough",
        sourcePlugin: {
          groupId: "mock.org.deltafi",
          artifactId: "mock-deltafi-core-actions",
          version: "mock-version-2.0",
        },
      },
      {
        name: "mock-gen-data",
        type: "TIMED_DATA_SOURCE",
        description: "Mock create DeltaFiles of random mediaTypes",
        flowStatus: {
          state: "RUNNING",
          errors: [],
          testMode: false,
        },
        __typename: "TimedDataSource",
        topic: "mock-generated-data",
        sourcePlugin: {
          groupId: "mock.org.deltafi.testjig",
          artifactId: "mock-deltafi-testjig",
          version: "mock-version-2.0",
        },
        cronSchedule: "*/5 * * * * *",
        lastRun: "2024-05-07T18:19:40.041Z",
        nextRun: "2024-05-07T18:19:45.000Z",
        memo: "104591",
        currentDid: null,
        executeImmediate: false,
        ingressStatus: "HEALTHY",
        ingressStatusMessage: "Mock successfully created gen-data-104591",
        timedIngressAction: {
          name: "MockGenerateData",
          apiVersion: null,
          actionType: "TIMED_INGRESS",
          type: "mock.org.deltafi.testjig.action.GenerateDataIngressAction",
          parameters: null,
        },
        variables: [],
      },
      {
        name: "mock-smoke-timed-data-source",
        type: "TIMED_DATA_SOURCE",
        description: "Mock create smoke DeltaFiles",
        flowStatus: {
          state: "RUNNING",
          errors: [],
          testMode: false,
        },
        __typename: "TimedDataSource",
        topic: "mock-smoke-transform",
        sourcePlugin: {
          groupId: "mock.org.deltafi",
          artifactId: "mock-deltafi-core-actions",
          version: "mock-version-2.0",
        },
        cronSchedule: "*/5 * * * * *",
        lastRun: "2024-05-07T18:19:30.012Z",
        nextRun: "2024-05-07T18:19:45.000Z",
        memo: "126977",
        currentDid: null,
        executeImmediate: false,
        ingressStatus: "HEALTHY",
        ingressStatusMessage: "Mock successfully created smoke-timed-data-source-126977 (sleepy)",
        timedIngressAction: {
          name: "MockSmokeTestIngressAction",
          apiVersion: null,
          actionType: "TIMED_INGRESS",
          type: "mock.org.deltafi.core.action.ingress.SmokeTestIngress",
          parameters: {
            metadata: {
              smoke: "mock-test",
            },
          },
        },
        variables: [],
      }
      
    ],
  };
};

export default () => {
  return {
    getAllFlows: generateData(),
  };
};