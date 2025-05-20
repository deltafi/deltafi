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
import _ from "lodash";

export default function useDataSink() {
  const { response, errors, queryGraphQL, loaded, loading } = useGraphQL();

  const getAllDataSinks = () => {
    const query = {
      getAllFlows: {
        dataSink: {
          name: true,
          description: true,
          sourcePlugin: {
            groupId: true,
            artifactId: true,
            version: true,
          },
          flowStatus: {
            state: true,
            testMode: true,
            errors: {
              configName: true,
              errorType: true,
              message: true,
            },
          },
          expectedAnnotations: true,
          subscribe: {
            condition: true,
            topic: true,
          },
          egressAction: {
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
        },
      },
    };
    return sendGraphQLQuery(query, "getAllDataSinks");
  };

  // Starts a data sink
  const startDataSinkByName = (flowName: string) => {
    const query = {
      setFlowState: {
        __args: {
          flowType: new EnumType("DATA_SINK"),
          flowName: flowName,
          flowState: new EnumType("RUNNING"),
        },
      },
    };
    return sendGraphQLQuery(query, "startDataSinkByName", "mutation");
  };

  // Pause a data sink
  const pauseDataSinkByName = (flowName: string) => {
    const query = {
      setFlowState: {
        __args: {
          flowType: new EnumType("DATA_SINK"),
          flowName: flowName,
          flowState: new EnumType("PAUSED"),
        },
      },
    };
    return sendGraphQLQuery(query, "pauseDataSinkByName", "mutation");
  };

  // Stops a data sink
  const stopDataSinkByName = (flowName: string) => {
    const query = {
      setFlowState: {
        __args: {
          flowType: new EnumType("DATA_SINK"),
          flowName: flowName,
          flowState: new EnumType("STOPPED"),
        },
      },
    };
    return sendGraphQLQuery(query, "stopDataSinkByName", "mutation");
  };

  // Enable test mode for egress
  const enableDataSinkTestModeByName = (flowName: string) => {
    const query = {
      enableDataSinkTestMode: {
        __args: {
          flowName: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "enableDataSinkTestModeByName", "mutation");
  };

  // Disable test mode for egress
  const disableDataSinkTestModeByName = (flowName: string) => {
    const query = {
      disableDataSinkTestMode: {
        __args: {
          flowName: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "disableDataSinkTestModeByName", "mutation");
  };

  const saveDataSinkPlan = (dataSinkPlan: Object) => {
    let newObject: any = null;
    const enumKeysToKey = ["matchingPolicy", "defaultBehavior"];
    // Function to convert certain keys values to enums
    function graphqlQueryObjectConverter(queryObject: any) {
      for (const [key, value] of Object.entries(queryObject)) {
        if (_.isArray(value)) {
          continue;
        }

        if (_.isObject(value)) {
          graphqlQueryObjectConverter(value);
        }

        if (enumKeysToKey.includes(key)) {
          queryObject[key] = new EnumType(value as any);
        } else {
          continue;
        }
      }
      newObject = queryObject;
    }

    graphqlQueryObjectConverter(dataSinkPlan);

    const formattedQuery = newObject;

    const query = {
      saveDataSinkPlan: {
        __args: {
          dataSinkPlan: formattedQuery,
        },
        name: true,
      },
    };
    return sendGraphQLQuery(query, "saveDataSinkPlan", "mutation");
  };

  const removeDataSinkPlan = (flowName: string) => {
    const query = {
      removeDataSinkPlan: {
        __args: {
          name: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "removeDataSinkPlan", "mutation");
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
    getAllDataSinks,
    startDataSinkByName,
    pauseDataSinkByName,
    stopDataSinkByName,
    saveDataSinkPlan,
    removeDataSinkPlan,
    enableDataSinkTestModeByName,
    disableDataSinkTestModeByName,
    loaded,
    loading,
    errors,
  };
}
