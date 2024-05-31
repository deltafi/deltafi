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
import { EnumType } from "json-to-graphql-query";
import _ from "lodash";

export default function useEgressActions() {
  const { response, errors, queryGraphQL, loaded, loading } = useGraphQL();

  const getAllEgress = () => {
    const query = {
      getAllFlows: {
        egress: {
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
    return sendGraphQLQuery(query, "getAllFlows");
  };

  // Starts a Egress flow
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

  // Stops a Egress flow
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

  const saveEgressFlowPlan = (egressFlowPlan: Object) => {
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

    graphqlQueryObjectConverter(egressFlowPlan);

    const formattedQuery = newObject;

    const query = {
      saveEgressFlowPlan: {
        __args: {
          egressFlowPlan: formattedQuery,
        },
        name: true,
      },
    };
    return sendGraphQLQuery(query, "saveEgressFlowPlan", "mutation");
  };

  const removeEgressFlowPlan = (flowName: string) => {
    const query = {
      removeEgressFlowPlan: {
        __args: {
          name: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "removeEgressFlowPlan", "mutation");
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
    getAllEgress,
    startEgressFlowByName,
    stopEgressFlowByName,
    saveEgressFlowPlan,
    removeEgressFlowPlan,
    loaded,
    loading,
    errors,
  };
}
