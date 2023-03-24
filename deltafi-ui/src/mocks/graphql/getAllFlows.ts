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
      enrichActions: [
        {
          name: "smoke.SmokeEnrichAction",
          type: "org.deltafi.passthrough.action.RoteEnrichAction",
          requiresDomains: ["binary"],
          requiresEnrichments: [],
          requiresMetadataKeyValues: [],
          parameters: {
            enrichments: {
              smokeEnrichment: "smoke enrichment value",
            },
          },
        },
      ],
      domainActions: [
        {
          name: "stix2_1.Stix2_1DomainAction",
          type: "org.deltafi.stix.actions.Stix2_1DomainAction",
          requiresDomains: ["stix"],
          parameters: {
            domains: {
              stixDomain: "stix domain value",
            },
          },
        },
      ],
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
    ingress: [
      {
        name: "smoke",
        description: "Test flow that passes data through unchanged",
        type: "binary",
        flowStatus: {
          state: "STOPPED",
          errors: [],
        },
        sourcePlugin: {
          artifactId: "smoke",
          groupId: "org.deltafi",
          version: "0.17.0",
        },
        maxErrors: 3,
        transformActions: [
          {
            name: "smoke.SmokeTransformAction",
            type: "org.deltafi.passthrough.action.RoteTransformAction",
            parameters: {
              resultType: "binary",
            },
          },
        ],
        loadAction: {
          name: "smoke.SmokeLoadAction",
          type: "org.deltafi.passthrough.action.RoteLoadAction",
          parameters: {
            domains: ["binary"],
          },
        },
        variables: [],
      },
      {
        name: "decompress-passthrough",
        description: "Flow that passes data through unchanged",
        type: "compressedBinary",
        flowStatus: {
          state: "STOPPED",
          errors: [],
        },
        sourcePlugin: {
          artifactId: "decompress-passthrough",
          groupId: "org.deltafi",
          version: "0.17.0",
        },
        maxErrors: 2,
        transformActions: [
          {
            name: "decompress-passthrough.DecompressPassthroughTransformAction",
            type: "org.deltafi.core.action.DecompressionTransformAction",
            parameters: {
              decompressionType: "auto",
            },
          },
        ],
        loadAction: {
          name: "decompress-passthrough.DecompressPassthroughLoadAction",
          type: "org.deltafi.core.action.SplitterLoadAction",
          parameters: {
            reinjectFlow: "passthrough",
          },
        },
        variables: [],
      },
      {
        name: "passthrough",
        description: "Flow that passes data through unchanged",
        type: "binary",
        flowStatus: {
          state: "RUNNING",
          errors: errors,
        },
        sourcePlugin: {
          artifactId: "passthrough",
          groupId: "org.deltafi",
          version: "0.17.0",
        },
        maxErrors: 2,
        transformActions: [
          {
            name: "passthrough.PassthroughTransformAction",
            type: "org.deltafi.passthrough.action.RoteTransformAction",
            parameters: {
              resultType: "binary",
            },
          },
        ],
        loadAction: {
          name: "passthrough.PassthroughLoadAction",
          type: "org.deltafi.passthrough.action.RoteLoadAction",
          parameters: {
            domains: ["binary"],
          },
        },
        variables: [],
      },
      {
        name: "stix1_x",
        description: "Defines stix1_x ingress flow that transforms stix 1.x to stix 2.1 domain objects.",
        type: "stix1_x",
        flowStatus: {
          state: "RUNNING",
          errors: [],
        },
        sourcePlugin: {
          artifactId: "stix",
          groupId: "org.deltafi",
          version: "0.17.0",
        },
        maxErrors: 1,
        transformActions: [
          {
            name: "stix1_x.Stix1_xTo2_1TransformAction",
            type: "org.deltafi.stix.actions.StixTransformAction",
            parameters: null,
          },
        ],
        loadAction: {
          name: "stix1_x.Stix2_1LoadAction",
          type: "org.deltafi.stix.actions.StixLoadAction",
          parameters: null,
        },
        variables: [],
      },
      {
        name: "stix2_1",
        description: "Defines stix2_1 ingress flow.",
        type: "stix2_1",
        flowStatus: {
          state: "STOPPED",
          errors: [],
        },
        sourcePlugin: {
          artifactId: "stix",
          groupId: "org.deltafi",
          version: "0.17.0",
        },
        maxErrors: 1,
        transformActions: [],
        loadAction: {
          name: "stix2_1.Stix2_1LoadAction",
          type: "org.deltafi.stix.actions.StixLoadAction",
          parameters: null,
        },
        variables: [],
      },
    ],
    enrich: enrichData[Math.floor(Math.random() * enrichData.length)],
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
        includeIngressFlows: [],
        excludeIngressFlows: [],
        enrichActions: [],
        formatAction: {
          name: "error.ErrorFormatAction",
          type: "org.deltafi.core.action.SimpleErrorFormatAction",
          requiresDomains: ["error"],
          requiresEnrichments: [],
          parameters: null,
        },
        validateActions: [],
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
        includeIngressFlows: ["passthrough"],
        excludeIngressFlows: [],
        enrichActions: [],
        formatAction: {
          name: "passthrough.PassthroughFormatAction",
          type: "org.deltafi.passthrough.action.RoteFormatAction",
          requiresDomains: ["binary"],
          requiresEnrichments: [],
          parameters: null,
        },
        validateActions: [
          {
            name: "passthrough.PassthroughValidateAction",
            type: "org.deltafi.passthrough.action.RubberStampValidateAction",
            parameters: null,
          },
        ],
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
        includeIngressFlows: ["smoke"],
        excludeIngressFlows: [],
        enrichActions: [
          {
            name: "smoke.SmokeEnrichAction",
            type: "org.deltafi.passthrough.action.RoteEnrichAction",
            requiresDomains: ["binary"],
            requiresEnrichments: [],
            requiresMetadataKeyValues: [],
            parameters: {
              enrichments: {
                smokeEnrichment: "smoke enrichment value",
              },
            },
          },
        ],
        formatAction: {
          name: "smoke.SmokeFormatAction",
          type: "org.deltafi.passthrough.action.RoteFormatAction",
          requiresDomains: ["binary"],
          requiresEnrichments: ["smokeEnrichment"],
          parameters: null,
        },
        validateActions: [
          {
            name: "smoke.SmokeValidateAction",
            type: "org.deltafi.passthrough.action.RubberStampValidateAction",
            parameters: null,
          },
        ],
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
        includeIngressFlows: [],
        excludeIngressFlows: [],
        enrichActions: [],
        formatAction: {
          name: "stix1_x.Stix1_xFormatAction",
          type: "org.deltafi.stix.actions.Stix1_XFormatAction",
          requiresDomains: ["stix"],
          requiresEnrichments: [],
          parameters: null,
        },
        validateActions: [],
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
        includeIngressFlows: [],
        excludeIngressFlows: [],
        enrichActions: [],
        formatAction: {
          name: "stix2_1.Stix2_1FormatAction",
          type: "org.deltafi.stix.actions.Stix2_1FormatAction",
          requiresDomains: ["stix"],
          requiresEnrichments: [],
          parameters: null,
        },
        validateActions: [],
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
