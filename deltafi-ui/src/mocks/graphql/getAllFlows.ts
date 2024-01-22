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
const getEnrichedData = () => {
  const data = [
    {
      name: "smoke",
      description: "Test flow that passes data through enrich flow unchanged",
      sourcePlugin: {
        artifactId: "smoke",
        groupId: "org.deltafi",
        version: "0.17.0",
      },
      flowStatus: {
        state: "RUNNING",
        errors: errors,
      },
      variables: [
        {
          name: "smoke-enrich-url",
          value: "smoke-value",
          description: "The URL to post the DeltaFile to",
          defaultValue: "http://deltafi-enrich-sink-service",
          dataType: "STRING",
        },
      ],
    },
  ];
  return data;
};

const enrichData = [[], getEnrichedData()];

const generateFlows = () => {
  return {
    transform: [
      {
        name: "keyword-extraction",
        subscriptions: [{
          condition: "Run At Night",
          topics: ["json", "HTML"],
        }],
        description: "Extract keywords from text",
        type: "text/plain",
        flowStatus: {
          state: "STOPPED",
          errors: [],
          testMode: false,
        },
        sourcePlugin: {
          artifactId: "keyword-actions",
          groupId: "org.deltafi",
          version: "1.0.0",
        },
        maxErrors: -1,
        transformActions: [
          {
            name: "keyword.KeywordExtractionTransformAction",
            type: "org.deltafi.keyword.action.KeywordExtractionTransformAction",
            parameters: {
              resultType: "text/plain",
            },
            apiVersion: null,
            collect: null,
          },
        ],
        egressAction: {
          name: "keyword.KeywordEgressAction",
          type: "org.deltafi.core.action.RestPostEgressAction",
          parameters: {
            metadataKey: "deltafiMetadata",
            url: "http://deltafi-egress-sink-service",
          },
          apiVersion: null,
        },
        variables: [],
        expectedAnnotations: null,
      }
    ],
    egress: [
      {
        name: "error",
        description: "Defines the flow for errored DeltaFiles",
        flowStatus: {
          state: "RUNNING",
          errors: [],
        },
        sourcePlugin: {
          artifactId: "deltafi-core-actions",
          groupId: "org.deltafi",
          version: "0.17.0",
        },
        egressAction: {
          name: "error.ErrorEgressAction",
          type: "org.deltafi.core.action.RestPostEgressAction",
          parameters: {
            egressFlow: "egress",
            metadataKey: "deltafiMetadata",
            url: "http://deltafi-egress-sink-service",
          },
        },
        variables: [],
      },
      {
        name: "passthrough",
        description: "Egress flow that passes data through unchanged",
        flowStatus: {
          state: "RUNNING",
          errors: errors,
        },
        sourcePlugin: {
          artifactId: "passthrough",
          groupId: "org.deltafi",
          version: "0.17.0",
        },
        egressAction: {
          name: "passthrough.PassthroughEgressAction",
          type: "org.deltafi.core.action.RestPostEgressAction",
          parameters: {
            egressFlow: "egressFlow",
            metadataKey: "deltafiMetadata",
            url: "http://deltafi-egress-sink-service",
          },
        },
        variables: [
          {
            name: "passthrough-egressUrl-one",
            value: "passthrough-value-one",
            description: "The URL to post the DeltaFile to",
            defaultValue: "http://deltafi-egress-sink-service",
            dataType: "STRING",
          },
          {
            name: "passthrough-egressUrl-two",
            value: "passthrough-value-two",
            description: "The URL to post the DeltaFile to",
            defaultValue: "http://deltafi-egress-sink-service",
            dataType: "STRING",
          },
        ],
      },
      {
        name: "smoke",
        description: "Test flow that passes data through unchanged",
        flowStatus: {
          state: "STOPPED",
          errors: [],
        },
        sourcePlugin: {
          artifactId: "smoke",
          groupId: "org.deltafi",
          version: "0.17.0",
        },
        egressAction: {
          name: "smoke.SmokeEgressAction",
          type: "org.deltafi.core.action.RestPostEgressAction",
          parameters: {
            egressFlow: "smoke",
            metadataKey: "deltafiMetadata",
            url: "http://deltafi-egress-sink-service",
          },
        },
        variables: [
          {
            name: "smoke-egress-url",
            value: "smoke-value",
            description: "The URL to post the DeltaFile to",
            defaultValue: "http://deltafi-egress-sink-service",
            dataType: "STRING",
          },
        ],
      },
      {
        name: "stix1_x",
        description: "Defines the egress flow that outputs stix1.x xml.",
        flowStatus: {
          state: "RUNNING",
          errors: [],
        },
        sourcePlugin: {
          artifactId: "stix",
          groupId: "org.deltafi",
          version: "0.17.0",
        },
        egressAction: {
          name: "stix1_x.Stix1_xEgressAction",
          type: "org.deltafi.core.action.RestPostEgressAction",
          parameters: {
            egressFlow: "stix1_x",
            metadataKey: "deltafiMetadata",
            url: "http://deltafi-egress-sink-service",
          },
        },
        variables: [
          {
            name: "stix1_x-egress-Url",
            value: "stix1_x-value",
            description: "The URL to post the DeltaFile to",
            defaultValue: "http://deltafi-egress-sink-service",
            dataType: "STRING",
          },
        ],
      },
      {
        name: "stix2_1",
        description: "Defines the egress flow that outputs stix2.1 json.",
        flowStatus: {
          state: "RUNNING",
          errors: [],
        },
        sourcePlugin: {
          artifactId: "stix",
          groupId: "org.deltafi",
          version: "0.17.0",
        },
        egressAction: {
          name: "stix2_1.Stix2_1EgressAction",
          type: "org.deltafi.core.action.RestPostEgressAction",
          parameters: {
            egressFlow: "stix2_1",
            metadataKey: "deltafiMetadata",
            url: "http://deltafi-egress-sink-service",
          },
        },
        variables: [
          {
            name: "stix2_1-egress-url",
            value: "stix2_1-value",
            description: "The URL to post the DeltaFile to",
            defaultValue: "http://deltafi-egress-sink-service",
            dataType: "STRING",
          },
        ],
      },
    ],
  };
};

export default {
  getAllFlows: generateFlows(),
};
