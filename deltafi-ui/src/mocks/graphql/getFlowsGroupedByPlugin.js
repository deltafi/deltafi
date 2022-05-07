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

import _ from "lodash";

const flowStatus = ["RUNNING", "STOPPED"];
const pluginsArtifactIds = ["smoke", "passthrough", "decompress-passthrough", "stix1_x", "stix2_1", "deltafi-stix", "error"];
const randomArtifact = pluginsArtifactIds[Math.floor(Math.random() * pluginsArtifactIds.length)];
const pluginsVersions = ["0.17.0", "0.21.0-SNAPSHOT", "0.22.0-SNAPSHOT", "0.99.0"];
const randomVersion = pluginsVersions[Math.floor(Math.random() * pluginsVersions.length)];
const errors = [
  {
    "configName": "passthroughv2",
    "errorType": "UNRESOLVED_VARIABLE",
    "message": "Could not resolve placeholder 'notFound' in value \"${notFound}\""
  },
  {
    "configName": "PassthroughLoadAction",
    "errorType": "INVALID_ACTION_PARAMETERS",
    "message": "$.unexpected: is not defined in the schema and the schema does not allow additional properties"
  },
  {
    "configName": "PassthroughTransformAction",
    "errorType": "UNREGISTERED_ACTION",
    "message": "Action: org.deltafi.passthrough.action.MissingRoteTransformAction has not been registered with the system"
  },
  {
    "configName": "NotReallyTranformAction",
    "errorType": "INVALID_ACTION_CONFIG",
    "message": "Action: org.deltafi.passthrough.action.RoteLoadAction is not registered as a TransformAction"
  }
];
const flowOptions = new Map([
  ['decompress-passthrough', { description: 'flow that decompresses data', egressFlow: ['passthrough', 'egressFlow'], ingressFlows: ['decompress-passthrough', 'passthrough', 'ingressFlow'], flowStatus: flowStatus }],
  ['deltafi-stix', { description: 'flow that transforms data into stix1_x and stix2_1', egressFlow: ['stix1_x', 'stix2_1'], ingressFlows: ['stix1_x', 'stix2_1'], flowStatus: flowStatus }],
  ['error', { description: 'flow that has had an error', egressFlow: ['egress'], ingressFlows: ['ingress'], flowStatus: ['STOPPED'], errors }],
  ['passthrough', { description: 'flow that passes data through unchanged', egressFlow: ['passthrough', 'egressFlow'], ingressFlows: ['passthrough', 'ingressFlow'], flowStatus: flowStatus }],
  ['smoke', { description: 'flow that is used in testing', egressFlow: ['egressFlow'], ingressFlows: ['ingressFlow'], flowStatus: flowStatus }],
  ['stix1_x', { description: 'flow that transforms data into stix1_x', egressFlow: ['stix2_1'], ingressFlows: ['stix1_x'], flowStatus: flowStatus }],
  ['stix2_1', { description: 'flow that outputs stix2.1 json', egressFlow: ['stix1_x'], ingressFlows: ['stix2_1'], flowStatus: flowStatus }],
]);

const plugin = {
  "sourcePlugin": {
    "artifactId": randomArtifact,
    "groupId": "org.deltafi.stix",
    "version": randomVersion
  },
  "variables": [
    {
      "name": "egressUrl",
      "value": null,
      "description": "The URL to post the DeltaFile to",
      "defaultValue": "http://deltafi-egress-sink-service",
      "dataType": "STRING"
    }
  ],
  "ingressFlows": [
    {
      "name": flowOptions.get(randomArtifact).ingressFlows[Math.floor(Math.random() * flowOptions.get(randomArtifact).ingressFlows.length)] + ".1",
      "description": "Ingress " + flowOptions.get(randomArtifact).description,
      "type": randomArtifact,
      "flowStatus": {
        "state": flowOptions.get(randomArtifact).flowStatus[Math.floor(Math.random() * flowOptions.get(randomArtifact).flowStatus.length)],
        "errors": (!_.isEmpty(flowOptions.get(randomArtifact).errors) ? flowOptions.get(randomArtifact).errors : null)
      },
      "transformActions": [
        {
          "name": "stix1_x.Stix1_xTo2_1TransformAction",
          "type": "org.deltafi.stix.actions.StixTransformAction",
          "consumes": "stix1_x",
          "produces": "stix2_1",
          "parameters": null
        }
      ],
      "loadAction": {
        "name": "stix1_x.Stix2_1LoadAction",
        "type": "org.deltafi.stix.actions.StixLoadAction",
        "consumes": "stix2_1",
        "parameters": null
      },
      "variables": [
                    {
              "name": "ingressUrl",
              "value": null,
              "description": "The URL to post the DeltaFile to",
              "defaultValue": "http://deltafi-egress-sink-service",
              "dataType": "STRING"
            }
      ]
    },
    {
      "name": flowOptions.get(randomArtifact).ingressFlows[Math.floor(Math.random() * flowOptions.get(randomArtifact).ingressFlows.length)] + ".2",
      "description": "Ingress " + flowOptions.get(randomArtifact).description,
      "type": randomArtifact,
      "flowStatus": {
        "state": flowOptions.get(randomArtifact).flowStatus[Math.floor(Math.random() * flowOptions.get(randomArtifact).flowStatus.length)],
        "errors": (!_.isEmpty(flowOptions.get(randomArtifact).errors) ? flowOptions.get(randomArtifact).errors : null)
      },
      "transformActions": [],
      "loadAction": {
        "name": "stix2_1.Stix2_1LoadAction",
        "type": "org.deltafi.stix.actions.StixLoadAction",
        "consumes": "stix2_1",
        "parameters": null
      },
      "variables": []
    }
  ],
  "egressFlows": [
    {
      "name": flowOptions.get(randomArtifact).egressFlows[Math.floor(Math.random() * flowOptions.get(randomArtifact).ingressFlows.length)] + ".1",
      "description": "Ingress " + flowOptions.get(randomArtifact).description,
      "flowStatus": {
        "state": flowOptions.get(randomArtifact).flowStatus[Math.floor(Math.random() * flowOptions.get(randomArtifact).flowStatus.length)],
        "errors": (!_.isEmpty(flowOptions.get(randomArtifact).errors) ? flowOptions.get(randomArtifact).errors : null)
      },
      "includeIngressFlows": [],
      "excludeIngressFlows": [],
      "enrichActions": [],
      "formatAction": {
        "name": "stix1_x.Stix1_xFormatAction",
        "type": "org.deltafi.stix.actions.Stix1_XFormatAction",
        "requiresDomains": [
          "stix"
        ],
        "requiresEnrichment": [],
        "parameters": null
      },
      "validateActions": [],
      "egressAction": {
        "name": "stix1_x.Stix1_xEgressAction",
        "type": "org.deltafi.core.action.RestPostEgressAction",
        "parameters": {
          "egressFlow": "stix1_x",
          "metadataKey": "deltafiMetadata",
          "url": "http://deltafi-egress-sink-service"
        }
      },
      "variables": [
        {
          "name": "stix1_x-egress-url-one",
          "value": "stix1_x-value-one",
          "description": "The URL to post the DeltaFile to",
          "defaultValue": "http://deltafi-egress-sink-service",
          "dataType": "STRING"
        },
        {
          "name": "stix1_x-egress-url-two",
          "value": "stix1_x-value-two",
          "description": "The URL to post the DeltaFile to",
          "defaultValue": "http://deltafi-egress-sink-service",
          "dataType": "STRING"
        }
      ]
    },
    {
      "name": flowOptions.get(randomArtifact).egressFlows[Math.floor(Math.random() * flowOptions.get(randomArtifact).ingressFlows.length)] + ".2",
      "description": "Ingress " + flowOptions.get(randomArtifact).description,
      "flowStatus": {
        "state": flowOptions.get(randomArtifact).flowStatus[Math.floor(Math.random() * flowOptions.get(randomArtifact).flowStatus.length)],
        "errors": (!_.isEmpty(flowOptions.get(randomArtifact).errors) ? flowOptions.get(randomArtifact).errors : null)
      },
      "includeIngressFlows": [],
      "excludeIngressFlows": [],
      "enrichActions": [],
      "formatAction": {
        "name": "stix2_1.Stix2_1FormatAction",
        "type": "org.deltafi.stix.actions.Stix2_1FormatAction",
        "requiresDomains": [
          "stix"
        ],
        "requiresEnrichment": [],
        "parameters": null
      },
      "validateActions": [],
      "egressAction": {
        "name": "stix2_1.Stix2_1EgressAction",
        "type": "org.deltafi.core.action.RestPostEgressAction",
        "parameters": {
          "egressFlow": "stix2_1",
          "metadataKey": "deltafiMetadata",
          "url": "http://deltafi-egress-sink-service"
        }
      },
      "variables": [
        {
          "name": "stix2_1-egress-url-one",
          "value": "stix2_1-value-one",
          "description": "The URL to post the DeltaFile to",
          "defaultValue": "http://deltafi-egress-sink-service",
          "dataType": "STRING"
        },
        {
          "name": "stix2_1-egress-url-two",
          "value": "stix2_1-value-two",
          "description": "The URL to post the DeltaFile to",
          "defaultValue": "http://deltafi-egress-sink-service",
          "dataType": "STRING"
        }
      ]
    }
  ]
}

const empytFlowsPlugin = {
  "sourcePlugin": {
    "artifactId": "deltafi-core-actions",
    "groupId": "org.deltafi",
    "version": "0.17.0"
  },
  "variables": [],
  "ingressFlows": [],
  "egressFlows": []
}

const generateFlows = (count) => {

  const pluginsArray = [plugin, empytFlowsPlugin]

  return Array.from(Array(count)).map(() => {
    return pluginsArray[Math.floor(Math.random() * pluginsArray.length)];
  })
};

const numberOfFlows = [2, 4, 5, 10, 20];

export default {
    "getFlows": generateFlows(numberOfFlows[Math.floor(Math.random() * numberOfFlows.length)])
}