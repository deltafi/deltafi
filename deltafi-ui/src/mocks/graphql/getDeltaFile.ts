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
  const data = {
    did: "27186720-723a-4f82-a5ab-2fff441b2c9b",
    parentDids: ["27186720-723a-4f82-a5ab-2fff441b2c9b"],
    childDids: ["27186720-723a-4f82-a5ab-2fff441b2c9b"],
    totalBytes: 65535,
    ingressBytes: 36,
    sourceInfo: {
      filename: "fakeData.txt",
      flow: "mock.ingressFlow",
      metadata: [
        {
          key: "123",
          value: "456",
        },
        {
          key: "foo",
          value: "bar",
        },
        {
          key: "abc",
          value: "xyz",
        },
      ],
    },
    stage: "ERROR",
    created: "2022-09-24T22:17:58.491Z",
    modified: "2022-09-24T22:17:58.563Z",
    actions: [
      {
        name: "IngressAction",
        type: "INGRESS",
        state: "COMPLETE",
        created: "2022-09-24T22:17:58.491Z",
        modified: "2022-09-24T22:17:58.499Z",
        queued: null,
        start: null,
        stop: null,
        errorCause: null,
        errorContext: null,
        content: [
          {
            name: "testfile.zip",
            mediaType: "application/octet-stream",
            segments: [
              {
                uuid: "82a12f6f-3b63-4c20-8f69-8fcd464d390d",
                offset: 0,
                size: 36,
                did: "b558ef37-4d20-4082-84d6-31e6d62e6f4a",
              },
            ],
            size: 36,
          },
        ],
        metadata: {
          "absolute.path": "/home/testFile/",
          path: "/",
          filename: "testfile",
          "file.group": "test",
          "file.lastModifiedTime": "2023-03-12T20:02:18+0000",
          "file.creationTime": "2023-03-12T20:02:18+0000",
          "file.lastAccessTime": "2023-06-09T20:49:31+0000",
          "file.owner": "test",
          "file.permissions": "rw-rw-r--",
          uuid: "82a12f6f-3b63-4c20-8f69-8fcd464d390d",
          flow: "ingress",
        },
        deleteMetadataKeys: ["fakeKey1", "fakeKey2"],
      },
      {
        name: "MockTransformAction",
        type: "TRANSFORM",
        state: "COMPLETE",
        created: "2022-09-24T22:17:58.499Z",
        modified: "2022-09-24T22:17:58.517Z",
        queued: "2022-09-24T22:17:58.499Z",
        start: "2022-09-24T22:17:58.514Z",
        stop: "2022-09-24T22:17:58.514Z",
        errorCause: null,
        errorContext: null,
        content: [
          {
            name: "TestFile.json",
            segments: [
              {
                uuid: "19a12f6f-3b63-4c20-8f69-8fcd464d390d",
                offset: 0,
                size: 36,
                did: "b558ef37-4d20-4082-84d6-31e6d62e6f4a",
              },
            ],
            size: 488,
            mediaType: "application/json",
          },
          {
            name: "TestFile.xml",
            segments: [
              {
                uuid: "82a12f6f-3b63-4c20-8f69-8fcd464d390d",
                offset: 0,
                size: 36,
                did: "b558ef37-4d20-4082-84d6-31e6d62e6f4a",
              },
            ],
            size: 718,
            mediaType: "application/xml",
          },
          {
            name: "TestFile.bin",
            segments: [
              {
                uuid: "82a12f6f-3b63-4c20-8f69-8fcd464d390d",
                offset: 0,
                size: 128,
                did: "b558ef37-4d20-4082-84d6-31e6d62e6f4a",
              },
            ],
            size: 128,
            mediaType: "application/octet-stream",
          },
          {
            name: "TestFile.txt",
            segments: [
              {
                uuid: "82a12f6f-3b63-4c20-8f69-8fcd464d390d",
                offset: 0,
                size: 32,
                did: "b558ef37-4d20-4082-84d6-31e6d62e6f4a",
              },
            ],
            size: 32,
            mediaType: "text/plain",
          },
        ],
        metadata: {
          "absolute.path": "/home/testFile/",
          path: "/",
          filename: "testfile",
          "file.group": "test",
          "file.lastModifiedTime": "2023-03-12T20:02:18+0000",
          "file.creationTime": "2023-03-12T20:02:18+0000",
          "file.lastAccessTime": "2023-06-09T20:49:31+0000",
          "file.owner": "test",
          "file.permissions": "rw-rw-r--",
          uuid: "82a12f6f-3b63-4c20-8f69-8fcd464d390d",
        },
        deleteMetadataKeys: ["flow"],
      },
      {
        name: "MockLoadAction",
        type: "LOAD",
        state: "COMPLETE",
        created: "2022-09-24T22:17:58.517Z",
        modified: "2022-09-24T22:17:58.526Z",
        queued: "2022-09-24T22:17:58.517Z",
        start: "2022-09-24T22:17:58.519Z",
        stop: "2022-09-24T22:17:58.519Z",
        errorCause: null,
        errorContext: null,
        content: [
          {
            name: "testfile1.txt",
            segments: [
              {
                uuid: "82a12f6f-3b63-4c20-8f69-8fcd464d390d",
                offset: 0,
                size: 36,
                did: "b558ef37-4d20-4082-84d6-31e6d62e6f4a",
              },
            ],
            size: 23423,
            mediaType: "application/octet-stream",
          },
          {
            name: "foobar.txt",
            segments: [
              {
                uuid: "82a12f6f-3b63-4c20-8f69-8fcd464d390d",
                offset: 0,
                size: 36,
                did: "b558ef37-4d20-4082-84d6-31e6d62e6f4a",
              },
            ],
            size: 24,
            mediaType: "application/octet-stream",
          },
        ],
        metadata: {
          "absolute.path": "/home/testFile/",
          path: "/",
          filename: "testfile",
          "file.group": "test",
          "file.lastModifiedTime": "2023-03-12T20:02:18+0000",
          "file.creationTime": "2023-03-12T20:02:18+0000",
          "file.lastAccessTime": "2023-06-09T20:49:31+0000",
          "file.owner": "test",
          "file.permissions": "rw-rw-r--",
          uuid: "82a12f6f-3b63-4c20-8f69-8fcd464d390d",
          newMetadata: "fake metadata",
        },
        deleteMetadataKeys: [],
      },
      {
        name: "MockEgressAction",
        type: "EGRESS",
        state: "FILTERED",
        created: "2022-09-24T22:17:58.541Z",
        modified: "2022-09-24T22:17:58.563Z",
        queued: "2022-09-24T22:17:58.541Z",
        start: "2022-09-24T22:17:58.543Z",
        stop: "2022-09-24T22:17:58.561Z",
        filteredCause: "Because I'm Batman! 🦇",
        errorCause: null,
        errorContext: null,
        content: [],
        metadata: {},
        deleteMetadataKeys: [],
      },
    ],
    annotations: {
      a: "b",
      c: "d",
    },
    contentDeleted: null,
    contentDeletedReason: null,
    errorAcknowledged: null,
    errorAcknowledgedReason: null,
    metadata: {
      "absolute.path": "/home/centos/stix-examples/2.1/cti-stix-elevator/",
      path: "/",
      filename: "archive-file.json",
      "file.group": "centos",
      "file.lastModifiedTime": "2023-03-12T20:02:18+0000",
      "file.creationTime": "2023-03-12T20:02:18+0000",
      "file.lastAccessTime": "2023-06-09T20:49:31+0000",
      "file.owner": "centos",
      "file.permissions": "rw-rw-r--",
      uuid: "b358260d-a1c2-4412-8259-c617cae96700",
      flow: "stix2_1",
    },
    pendingAnnotationsForFlows: ["mock_DevFlow1"],
  };

  return data;
};

export default {
  deltaFile: generateData(),
};
