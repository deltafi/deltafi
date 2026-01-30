/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>

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
    plugins: [
      {
        imageName: "deltafi/deltafi-core-actions",
        imagePullSecret: null,
        pluginCoordinates: {
          groupId: "org.deltafi",
          artifactId: "deltafi-core-actions",
          version: "2.47.1",
        },
      },
      {
        imageName: "deltafi/deltafi-passthrough",
        imagePullSecret: null,
        pluginCoordinates: {
          groupId: "org.deltafi",
          artifactId: "deltafi-passthrough",
          version: "2.47.0",
        },
      },
    ],
    deltaFiProperties: [
      { key: "deltafi.ingress.diskSpaceRequirementInMb", value: "500" },
      { key: "deltafi.delete.frequency", value: "PT15M" },
    ],
    restDataSources: [
      { name: "smoke-test-data-source", running: true, testMode: false },
      { name: "passthrough-data-source", running: false, testMode: false },
    ],
    timedDataSources: [],
    onErrorDataSources: [],
    transformFlows: [
      { name: "smoke-test-transform", running: true, testMode: false },
      { name: "passthrough-transform", running: false, testMode: false },
    ],
    dataSinks: [
      { name: "smoke-test-data-sink", running: true, testMode: false },
      { name: "passthrough-data-sink", running: false, testMode: false },
    ],
    deletePolicies: [],
    resumePolicies: [],
    links: [],
    users: [],
    roles: [],
  };
};

export default generateData();
