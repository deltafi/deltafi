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

import useGraphQL from "./useGraphQL";
import { EnumType } from "json-to-graphql-query";
import { transformFlow, dataSink, flowStatusFields } from "./useFlowPlanQueryVariables";

export default function useFlowQueryBuilder() {
  const { response, errors, queryGraphQL, loaded, loading } = useGraphQL();

  // Get all flows (no grouping)
  const getAllFlows = () => {
    const query = {
      getAllFlows: {
        transform: {
          ...transformFlow,
        },
        dataSink: {
          ...dataSink,
        },
      },
    };
    return sendGraphQLQuery(query, "getAllFlows");
  };

  // Get a Transform - (if you want to grab a single flow, return type is TransformFlow)
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

  // Get an Egress Flow - (if you want to grab a single flow, return type is DataSink)
  const getDataSinkByName = (flowName: string) => {
    const query = {
      getDataSink: {
        __args: {
          flowName: flowName,
        },
        ...dataSink,
      },
    };
    return sendGraphQLQuery(query, "getDataSinkByName");
  };

  // Validate a transform - return type is TransformFlow
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

  // Validate an egress flow - return type is DataSink
  const validateDataSink = (flowName: string) => {
    const query = {
      validateDataSink: {
        __args: {
          flowName: flowName,
        },
        flowStatus: {
          ...flowStatusFields,
          testMode: true,
        },
      },
    };
    return sendGraphQLQuery(query, "validateDataSink");
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

  // Starts a transform
  const startTransformFlowByName = (flowName: string) => {
    const query = {
      setFlowState: {
        __args: {
          flowType: new EnumType('TRANSFORM'),
          flowName: flowName,
          flowState: new EnumType('RUNNING'),
        },
      },
    };
    return sendGraphQLQuery(query, "startTransformFlowByName", "mutation");
  };

  // Stops a Transform
  const stopTransformFlowByName = (flowName: string) => {
    const query = {
      setFlowState: {
        __args: {
          flowType: new EnumType('TRANSFORM'),
          flowName: flowName,
          flowState: new EnumType('STOPPED'),
        },
      },
    };
    return sendGraphQLQuery(query, "stopTransformFlowByName", "mutation");
  };

  // Starts an egress flow
  const startDataSinkByName = (flowName: string) => {
    const query = {
      startDataSink: {
        __args: {
          flowName: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "startDataSinkByName", "mutation");
  };

  // Stops an egress flow
  const stopDataSinkByName = (flowName: string) => {
    const query = {
      stopDataSink: {
        __args: {
          flowName: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "stopDataSinkByName", "mutation");
  };

  // sets a transform to test mode
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

  // sets a transform to test mode
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
  const enableTestDataSinkByName = (flowName: string) => {
    const query = {
      enableDataSinkTestMode: {
        __args: {
          flowName: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "enableDataSinkTestModeFlowByName", "mutation");
  };

  // sets an egress flow to test mode
  const disableTestDataSinkByName = (flowName: string) => {
    const query = {
      disableDataSinkTestMode: {
        __args: {
          flowName: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "disableDataSinkTestModeFlowByName", "mutation");
  };

  // sets expected annotations for an egress flow
  const setDataSinkExpectedAnnotations = (flowName: string, expectedAnnotations: Array<string>) => {
    const query = {
      setDataSinkExpectedAnnotations: {
        __args: {
          flowName: flowName,
          expectedAnnotations: expectedAnnotations,
        },
      },
    };
    return sendGraphQLQuery(query, "setDataSinkExpectedAnnotations", "mutation");
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
    getDataSinkByName,
    validateTransformFlow,
    validateDataSink,
    setPluginVariables,
    startTransformFlowByName,
    stopTransformFlowByName,
    startDataSinkByName,
    stopDataSinkByName,
    enableTestTransformFlowByName,
    disableTestTransformFlowByName,
    enableTestDataSinkByName,
    disableTestDataSinkByName,
    setDataSinkExpectedAnnotations,
    loaded,
    loading,
    errors,
  };
}
