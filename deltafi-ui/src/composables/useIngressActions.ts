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
            state: true
          },
          targetFlow: true,
          interval: true,
          lastRun: true,
          memo: true,
          currentDid: true,
          executeImmediate: true,
          ingressStatus: true,
          ingressStatusMessage: true
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

  const sendGraphQLQuery = async (query: any, operationName: string, queryType?: string) => {
    try {
      await queryGraphQL(query, operationName, queryType);
      return response.value;
    } catch (e: any) {
      return e.value;
      // Continue regardless of error
    }
  };

  const setTimedIngressInterval = (flowName: string, interval: string) => {
    const query = {
      setTimedIngressInterval: {
        __args: {
          flowName: flowName,
          interval: interval,
        },
      },
    };
    return sendGraphQLQuery(query, "setTimedIngressInterval", "mutation");
  };

  return {
    getAllTimedIngress,
    startTimedIngressFlowByName,
    stopTimedIngressFlowByName,
    setTimedIngressInterval,
    loaded,
    loading,
    errors,
  };
}
