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

export default {
  queueMetrics: {
    timestamp: new Date().toISOString(),
    warmQueues: [
      {
        flowName: "smoke-test-transform",
        flowType: "TRANSFORM",
        actionName: "SmokeTestTransformAction",
        actionClass: "org.deltafi.testjig.action.SmokeTestTransformAction",
        count: 150,
        oldestQueuedAt: new Date(Date.now() - 60000).toISOString(),
      },
      {
        flowName: "hello-world-java-transform",
        flowType: "TRANSFORM",
        actionName: "HelloWorldJavaAction",
        actionClass: "org.deltafi.helloworld.action.HelloWorldJavaAction",
        count: 75,
        oldestQueuedAt: new Date(Date.now() - 120000).toISOString(),
      },
      {
        flowName: "passthrough-data-sink",
        flowType: "DATA_SINK",
        actionName: "PassthroughEgressAction",
        actionClass: "org.deltafi.testjig.action.PassthroughEgressAction",
        count: 50,
        oldestQueuedAt: new Date(Date.now() - 30000).toISOString(),
      },
    ],
    coldQueues: [
      {
        flowName: "smoke-test-transform",
        flowType: "TRANSFORM",
        actionName: "SmokeTestTransformAction",
        actionClass: "org.deltafi.testjig.action.SmokeTestTransformAction",
        count: 500,
        oldestQueuedAt: new Date(Date.now() - 300000).toISOString(),
      },
      {
        flowName: "hello-world-java-transform",
        flowType: "TRANSFORM",
        actionName: "HelloWorldJavaAction",
        actionClass: "org.deltafi.helloworld.action.HelloWorldJavaAction",
        count: 200,
        oldestQueuedAt: new Date(Date.now() - 600000).toISOString(),
      },
    ],
  },
};
