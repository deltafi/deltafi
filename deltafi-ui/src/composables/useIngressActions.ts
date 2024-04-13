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

export default function useIngressActions() {
  const { response, errors, queryGraphQL, loaded, loading } = useGraphQL();

  const getAllTimedIngress = () => {
    const query = {
      getAllFlows: {
        dataSource: {
          name: true,
          type: true,
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
          __typename: true,
          __on: [
            {
              __typeName: "TimedDataSource",
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
              }
            }
          ],
          topic: true,
          sourcePlugin: {
            groupId: true,
            artifactId: true,
            version: true,
          },
        },
      },
    };
    return sendGraphQLQuery(query, "getAllTimedIngress");
  };

  // Starts a DataSource
  const startDataSourceByName = (name: string) => {
    const query = {
      startDataSource: {
        __args: {
          name: name,
        },
      },
    };
    return sendGraphQLQuery(query, "startDataSourceFlowByName", "mutation");
  };

  // Stops a DataSource
  const stopDataSourceByName = (name: string) => {
    const query = {
      stopDataSource: {
        __args: {
          name: name,
        },
      },
    };
    return sendGraphQLQuery(query, "stopDataSourceByName", "mutation");
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

  const saveTimedIngressFlowPlan = (timedIngressFlowPlan: Object) => {
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
        }
      }
      newObject = queryObject;
    }

    graphqlQueryObjectConverter(timedIngressFlowPlan);

    const formattedQuery = newObject;

    const query = {
      saveTimedIngressFlowPlan: {
        __args: {
          timedIngressFlowPlan: formattedQuery,
        },
        name: true,
      },
    };
    return sendGraphQLQuery(query, "saveTimedIngressFlowPlan", "mutation");
  };

  const removeTimedIngressFlowPlan = (flowName: string) => {
    const query = {
      removeTimedIngressFlowPlan: {
        __args: {
          name: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "removeTimedIngressFlowPlan", "mutation");
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
    getAllTimedIngress,
    startTimedIngressFlowByName: startDataSourceByName,
    stopTimedIngressFlowByName: stopDataSourceByName,
    setTimedIngressCronSchedule: setTimedDataSourceCronSchedule,
    saveTimedIngressFlowPlan,
    removeTimedIngressFlowPlan,
    loaded,
    loading,
    errors,
  };
}
