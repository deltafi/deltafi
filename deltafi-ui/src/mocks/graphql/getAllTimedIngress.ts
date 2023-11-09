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
    timedIngress: [
      {
        name: "hello-world-mock-timed-ingress",
        description: "Hello World mock timed ingress",
        flowStatus: {
          state: "STOPPED"
        },
        targetFlow: "mock-flow",
        cronSchedule: "*/5 * * * * *",
        lastRun: null,
        nextRun: "2023-11-07T19:23:00.000Z",
        memo: null,
        currentDid: null,
        executeImmediate: false,
        ingressStatus: "HEALTHY",
        ingressStatusMessage: null
      },
      {
        name: "smoke-test-ingress",
        description: "Create smoke DeltaFiles",
        flowStatus: {
          state: "RUNNING"
        },
        targetFlow: "smoke",
        cronSchedule: "*/1 * * * * *",
        lastRun: "2023-11-07T19:26:05.213Z",
        nextRun: "2023-11-07T19:26:10.000Z",
        memo: "28756",
        currentDid: null,
        executeImmediate: false,
        ingressStatus: "HEALTHY",
        ingressStatusMessage: null
      }
    ]

  }
};

export default () => {
  return {
    getAllFlows: generateData()
  }
}