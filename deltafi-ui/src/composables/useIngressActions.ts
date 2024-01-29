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

export default function useIngressActions() {
  const { response, errors, queryGraphQL, loaded, loading } = useGraphQL();

  const getAllTimedIngress = () => {
    const query = {
      getAllFlows: {
        timedIngress: {
          name: true,
          description: true,
          flowStatus: {
            state: true,
          },
          targetFlow: true,
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
          publishRules: {
            matchingPolicy: true,
            defaultRule: {
              defaultBehavior: true,
              topic: true,
            },
            rules: {
              topics: true,
              condition: true,
            },
          },
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

  // Starts a Timed Ingress flow
  const startTimedIngressFlowByName = (flowName: string) => {
    const query = {
      startTimedIngressFlow: {
        __args: {
          flowName: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "startTimedIngressFlowByName", "mutation");
  };

  // Stops a Timed Ingress flow
  const stopTimedIngressFlowByName = (flowName: string) => {
    const query = {
      stopTimedIngressFlow: {
        __args: {
          flowName: flowName,
        },
      },
    };
    return sendGraphQLQuery(query, "stopTimedIngressFlowByName", "mutation");
  };

  const setTimedIngressCronSchedule = (flowName: string, cronSchedule: string) => {
    const query = {
      setTimedIngressCronSchedule: {
        __args: {
          flowName: flowName,
          cronSchedule: cronSchedule,
        },
      },
    };
    return sendGraphQLQuery(query, "setTimedIngressCronSchedule", "mutation");
  };

  const saveTimedIngressFlowPlan = (timedIngressFlowPlan: Object) => {
    const query = {
      saveTimedIngressFlowPlan: {
        __args: {
          timedIngressFlowPlan: timedIngressFlowPlan,
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
    startTimedIngressFlowByName,
    stopTimedIngressFlowByName,
    setTimedIngressCronSchedule,
    saveTimedIngressFlowPlan,
    removeTimedIngressFlowPlan,
    loaded,
    loading,
    errors,
  };
}
