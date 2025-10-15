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

export default function useDataSource() {
  const { response, errors, queryGraphQL, loaded, loading } = useGraphQL();

  const defaultDataSourceFields = {
    name: true,
    type: true,
    description: true,
    maxErrors: true,
    flowStatus: {
      state: true,
      valid: true,
      errors: {
        configName: true,
        errorType: true,
        message: true,
      },
      testMode: true,
    },
    sourcePlugin: {
      groupId: true,
      artifactId: true,
      version: true,
    },
    annotationConfig: {
      annotations: true,
      metadataPatterns: true,
      discardPrefix: true,
    },
    metadata: true,
    __typename: true,
    topic: true,
  };

  const onErrorDataSourceFields = {
    errorMessageRegex: true,
    sourceMetadataPrefix: true,
    includeSourceMetadataRegex: true,
  };

  const timeDataSourceFields = {
    cronSchedule: true,
    lastRun: true,
    nextRun: true,
    memo: true,
    currentDid: true,
    executeImmediate: true,
    ingressStatus: true,
    ingressStatusMessage: true,
    timedIngressAction: {
      name: true,
      apiVersion: true,
      actionType: true,
      type: true,
      parameters: true,
    },
    variables: {
      name: true,
      description: true,
      dataType: true,
      required: true,
      defaultValue: true,
      value: true,
      masked: true,
    },
  };

  const restDataSourceFields = {
    rateLimit: {
      unit: true,
      maxAmount: true,
      durationSeconds: true,
    },
  };

  const getAllDataSources = () => {
    const query = {
      getAllFlows: {
        restDataSource: {
          ...defaultDataSourceFields,
          ...restDataSourceFields,
        },
        timedDataSource: {
          ...defaultDataSourceFields,
          ...timeDataSourceFields,
        },
        onErrorDataSource: {
          ...defaultDataSourceFields,
          ...onErrorDataSourceFields,
        },
        dataSink: {
          ...defaultDataSourceFields,
        },
      },
    };
    return sendGraphQLQuery(query, "getAllDataSources");
  };

  const getRestDataSources = () => {
    const query = {
      getAllFlows: {
        restDataSource: {
          ...defaultDataSourceFields,
          ...restDataSourceFields,
        },
      },
    };
    return sendGraphQLQuery(query, "getRestDataSources");
  };

  const getOnErrorDataSources = () => {
    const query = {
      getAllFlows: {
        onErrorDataSource: {
          ...defaultDataSourceFields,
          ...onErrorDataSourceFields,
        },
      },
    };
    return sendGraphQLQuery(query, "getOnErrorDataSources");
  };

  const getTimedDataSources = () => {
    const query = {
      getAllFlows: {
        timedDataSource: {
          ...defaultDataSourceFields,
          ...timeDataSourceFields,
        },
      },
    };
    return sendGraphQLQuery(query, "getTimedDataSources");
  };

  // Starts a Rest Data Source by name
  const startRestDataSourceByName = (name: string) => {
    const query = {
      setFlowState: {
        __args: {
          flowType: new EnumType("REST_DATA_SOURCE"),
          flowName: name,
          flowState: new EnumType("RUNNING"),
        },
      },
    };
    return sendGraphQLQuery(query, "startRestDataSourceByName", "mutation");
  };

  // Starts a Timed Data Source by name
  const startTimedDataSourceByName = (name: string) => {
    const query = {
      setFlowState: {
        __args: {
          flowType: new EnumType("TIMED_DATA_SOURCE"),
          flowName: name,
          flowState: new EnumType("RUNNING"),
        },
      },
    };
    return sendGraphQLQuery(query, "startTimedDataSourceByName", "mutation");
  };

  // Stops a Rest Data Source by name
  const stopRestDataSourceByName = (name: string) => {
    const query = {
      setFlowState: {
        __args: {
          flowType: new EnumType("REST_DATA_SOURCE"),
          flowName: name,
          flowState: new EnumType("STOPPED"),
        },
      },
    };
    return sendGraphQLQuery(query, "stopRestDataSourceByName", "mutation");
  };

  // Stops a Timed Data Source by name
  const stopTimedDataSourceByName = (name: string) => {
    const query = {
      setFlowState: {
        __args: {
          flowType: new EnumType("TIMED_DATA_SOURCE"),
          flowName: name,
          flowState: new EnumType("STOPPED"),
        },
      },
    };
    return sendGraphQLQuery(query, "stopTimedDataSourceByName", "mutation");
  };

  const setTimedDataSourceCronSchedule = (name: string, cronSchedule: string) => {
    const query = {
      setTimedDataSourceCronSchedule: {
        __args: {
          name: name,
          cronSchedule: cronSchedule,
        },
      },
    };
    return sendGraphQLQuery(query, "setTimedDataSourceCronSchedule", "mutation");
  };

  const saveTimedDataSourcePlan = (timedDataSourceFlowPlan: Object) => {
    const query = {
      saveTimedDataSourcePlan: {
        __args: {
          dataSourcePlan: {
            type: "TIMED_DATA_SOURCE",
            ...timedDataSourceFlowPlan,
          },
        },
        name: true,
      },
    };
    return sendGraphQLQuery(query, "saveTimedDataSourcePlan", "mutation");
  };

  const saveRestDataSourcePlan = (restDataSourceFlowPlan: Object) => {
    const query = {
      saveRestDataSourcePlan: {
        __args: {
          dataSourcePlan: {
            type: "REST_DATA_SOURCE",
            ...restDataSourceFlowPlan,
          },
        },
        name: true,
      },
    };
    return sendGraphQLQuery(query, "saveRestDataSourcePlan", "mutation");
  };

  const saveOnErrorDataSourcePlan = (onErrorDataSourceFlowPlan: Object) => {
    const query = {
      saveOnErrorDataSourcePlan: {
        __args: {
          dataSourcePlan: {
            type: "ON_ERROR_DATA_SOURCE",
            ...onErrorDataSourceFlowPlan,
          },
        },
        name: true,
      },
    };
    return sendGraphQLQuery(query, "saveOnErrorDataSourcePlan", "mutation");
  };

  const removeDataSourcePlan = (name: string, dataSourceType: string) => {
    let query = {};
    if (dataSourceType === "REST_DATA_SOURCE") {
      query = {
        removeRestDataSourcePlan: {
          __args: {
            name: name,
          },
        },
      };
    } else if (dataSourceType === "TIMED_DATA_SOURCE") {
      query = {
        removeTimedDataSourcePlan: {
          __args: {
            name: name,
          },
        },
      };
    } else if (dataSourceType === "ON_ERROR_DATA_SOURCE") {
      query = {
        removeOnErrorDataSourcePlan: {
          __args: {
            name: name,
          },
        },
      };
    } else {
      throw new Error(`Unsupported data source type: ${dataSourceType}`);
    }
    return sendGraphQLQuery(query, "removeDataSourcePlan", "mutation");
  };

  // Sets a Rest Data Source flow to test mode
  const enableTestRestDataSourceFlowByName = (flowName: string) => {
    const query = {
      enableRestDataSourceTestMode: {
        __args: {
          name: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "enableTestRestDataSourceFlowByName", "mutation");
  };

  // Sets a Rest Data Source flow to test mode
  const disableTestRestDataSourceFlowByName = (flowName: string) => {
    const query = {
      disableRestDataSourceTestMode: {
        __args: {
          name: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "disableTestRestDataSourceFlowByName", "mutation");
  };

  const disableTestOnErrorDataSourceFlowByName = (flowName: string) => {
    const query = {
      disableOnErrorDataSourceTestMode: {
        __args: {
          name: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "disableTestOnErrorDataSourceFlowByName", "mutation");
  };

  // Sets a Time Data Source flow to test mode
  const enableTestTimedDataSourceFlowByName = (flowName: string) => {
    const query = {
      enableTimedDataSourceTestMode: {
        __args: {
          name: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "enableTestTimedDataSourceFlowByName", "mutation");
  };

  const enableTestOnErrorDataSourceFlowByName = (flowName: string) => {
    const query = {
      enableOnErrorDataSourceTestMode: {
        __args: {
          name: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "enableTestOnErrorDataSourceFlowByName", "mutation");
  };

  // Sets a Time Data Source flow to test mode
  const disableTestTimedDataSourceFlowByName = (flowName: string) => {
    const query = {
      disableTimedDataSourceTestMode: {
        __args: {
          name: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "disableTestTimedDataSourceFlowByName", "mutation");
  };

  // sets max errors for a DataSource
  const setRestDataSourceMaxErrors = (name: string, maxErrors: number) => {
    const query = {
      setRestDataSourceMaxErrors: {
        __args: {
          name: name,
          maxErrors: maxErrors,
        },
      },
    };
    return sendGraphQLQuery(query, "setRestDataSourceMaxErrors", "mutation");
  };

  const setOnErrorDataSourceMaxErrors = (name: string, maxErrors: number) => {
    const query = {
      setOnErrorDataSourceMaxErrors: {
        __args: {
          name: name,
          maxErrors: maxErrors,
        },
      },
    };
    return sendGraphQLQuery(query, "setOnErrorDataSourceMaxErrors", "mutation");
  };

  // sets max errors for a DataSource
  const setTimedDataSourceMaxErrors = (name: string, maxErrors: number) => {
    const query = {
      setTimedDataSourceMaxErrors: {
        __args: {
          name: name,
          maxErrors: maxErrors,
        },
      },
    };
    return sendGraphQLQuery(query, "setTimedDataSourceMaxErrors", "mutation");
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
    getAllDataSources,
    getRestDataSources,
    getTimedDataSources,
    getOnErrorDataSources,
    startRestDataSourceByName,
    startTimedDataSourceByName,
    stopRestDataSourceByName,
    stopTimedDataSourceByName,
    setTimedDataSourceCronSchedule,
    saveTimedDataSourcePlan,
    saveRestDataSourcePlan,
    saveOnErrorDataSourcePlan,
    enableTestRestDataSourceFlowByName,
    disableTestRestDataSourceFlowByName,
    enableTestTimedDataSourceFlowByName,
    disableTestTimedDataSourceFlowByName,
    enableTestOnErrorDataSourceFlowByName,
    disableTestOnErrorDataSourceFlowByName,
    removeDataSourcePlan,
    setRestDataSourceMaxErrors,
    setTimedDataSourceMaxErrors,
    setOnErrorDataSourceMaxErrors,
    loaded,
    loading,
    errors,
  };
}
