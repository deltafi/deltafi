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
          did: "2a086f4e-ce1d-4eb6-bc8f-8709c4d74374",
          name: "hello-world-java-timed-data-source-12877",
          dataSource: "hello-world-java-timed-data-source",
          stage: "COMPLETE",
          modified: "2024-05-29T11:02:40.221Z",
          created: "2024-05-29T11:02:40.202Z",
          flows: [
            {
              name: "hello-world-java-timed-data-source",
              state: "COMPLETE",
              created: "2024-05-29T11:02:40.202Z",
              modified: "2024-05-29T11:02:40.216Z",
              actions: [
                {
                  name: "HelloWorldTimedIngressAction",
                  created: "2024-05-29T11:02:40.202Z",
                  modified: "2024-05-29T11:02:40.216Z",
                  filteredCause: null,
                  state: "COMPLETE"
                }
              ]
            },
            {
              name: "hello-world-java-data-route-or-filter",
              state: "COMPLETE",
              created: "2024-05-29T11:02:40.217Z",
              modified: "2024-05-29T11:02:40.217Z",
              actions: []
            },
            {
              name: "hello-world-java-transform",
              state: "COMPLETE",
              created: "2024-05-29T11:02:40.217Z",
              modified: "2024-05-29T11:02:40.221Z",
              actions: [
                {
                  name: "HelloWorldTransformAction",
                  created: "2024-05-29T11:02:40.217Z",
                  modified: "2024-05-29T11:02:40.221Z",
                  filteredCause: "We prefer DIDs that do not start with 2",
                  state: "FILTERED"
                }
              ]
            }
          ]
        },
        {
          did: "3789abd2-c13e-47e9-816d-6c4efc9546ed",
          name: "hello-world-java-timed-data-source-12876",
          dataSource: "hello-world-java-timed-data-source",
          stage: "COMPLETE",
          modified: "2024-05-29T11:02:35.183Z",
          created: "2024-05-29T11:02:35.175Z",
          flows: [
            {
              name: "hello-world-java-timed-data-source",
              state: "COMPLETE",
              created: "2024-05-29T11:02:35.175Z",
              modified: "2024-05-29T11:02:35.183Z",
              actions: [
                {
                  name: "HelloWorldTimedIngressAction",
                  created: "2024-05-29T11:02:35.175Z",
                  modified: "2024-05-29T11:02:35.183Z",
                  filteredCause: null,
                  state: "COMPLETE"
                }
              ]
            },
            {
              name: "hello-world-java-data-route-or-filter",
              state: "COMPLETE",
              created: "2024-05-29T11:02:35.183Z",
              modified: "2024-05-29T11:02:35.183Z",
              actions: [
                {
                  name: "NO_SUBSCRIBERS",
                  created: "2024-05-29T11:02:35.183Z",
                  modified: "2024-05-29T11:02:35.183Z",
                  filteredCause: "No matching subscribers were found",
                  state: "FILTERED"
                }
              ]
            }
          ]
        },
        {
          did: "2206a40d-83ce-44c3-b24b-4d7d51641f67",
          name: "hello-world-java-timed-data-source-12875",
          dataSource: "hello-world-java-timed-data-source",
          stage: "COMPLETE",
          modified: "2024-05-29T11:02:30.155Z",
          created: "2024-05-29T11:02:30.147Z",
          flows: [
            {
              name: "hello-world-java-timed-data-source",
              state: "COMPLETE",
              created: "2024-05-29T11:02:30.147Z",
              modified: "2024-05-29T11:02:30.155Z",
              actions: [
                {
                  name: "HelloWorldTimedIngressAction",
                  created: "2024-05-29T11:02:30.147Z",
                  modified: "2024-05-29T11:02:30.155Z",
                  filteredCause: null,
                  state: "COMPLETE"
                }
              ]
            },
            {
              name: "hello-world-java-data-route-or-filter",
              state: "COMPLETE",
              created: "2024-05-29T11:02:30.155Z",
              modified: "2024-05-29T11:02:30.155Z",
              actions: [
                {
                  name: "NO_SUBSCRIBERS",
                  created: "2024-05-29T11:02:30.155Z",
                  modified: "2024-05-29T11:02:30.155Z",
                  filteredCause: "No matching subscribers were found",
                  state: "FILTERED"
                }
              ]
            }
          ]
        }
    ]
  }
};

export default () => {
  return {
    deltaFiles: generateData()
  }
}