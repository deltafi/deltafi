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

import useGraphQL from "./useGraphQL";

export default function useFlowQueryBuilder() {
  const { response, errors, queryGraphQL, loaded, loading } = useGraphQL();

  const sourcePluginFields = {
    sourcePlugin: {
      artifactId: true,
      groupId: true,
      version: true,
    }
  };

  const variableFields = {
    variables: {
      name: true,
      value: true,
      description: true,
      defaultValue: true,
      dataType: true,
    }
  };

  const transformFlowFields = {
    name: true,
    description: true,
    flowStatus: {
      state: true,
      errors: {
        configName: true,
        errorType: true,
        message: true,
      },
      testMode: true,
    },
    maxErrors: true,
    transformActions: {
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
    ...variableFields,
    expectedAnnotations: true,
  };

  const normalizeFlowFields = {
    name: true,
    description: true,
    flowStatus: {
      state: true,
      errors: {
        configName: true,
        errorType: true,
        message: true,
      },
      testMode: true,
    },
    maxErrors: true,
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
    ...variableFields
  };

  const enrichFlowFields = {
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
    ...variableFields
  };

  const egressFlowFields = {
    name: true,
    description: true,
    flowStatus: {
      state: true,
      errors: {
        configName: true,
        errorType: true,
        message: true,
      },
      testMode: true,
    },
    includeNormalizeFlows: true,
    excludeNormalizeFlows: true,
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
    ...variableFields,
    expectedAnnotations: true,
  };

  // Get all flows (no grouping)
  const getAllFlows = () => {
    const query = {
      getAllFlows: {
        transform: {
          ...sourcePluginFields,
          ...transformFlowFields
        },
        normalize: {
          ...sourcePluginFields,
          ...normalizeFlowFields
        },
        enrich: {
          ...sourcePluginFields,
          ...enrichFlowFields
        },
        egress: {
          ...sourcePluginFields,
          ...egressFlowFields
        },
      },
    };
    return sendGraphQLQuery(query, "getAllFlows");
  };

  // Get a Transform Flow - (if you want to grab a single flow, return type is TransformFlow)
  const getTransformFlowByName = (flowName: string) => {
    const query = {
      getTransformFlow: {
        __args: {
          flowName: flowName,
        },
        ...sourcePluginFields,
        ...transformFlowFields
      },
    };
    return sendGraphQLQuery(query, "getTransformFlowByName");
  };

  // Get a Normalize Flow - (if you want to grab a single flow, return type is NormalizeFlow)
  const getNormalizeFlowByName = (flowName: string) => {
    const query = {
      getNormalizeFlow: {
        __args: {
          flowName: flowName,
        },
        ...sourcePluginFields,
        ...normalizeFlowFields
      },
    };
    return sendGraphQLQuery(query, "getNormalizeFlowByName");
  };

  // Get an Enrich Flow - (if you want to grab a single flow, return type is EnrichFlow)
  const getEnrichFlowByName = (flowName: string) => {
    const query = {
      getEnrichFlow: {
        __args: {
          flowName: flowName,
        },
        ...sourcePluginFields,
        ...enrichFlowFields
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
        ...sourcePluginFields,
        ...egressFlowFields
      },
    };
    return sendGraphQLQuery(query, "getEgressFlowByName");
  };

  // Validate a transform flow - return type is TransformFlow
  const validateTransformFlow = (flowName: string) => {
    const query = {
      validateTransformFlow: {
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
          testMode: true,
        },
      },
    };
    return sendGraphQLQuery(query, "validateTransformFlow");
  };

  // Validate a normalize flow - return type is NormalizeFlow
  const validateNormalizeFlow = (flowName: string) => {
    const query = {
      validateNormalizeFlow: {
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
          testMode: true,
        },
      },
    };
    return sendGraphQLQuery(query, "validateNormalizeFlow");
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
          testMode: true,
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

  // Starts a transform flow
  const startTransformFlowByName = (flowName: string) => {
    const query = {
      startTransformFlow: {
        __args: {
          flowName: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "startTransformFlowByName", "mutation");
  };

  // Stops a Transform flow
  const stopTransformFlowByName = (flowName: string) => {
    const query = {
      stopTransformFlow: {
        __args: {
          flowName: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "stopTransformFlowByName", "mutation");
  };

  // Starts an normalize flow
  const startNormalizeFlowByName = (flowName: string) => {
    const query = {
      startNormalizeFlow: {
        __args: {
          flowName: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "startNormalizeFlowByName", "mutation");
  };

  // Stops an normalize flow
  const stopNormalizeFlowByName = (flowName: string) => {
    const query = {
      stopNormalizeFlow: {
        __args: {
          flowName: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "stopNormalizeFlowByName", "mutation");
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

  // sets a transform flow to test mode
  const enableTestTransformFlowByName = (flowName: string) => {
    const query = {
      enableTransformTestMode: {
        __args: {
          flowName: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "enableTransformTestModeFlowByName", "mutation");
  };

  // sets a transform flow to test mode
  const disableTestTransformFlowByName = (flowName: string) => {
    const query = {
      disableTransformTestMode: {
        __args: {
          flowName: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "disableTransformTestModeFlowByName", "mutation");
  };

  // sets an normalize flow to test mode
  const enableTestNormalizeFlowByName = (flowName: string) => {
    const query = {
      enableNormalizeTestMode: {
        __args: {
          flowName: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "enableNormalizeTestModeFlowByName", "mutation");
  };

  // sets an normalize flow to test mode
  const disableTestNormalizeFlowByName = (flowName: string) => {
    const query = {
      disableNormalizeTestMode: {
        __args: {
          flowName: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "disableNormalizeTestModeFlowByName", "mutation");
  };

  // sets an egress flow to test mode
  const enableTestEgressFlowByName = (flowName: string) => {
    const query = {
      enableEgressTestMode: {
        __args: {
          flowName: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "enableEgressTestModeFlowByName", "mutation");
  };

  // sets an egress flow to test mode
  const disableTestEgressFlowByName = (flowName: string) => {
    const query = {
      disableEgressTestMode: {
        __args: {
          flowName: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "disableEgressTestModeFlowByName", "mutation");
  };

  // sets max errors for a normalize or transform flow
  const setMaxErrors = (flowName: string, maxErrors: number) => {
    const query = {
      setMaxErrors: {
        __args: {
          flowName: flowName,
          maxErrors: maxErrors,
        },
      },
    };
    return sendGraphQLQuery(query, "setMaxErrors", "mutation");
  };

  // sets expected annotations for an transform flow
  const setTransformFlowExpectedAnnotations = (flowName: string, expectedAnnotations: Array<string>) => {
    const query = {
      setTransformFlowExpectedAnnotations: {
        __args: {
          flowName: flowName,
          expectedAnnotations: expectedAnnotations,
        },
      },
    };
    return sendGraphQLQuery(query, "setTransformFlowExpectedAnnotations", "mutation");
  };

  // sets expected annotations for an egress flow
  const setEgressFlowExpectedAnnotations = (flowName: string, expectedAnnotations: Array<string>) => {
    const query = {
      setEgressFlowExpectedAnnotations: {
        __args: {
          flowName: flowName,
          expectedAnnotations: expectedAnnotations,
        },
      },
    };
    return sendGraphQLQuery(query, "setEgressFlowExpectedAnnotations", "mutation");
  };

  const sendGraphQLQuery = async (query: any, operationName: string, queryType?: string) => {
    try {
      await queryGraphQL(query, operationName, queryType);
      return response.value;
    } catch (e: any) {
      return e.value;
      // Continue regardless of error
    }
  };

  return {
    getAllFlows,
    getTransformFlowByName,
    getNormalizeFlowByName,
    getEnrichFlowByName,
    getEgressFlowByName,
    validateTransformFlow,
    validateNormalizeFlow,
    validateEnrichFlow,
    validateEgressFlow,
    setPluginVariables,
    startTransformFlowByName,
    stopTransformFlowByName,
    startNormalizeFlowByName,
    stopNormalizeFlowByName,
    startEnrichFlowByName,
    stopEnrichFlowByName,
    startEgressFlowByName,
    stopEgressFlowByName,
    enableTestTransformFlowByName,
    disableTestTransformFlowByName,
    enableTestNormalizeFlowByName,
    disableTestNormalizeFlowByName,
    enableTestEgressFlowByName,
    disableTestEgressFlowByName,
    setMaxErrors,
    setTransformFlowExpectedAnnotations,
    setEgressFlowExpectedAnnotations,
    loaded,
    loading,
    errors,
  };
}
