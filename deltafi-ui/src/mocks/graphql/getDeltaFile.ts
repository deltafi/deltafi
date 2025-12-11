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
  const data = {
    did: "2ace8f8a-60d6-4211-894a-0cd7455bf599",
    name: "hello-world-java-timed-data-source-28494",
    totalBytes: 0,
    ingressBytes: 24,
    dataSource: "hello-world-java-timed-data-source",
    parentDids: [],
    childDids: [],
    flows: [
      {
        name: "hello-world-java-timed-data-source",
        id: 0,
        number: 0,
        type: "TIMED_DATA_SOURCE",
        state: "COMPLETE",
        created: "2024-05-30T11:31:55.653Z",
        modified: "2024-05-30T11:31:55.659Z",
        input: {
          metadata: {},
          content: [],
          topics: [],
          ancestorIds: []
        },
        errorAcknowledged: null,
        errorAcknowledgedReason: null,
        actions: [
          {
            name: "HelloWorldTimedIngressAction",
            type: "INGRESS",
            state: "COMPLETE",
            created: "2024-05-30T11:31:55.653Z",
            queued: null,
            start: null,
            stop: null,
            modified: "2024-05-30T11:31:55.653Z",
            errorCause: null,
            errorContext: null,
            nextAutoResume: null,
            nextAutoResumeReason: null,
            filteredCause: null,
            filteredContext: null,
            attempt: 1,
            content: [
              {
                name: "hello-world-java-timed-data-source-28494",
                mediaType: "text/plain",
                size: 24,
                segments: [
                  {
                    uuid: "0bb6acee-0020-47c2-a8f2-36d28de07169",
                    offset: 0,
                    size: 24,
                    did: "b4be75f4-7902-4c3b-88d9-4ed3c1b85a09"
                  }
                ]
              },
              {
                name: "TestFile.xml",
                mediaType: "application/xml",
                size: 1024,
                segments: [
                  {
                    uuid: "0bb6acee-0020-47c2-a8f2-36d28de07169",
                    offset: 0,
                    size: 1024,
                    did: "06a6287c-dcec-477f-a2be-e6dd77caa143"
                  }
                ]
              }
            ],
            metadata: {
              index: "28494"
            },
            deleteMetadataKeys: []
          },
          {
            name: "Replay",
            type: "INGRESS",
            state: "COMPLETE",
            created: "2024-05-30T11:31:55.653Z",
            queued: "2024-05-30T11:31:55.653Z",
            start: null,
            stop: null,
            modified: "2024-05-30T11:31:55.653Z",
            errorCause: null,
            errorContext: null,
            nextAutoResume: null,
            nextAutoResumeReason: null,
            filteredCause: null,
            filteredContext: null,
            attempt: 1,
            content: [
              {
                name: "hello-world-java-timed-data-source-28494",
                mediaType: "text/plain",
                size: 24,
                segments: [
                  {
                    uuid: "0bb6acee-0020-47c2-a8f2-36d28de07169",
                    offset: 0,
                    size: 24,
                    did: "b4be75f4-7902-4c3b-88d9-4ed3c1b85a09"
                  }
                ]
              }
            ],
            metadata: {
              Mockdata: "mockMeta",
              Mock: "MetaMock2"
            },
            deleteMetadataKeys: []
          }
        ],
        publishTopics: [
          "hello-world-java-data"
        ],
        depth: 0,
        pendingAnnotations: [],
        testMode: false,
        testModeReason: null
      },
      {
        name: "hello-world-java-data-route-or-filter",
        number: 1,
        type: "TRANSFORM",
        state: "COMPLETE",
        created: "2024-05-30T11:31:55.659Z",
        modified: "2024-05-30T11:31:55.659Z",
        input: {
          metadata: {
            Mockdata: "mockMeta",
            index: "28494",
            Mock: "MetaMock2"
          },
          content: [
            {
              name: "hello-world-java-timed-data-source-28494",
              segments: [
                {
                  uuid: "0bb6acee-0020-47c2-a8f2-36d28de07169",
                  offset: 0,
                  size: 24,
                  did: "b4be75f4-7902-4c3b-88d9-4ed3c1b85a09"
                }
              ],
              mediaType: "text/plain",
              size: 24
            }
          ],
          topics: [
            "hello-world-java-data"
          ],
          ancestorIds: [0]
        },
        errorAcknowledged: null,
        errorAcknowledgedReason: null,
        actions: [
          {
            name: "NO_SUBSCRIBERS",
            type: "PUBLISH",
            state: "FILTERED",
            created: "2024-05-30T11:31:55.659Z",
            queued: "2024-05-30T11:31:55.659Z",
            start: null,
            stop: null,
            modified: "2024-05-30T11:31:55.659Z",
            errorCause: null,
            errorContext: null,
            nextAutoResume: null,
            nextAutoResumeReason: null,
            filteredCause: "No matching subscribers were found",
            filteredContext: "No subscribers found from flow 'hello-world-java-data-route-or-filter' because no topics matched the criteria.\nWith rules:\n---\nmatchingPolicy: \"FIRST_MATCHING\"\ndefaultRule:\n  defaultBehavior: \"FILTER\"\nrules:\n- topic: \"hello-world-java-transform\"\n  condition: \"metadata['hello'] == 'transform'\"\n- topic: \"hello-world-java-join\"\n  condition: \"metadata['hello'] == 'join'\"\n",
            attempt: 1,
            content: [],
            metadata: {},
            deleteMetadataKeys: []
          }
        ],
        publishTopics: [],
        depth: 1,
        pendingAnnotations: [],
        testMode: false,
        testModeReason: null
      }
    ],
    requeueCount: 0,
    referencedBytes: 24,
    stage: "COMPLETE",
    annotations: {
      MockAnnotate1: "MockVal1",
      MockCnnotate2: "MockVal2"
    },
    dataSinks: [],
    created: "2024-05-30T11:31:55.653Z",
    modified: "2024-05-30T11:33:13.041Z",
    contentDeleted: null,
    contentDeletedReason: null,
    egressed: false,
    filtered: true,
    replayed: "2024-05-30T11:32:27.827Z",
    replayDid: "993c1172-449d-4b7d-be1f-ec47f21b13fc"
  };

  return data;
};

export default {
  deltaFile: generateData(),
};
