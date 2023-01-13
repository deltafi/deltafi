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

const errors = [
  {
    configName: "passthroughv2",
    errorType: "UNRESOLVED_VARIABLE",
    message: "Could not resolve placeholder 'notFound' in value \"${notFound}\"",
  },
  {
    configName: "PassthroughLoadAction",
    errorType: "INVALID_ACTION_PARAMETERS",
    message: "$.unexpected: is not defined in the schema and the schema does not allow additional properties",
  },
  {
    configName: "PassthroughTransformAction",
    errorType: "UNREGISTERED_ACTION",
    message: "Action: org.deltafi.passthrough.action.MissingRoteTransformAction has not been registered with the system",
  },
  {
    configName: "NotReallyTranformAction",
    errorType: "INVALID_ACTION_CONFIG",
    message: "Action: org.deltafi.passthrough.action.RoteLoadAction is not registered as a TransformAction",
  },
];

const flowStatus = ["STOPPED", "ERRORS"];
const flowStatusMap = new Map([
  ["STOPPED", { state: "STOPPED", errors: [] }],
  ["ERRORS", { state: "STOPPED", errors: errors }],
]);

const generateData = () => {
  return {
    name: "decompress-passthrough",
    description: "Flow that passes data through unchanged",
    sourcePlugin: {
      groupId: "org.deltafi.passthrough",
      artifactId: "deltafi-passthrough",
      version: "0.95.4",
    },
    flowStatus: flowStatusMap.get(flowStatus[Math.floor(Math.random() * flowStatus.length)]),
    transformActions: [
      {
        name: "decompress-passthrough.DecompressPassthroughTransformAction1",
        parameters: {
          decompressionType: "auto",
        },
        requiresDomains: ["binary", "non-binary"],
        type: "org.deltafi.core.action.DecompressionTransformAction",
        apiVersion: null,
      },
      {
        name: "decompress-passthrough.DecompressPassthroughTransformAction2",
        parameters: {
          decompressionType: "auto",
        },
        type: "org.deltafi.core.action.DecompressionTransformAction",
        apiVersion: null,
      },
    ],
    loadAction: {
      name: "decompress-passthrough.DecompressPassthroughLoadAction",
      apiVersion: null,
      parameters: {
        reinjectFlow: "passthrough",
      },
    },
    variables: [],
  };
};

export default {
  getIngressFlow: generateData(),
};
