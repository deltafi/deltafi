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
    created: "2021-2023-09-24T22:17:58.491Z",
    modified: "2021-2023-09-24T22:17:58.563Z",
    actions: [
      {
        name: "IngressAction",
        state: "COMPLETE",
        created: "2021-2023-09-24T22:17:58.491Z",
        modified: "2021-2023-09-24T22:17:58.499Z",
        queued: null,
        start: null,
        stop: null,
        errorCause: null,
        errorContext: null,
      },
      {
        name: "MockTransformAction",
        state: "COMPLETE",
        created: "2021-2023-09-24T22:17:58.499Z",
        modified: "2021-2023-09-24T22:17:58.517Z",
        queued: "2021-2023-09-24T22:17:58.499Z",
        start: "2021-2023-09-24T22:17:58.514Z",
        stop: "2021-2023-09-24T22:17:58.514Z",
        errorCause: null,
        errorContext: null,
      },
      {
        name: "MockLoadAction",
        state: "COMPLETE",
        created: "2021-2023-09-24T22:17:58.517Z",
        modified: "2021-2023-09-24T22:17:58.526Z",
        queued: "2021-2023-09-24T22:17:58.517Z",
        start: "2021-2023-09-24T22:17:58.519Z",
        stop: "2021-2023-09-24T22:17:58.519Z",
        errorCause: null,
        errorContext: null,
      },
      {
        name: "MockEnrichAction",
        state: "COMPLETE",
        created: "2021-2023-09-24T22:17:58.526Z",
        modified: "2021-2023-09-24T22:17:58.532Z",
        queued: "2021-2023-09-24T22:17:58.526Z",
        start: "2021-2023-09-24T22:17:58.530Z",
        stop: "2021-2023-09-24T22:17:58.530Z",
        errorCause: null,
        errorContext: null,
      },
      {
        name: "MockFormatAction",
        state: "COMPLETE",
        created: "2021-2023-09-24T22:17:58.532Z",
        modified: "2021-2023-09-24T22:17:58.535Z",
        queued: "2021-2023-09-24T22:17:58.532Z",
        start: "2021-2023-09-24T22:17:58.534Z",
        stop: "2021-2023-09-24T22:17:58.534Z",
        errorCause: null,
        errorContext: null,
      },
      {
        name: "MockValidateAction",
        state: "ERROR",
        created: "2021-2023-09-24T22:17:58.535Z",
        modified: "2021-2023-09-24T22:17:58.541Z",
        queued: "2021-2023-09-24T22:17:58.535Z",
        start: "2021-2023-09-24T22:17:58.539Z",
        stop: "2021-2023-09-24T22:17:58.539Z",
        errorCause: null,
        errorContext: null,
      },
      {
        name: "MockEgressAction",
        state: "FILTERED",
        created: "2021-2023-09-24T22:17:58.541Z",
        modified: "2021-2023-09-24T22:17:58.563Z",
        queued: "2021-2023-09-24T22:17:58.541Z",
        start: "2021-2023-09-24T22:17:58.543Z",
        stop: "2021-2023-09-24T22:17:58.561Z",
        filteredCause: "Because I'm Batman! 🦇",
        errorCause: null,
        errorContext: null,
      },
    ],
    domains: [
      {
        name: "xml",
        value: '<?xml version="1.0" encoding="UTF-8"?><note><to>Alice</to><from>Bob</from><heading>Congratulations!</heading><body>Congratulations, Alice! 🎉</body></note>',
        mediaType: "application/xml",
      },
    ],
    indexedMetadata: {
      a: "b",
      c: "d",
    },
    enrichment: [
      {
        name: "mockEnrichment",
        value: '{"stage":"ERROR","actions":[{"name":"IngressAction","state":"ERROR","errorCause":"Failed Ingress","errorContext":"Details..."}],"sourceInfo":{"flow":"smoke"}}',
        mediaType: "application/json",
      },
      {
        name: "MockEnrichmentTwo",
        value: "smoke more enrichment value",
        mediaType: "text/plain",
      },
    ],
    formattedData: [
      {
        filename: "textdata1.txt",
        metadata: [
          {
            key: "sourceInfo.123",
            value: "456",
          },
          {
            key: "sourceInfo.foo",
            value: "bar",
          },
          {
            key: "sourceInfo.abc",
            value: "xyz",
          },
        ],
        formatAction: "MockFormatAction",
        egressActions: ["MockEgressAction"],
        contentReference: {
          "mediaType": "application/octet-stream",
          "segments": [
            {
              "uuid": "82a12f6f-3b63-4c20-8f69-8fcd464d390d",
              "offset": 0,
              "size": 36,
              "did": "b558ef37-4d20-4082-84d6-31e6d62e6f4a"
            }
          ],
          "size": 36
        },
      },
    ],
    protocolStack: [
      {
        action: "IngressAction",
        metadata: null,
        content: [
          {
            name: "testfile.zip",
            contentReference: {
              "mediaType": "application/octet-stream",
              "segments": [
                {
                  "uuid": "82a12f6f-3b63-4c20-8f69-8fcd464d390d",
                  "offset": 0,
                  "size": 36,
                  "did": "b558ef37-4d20-4082-84d6-31e6d62e6f4a"
                }
              ],
              "size": 36
            },
          },
        ],
      },
      {
        action: "MockTransformAction",
        metadata: [],
        content: [
          {
            name: "TestFile.json",
            contentReference: {
              "segments": [
                {
                  "uuid": "19a12f6f-3b63-4c20-8f69-8fcd464d390d",
                  "offset": 0,
                  "size": 36,
                  "did": "b558ef37-4d20-4082-84d6-31e6d62e6f4a"
                }
              ],
              size: 488,
              mediaType: "application/json",
            },
          },
          {
            name: "TestFile.xml",
            contentReference: {
              "segments": [
                {
                  "uuid": "82a12f6f-3b63-4c20-8f69-8fcd464d390d",
                  "offset": 0,
                  "size": 36,
                  "did": "b558ef37-4d20-4082-84d6-31e6d62e6f4a"
                }
              ],
              size: 718,
              mediaType: "application/xml",
            },
          },
          {
            name: "TestFile.bin",
            contentReference: {
              "segments": [
                {
                  "uuid": "82a12f6f-3b63-4c20-8f69-8fcd464d390d",
                  "offset": 0,
                  "size": 128,
                  "did": "b558ef37-4d20-4082-84d6-31e6d62e6f4a"
                }
              ],
              size: 128,
              mediaType: "application/octet-stream",
            },
          },
          {
            name: "TestFile.txt",
            contentReference: {
              "segments": [
                {
                  "uuid": "82a12f6f-3b63-4c20-8f69-8fcd464d390d",
                  "offset": 0,
                  "size": 32,
                  "did": "b558ef37-4d20-4082-84d6-31e6d62e6f4a"
                }
              ],
              size: 32,
              mediaType: "text/plain",
            },
          }
        ],
      },
      {
        action: "MockLoadAction",
        metadata: [],
        content: [
          {
            name: "testfile1.txt",
            contentReference: {
              "segments": [
                {
                  "uuid": "82a12f6f-3b63-4c20-8f69-8fcd464d390d",
                  "offset": 0,
                  "size": 36,
                  "did": "b558ef37-4d20-4082-84d6-31e6d62e6f4a"
                }
              ],
              size: 23423,
              mediaType: "application/octet-stream",
            },
          },
          {
            name: "foobar.txt",
            metadata: [
              {
                key: "sourceInfo.123",
                value: "456",
              },
            ],
            contentReference: {
              "segments": [
                {
                  "uuid": "82a12f6f-3b63-4c20-8f69-8fcd464d390d",
                  "offset": 0,
                  "size": 36,
                  "did": "b558ef37-4d20-4082-84d6-31e6d62e6f4a"
                }
              ],
              size: 24,
              mediaType: "application/octet-stream",
            },
          },
        ],
      },
    ],
    contentDeleted: null,
    contentDeletedReason: null,
    errorAcknowledged: null,
    errorAcknowledgedReason: null,
  };

  return data;
};

export default {
  deltaFile: generateData(),
};
