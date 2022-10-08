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

import useGraphQL from "./useGraphQL";

export default function useFlowQueryBuilder() {
  const { response, queryGraphQL } = useGraphQL();

  // Get all flows grouped by plugin
  const getFlowsGroupedByPlugin = () => {
    const query = {
      getFlows: {
        sourcePlugin: {
          artifactId: true,
          groupId: true,
          version: true,
        },
        variables: {
          name: true,
          value: true,
          description: true,
          defaultValue: true,
          dataType: true,
        },
        ingressFlows: {
          name: true,
          description: true,
          type: true,
          flowStatus: {
            state: true,
            errors: {
              configName: true,
              errorType: true,
              message: true,
            },
          },
          transformActions: {
            name: true,
            type: true,
            parameters: true,
            apiVersion: true,
          },
          loadAction: {
            name: true,
            type: true,
            parameters: true,
            apiVersion: true,
          },
        },
        enrichFlows: {
          name: true,
          description: true,
          flowStatus: {
            state: true,
            errors: {
              configName: true,
              message: true,
              errorType: true,
            },
          },
          domainActions: {
            name: true,
            type: true,
            requiresDomains: true,
            parameters: true,
            apiVersion: true,
          },
          enrichActions: {
            name: true,
            type: true,
            requiresDomains: true,
            requiresEnrichments: true,
            requiresMetadataKeyValues: {
              key: true,
              value: true,
            },
            parameters: true,
            apiVersion: true,
          },
        },
        egressFlows: {
          name: true,
          description: true,
          flowStatus: {
            state: true,
            errors: {
              configName: true,
              errorType: true,
              message: true,
            },
          },
          includeIngressFlows: true,
          excludeIngressFlows: true,
          formatAction: {
            name: true,
            type: true,
            requiresDomains: true,
            requiresEnrichments: true,
            parameters: true,
            apiVersion: true,
          },
          validateActions: {
            name: true,
            type: true,
            parameters: true,
            apiVersion: true,
          },
          egressAction: {
            name: true,
            type: true,
            parameters: true,
            apiVersion: true,
          },
          variables: {
            name: true,
            value: true,
            description: true,
            defaultValue: true,
            dataType: true,
          },
        },
      },
    };
    return sendGraphQLQuery(query, "getFlowsGroupedByPlugin");
  };

  // Get all flows (no grouping)
  const getAllFlows = () => {
    const query = {
      getAllFlows: {
        ingress: {
          name: true,
          description: true,
          sourcePlugin: {
            artifactId: true,
            groupId: true,
            version: true,
          },
          flowStatus: {
            state: true,
            errors: {
              configName: true,
              errorType: true,
              message: true,
            },
          },
          transformActions: {
            name: true,
            type: true,
            parameters: true,
            apiVersion: true,
          },
          loadAction: {
            name: true,
            type: true,
            parameters: true,
            apiVersion: true,
          },
          variables: {
            name: true,
            value: true,
            description: true,
            defaultValue: true,
            dataType: true,
          },
        },
        enrich: {
          name: true,
          description: true,
          sourcePlugin: {
            groupId: true,
            artifactId: true,
            version: true,
          },
          flowStatus: {
            state: true,
            errors: {
              configName: true,
              message: true,
              errorType: true,
            },
          },
          domainActions: {
            name: true,
            type: true,
            requiresDomains: true,
            parameters: true,
            apiVersion: true,
          },
          enrichActions: {
            name: true,
            type: true,
            requiresDomains: true,
            requiresEnrichments: true,
            requiresMetadataKeyValues: {
              key: true,
              value: true,
            },
            parameters: true,
            apiVersion: true,
          },
        },
        egress: {
          name: true,
          description: true,
          sourcePlugin: {
            artifactId: true,
            groupId: true,
            version: true,
          },
          flowStatus: {
            state: true,
            errors: {
              configName: true,
              errorType: true,
              message: true,
            },
          },
          includeIngressFlows: true,
          excludeIngressFlows: true,
          formatAction: {
            name: true,
            type: true,
            requiresDomains: true,
            requiresEnrichments: true,
            parameters: true,
            apiVersion: true,
          },
          validateActions: {
            name: true,
            type: true,
            parameters: true,
            apiVersion: true,
          },
          egressAction: {
            name: true,
            type: true,
            parameters: true,
            apiVersion: true,
          },
          variables: {
            name: true,
            value: true,
            description: true,
            defaultValue: true,
            dataType: true,
          },
        },
      },
    };
    return sendGraphQLQuery(query, "getAllFlows");
  };

  // Get an Ingress Flow - (if you want to grab a single flow, return type is IngressFlow)
  const getIngressFlowByName = (flowName: string) => {
    const query = {
      getIngressFlow: {
        __args: {
          flowName: flowName,
        },
        name: true,
        description: true,
        sourcePlugin: {
          groupId: true,
          artifactId: true,
          version: true,
        },
        flowStatus: {
          state: true,
          errors: {
            configName: true,
            message: true,
            errorType: true,
          },
        },
        transformActions: {
          name: true,
          type: true,
          parameters: true,
          apiVersion: true,
        },
        loadAction: {
          name: true,
          type: true,
          parameters: true,
          apiVersion: true,
        },
        variables: {
          name: true,
          dataType: true,
          description: true,
          defaultValue: true,
          required: true,
        },
      },
    };
    return sendGraphQLQuery(query, "getIngressFlowByName");
  };

  // Get an Enrich Flow - (if you want to grab a single flow, return type is EnrichFlow)
  const getEnrichFlowByName = (flowName: string) => {
    const query = {
      getEnrichFlow: {
        __args: {
          flowName: flowName,
        },
        name: true,
        description: true,
        sourcePlugin: {
          groupId: true,
          artifactId: true,
          version: true,
        },
        flowStatus: {
          state: true,
          errors: {
            configName: true,
            message: true,
            errorType: true,
          },
        },
        domainActions: {
          name: true,
          type: true,
          requiresDomains: true,
          parameters: true,
          apiVersion: true,
        },
        enrichActions: {
          name: true,
          type: true,
          requiresDomains: true,
          requiresEnrichments: true,
          requiresMetadataKeyValues: {
            key: true,
            value: true,
          },
          parameters: true,
          apiVersion: true,
        },
      },
    };
    return sendGraphQLQuery(query, "getEnrichFlowByName");
  };

  // Get an Egress Flow - (if you want to grab a single flow, return type is EgressFlow)
  const getEgressFlowByName = (flowName: string) => {
    const query = {
      getEgressFlow: {
        __args: {
          flowName: flowName,
        },
        name: true,
        description: true,
        sourcePlugin: {
          groupId: true,
          artifactId: true,
          version: true,
        },
        flowStatus: {
          state: true,
          errors: {
            configName: true,
            errorType: true,
            message: true,
          },
        },
        includeIngressFlows: true,
        excludeIngressFlows: true,
        formatAction: {
          name: true,
          type: true,
          requiresDomains: true,
          requiresEnrichments: true,
          parameters: true,
          apiVersion: true,
        },
        validateActions: {
          name: true,
          type: true,
          parameters: true,
          apiVersion: true,
        },
        egressAction: {
          name: true,
          type: true,
          parameters: true,
          apiVersion: true,
        },
        variables: {
          name: true,
          value: true,
          dataType: true,
          description: true,
          defaultValue: true,
          required: true,
        },
      },
    };
    return sendGraphQLQuery(query, "getEgressFlowByName");
  };

  // Validate an ingress flow - return type is IngressFlow
  const validateIngressFlow = (flowName: string) => {
    const query = {
      validateIngressFlow: {
        __args: {
          flowName: flowName,
        },
        flowStatus: {
          state: true,
          errors: {
            configName: true,
            errorType: true,
            message: true,
          },
        },
      },
    };
    return sendGraphQLQuery(query, "validateIngressFlow");
  };

  // Validate an Enrich flow - return type is EnrichFlow
  const validateEnrichFlow = (flowName: string) => {
    const query = {
      validateEnrichFlow: {
        __args: {
          flowName: flowName,
        },
        flowStatus: {
          state: true,
          errors: {
            configName: true,
            errorType: true,
            message: true,
          },
        },
      },
    };
    return sendGraphQLQuery(query, "validateEnrichFlow");
  };

  // Validate an egress flow - return type is EgressFlow
  const validateEgressFlow = (flowName: string) => {
    const query = {
      validateEgressFlow: {
        __args: {
          flowName: flowName,
        },
        flowStatus: {
          state: true,
          errors: {
            configName: true,
            errorType: true,
            message: true,
          },
        },
      },
    };
    return sendGraphQLQuery(query, "validateEgressFlow");
  };

  // Set plugin variables
  const setPluginVariables = (pluginVariables: string) => {
    const query = {
      setPluginVariableValues: {
        __args: {
          name: pluginVariables,
        },
      },
    };
    return sendGraphQLQuery(query, "setPluginVariables", "mutation");
  };

  // Starts an ingress flow
  const startIngressFlowByName = (flowName: string) => {
    const query = {
      startIngressFlow: {
        __args: {
          flowName: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "startIngressFlowByName", "mutation");
  };

  // Stops an ingress flow
  const stopIngressFlowByName = (flowName: string) => {
    const query = {
      stopIngressFlow: {
        __args: {
          flowName: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "stopIngressFlowByName", "mutation");
  };

  // Starts an enrich flow
  const startEnrichFlowByName = (flowName: string) => {
    const query = {
      startEnrichFlow: {
        __args: {
          flowName: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "startEnrichFlowByName", "mutation");
  };

  // Stops an enrich flow
  const stopEnrichFlowByName = (flowName: string) => {
    const query = {
      stopEnrichFlow: {
        __args: {
          flowName: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "stopEnrichFlowByName", "mutation");
  };

  // Starts an egress flow
  const startEgressFlowByName = (flowName: string) => {
    const query = {
      startEgressFlow: {
        __args: {
          flowName: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "startEgressFlowByName", "mutation");
  };

  // Stops an egress flow
  const stopEgressFlowByName = (flowName: string) => {
    const query = {
      stopEgressFlow: {
        __args: {
          flowName: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "stopEgressFlowByName", "mutation");
  };

  const sendGraphQLQuery = async (query: any, operationName: string, queryType?: string) => {
    try {
      await queryGraphQL(query, operationName, queryType);
      return response.value;
    } catch {
      // Continue regardless of error
    }
  };

  return {
    getFlowsGroupedByPlugin,
    getAllFlows,
    getIngressFlowByName,
    getEnrichFlowByName,
    getEgressFlowByName,
    validateIngressFlow,
    validateEnrichFlow,
    validateEgressFlow,
    setPluginVariables,
    startIngressFlowByName,
    stopIngressFlowByName,
    startEnrichFlowByName,
    stopEnrichFlowByName,
    startEgressFlowByName,
    stopEgressFlowByName,
  };
}
