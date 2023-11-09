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
    offset: 0,
    count: 1,
    totalCount: 1,
    deltaFiles: [
      {
        did: "2549aa54-c7e8-4353-a226-622e71956c5d",
        stage: "COMPLETE",
        modified: "2023-11-08T19:15:09.031Z",
        created: "2023-11-08T19:15:09.008Z",
        actions: [
          {
            name: "IngressAction",
            created: "2023-11-08T19:15:09.008Z",
            modified: "2023-11-08T19:15:09.015Z",
            filteredCause: null,
            errorCause: null,
            errorContext: null,
            state: "COMPLETE"
          },
          {
            name: "FilterTransform",
            created: "2023-11-08T19:15:09.015Z",
            modified: "2023-11-08T19:15:09.031Z",
            filteredCause: "Mock filtered cause",
            errorCause: null,
            errorContext: null,
            state: "FILTERED"
          }
        ],
        sourceInfo: {
          filename: "filtermeplease.txt",
          flow: "mock-flow"
        },
        errorAcknowledged: null,
        errorAcknowledgedReason: null,
        nextAutoResume: null,
        nextAutoResumeReason: null
      },
      {
        did: "6cba7eb0-9604-4f93-bc70-1afc201d10f6",
        stage: "COMPLETE",
        modified: "2023-11-08T19:15:10.031Z",
        created: "2023-11-08T19:15:10.008Z",
        actions: [
          {
            name: "IngressAction",
            created: "2023-11-08T19:15:10.008Z",
            modified: "2023-11-08T19:15:10.015Z",
            filteredCause: null,
            errorCause: null,
            errorContext: null,
            state: "COMPLETE"
          },
          {
            name: "FilterTransform",
            created: "2023-11-08T19:15:10.015Z",
            modified: "2023-11-08T19:15:10.031Z",
            filteredCause: "Mock filtered cause",
            errorCause: null,
            errorContext: null,
            state: "FILTERED"
          }
        ],
        sourceInfo: {
          filename: "pleasefilterme.txt",
          flow: "mock-flow"
        },
        errorAcknowledged: null,
        errorAcknowledgedReason: null,
        nextAutoResume: null,
        nextAutoResumeReason: null
      },
      {
        did: "2f5de6d5-c5dd-45b2-9d6f-3dd412524129",
        stage: "COMPLETE",
        modified: "2023-11-08T19:15:11.031Z",
        created: "2023-11-08T19:15:11.008Z",
        actions: [
          {
            name: "IngressAction",
            created: "2023-11-08T19:15:11.008Z",
            modified: "2023-11-08T19:15:11.015Z",
            filteredCause: null,
            errorCause: null,
            errorContext: null,
            state: "COMPLETE"
          },
          {
            name: "FilterTransform",
            created: "2023-11-08T19:15:11.015Z",
            modified: "2023-11-08T19:15:11.031Z",
            filteredCause: "Another mock filtered cause",
            errorCause: null,
            errorContext: null,
            state: "FILTERED"
          }
        ],
        sourceInfo: {
          filename: "filterit.txt",
          flow: "another-mock-flow"
        },
        errorAcknowledged: null,
        errorAcknowledgedReason: null,
        nextAutoResume: null,
        nextAutoResumeReason: null
      }
    ]
  }
};

export default () => {
  return {
    deltaFiles: generateData()
  }
}