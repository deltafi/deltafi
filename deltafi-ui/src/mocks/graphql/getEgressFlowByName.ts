/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>

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
    name: "passthrough",
    description: "Egress flow that passes data through unchanged",
    sourcePlugin: {
      groupId: "org.deltafi.passthrough",
      artifactId: "deltafi-passthrough",
      version: "0.95.4",
    },
    flowStatus: flowStatusMap.get(flowStatus[Math.floor(Math.random() * flowStatus.length)]),
    includeIngressFlows: ["passthrough"],
    excludeIngressFlows: [],
    formatAction: {
      name: "passthrough.PassthroughFormatAction",
      parameters: null,
      apiVersion: null,
      requiresDomains: ["binary"],
      requiresEnrichments: [],
      type: "org.deltafi.passthrough.action.RoteFormatAction",
    },
    validateActions: [
      {
        name: "passthrough.PassthroughValidateAction",
        apiVersion: null,
        parameters: null,
        type: "org.deltafi.passthrough.action.RubberStampValidateAction",
      },
    ],
    egressAction: {
      apiVersion: null,
      name: "passthrough.PassthroughEgressAction",
      parameters: {
        egressFlow: "egressFlow",
        metadataKey: "deltafiMetadata",
        url: "http://deltafi-egress-sink-service",
      },
      type: "org.deltafi.core.action.RestPostEgressAction",
    },
    variables: [
      {
        name: "egressUrl",
        value: null,
        dataType: "STRING",
        description: "The URL to post the DeltaFile to",
        defaultValue: "http://deltafi-egress-sink-service",
        required: true,
      },
    ],
  };
};

export default {
  getEgressFlow: generateData(),
};
