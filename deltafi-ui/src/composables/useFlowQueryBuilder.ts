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
import { transformFlow, egressFlow, flowStatusFields } from "./useFlowPlanQueryVariables";

export default function useFlowQueryBuilder() {
  const { response, errors, queryGraphQL, loaded, loading } = useGraphQL();

  // Get all flows (no grouping)
  const getAllFlows = () => {
    const query = {
      getAllFlows: {
        transform: {
          ...transformFlow,
        },
        egress: {
          ...egressFlow,
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
        ...transformFlow,
      },
    };
    return sendGraphQLQuery(query, "getTransformFlowByName");
  };

  // Get an Egress Flow - (if you want to grab a single flow, return type is EgressFlow)
  const getEgressFlowByName = (flowName: string) => {
    const query = {
      getEgressFlow: {
        __args: {
          flowName: flowName,
        },
        ...egressFlow,
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
          ...flowStatusFields,
          testMode: true,
        },
      },
    };
    return sendGraphQLQuery(query, "validateTransformFlow");
  };

  // Validate an egress flow - return type is EgressFlow
  const validateEgressFlow = (flowName: string) => {
    const query = {
      validateEgressFlow: {
        __args: {
          flowName: flowName,
        },
        flowStatus: {
          ...flowStatusFields,
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

  // sets max errors for a transform flow
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
    getEgressFlowByName,
    validateTransformFlow,
    validateEgressFlow,
    setPluginVariables,
    startTransformFlowByName,
    stopTransformFlowByName,
    startEgressFlowByName,
    stopEgressFlowByName,
    enableTestTransformFlowByName,
    disableTestTransformFlowByName,
    enableTestEgressFlowByName,
    disableTestEgressFlowByName,
    setMaxErrors,
    setEgressFlowExpectedAnnotations,
    loaded,
    loading,
    errors,
  };
}
