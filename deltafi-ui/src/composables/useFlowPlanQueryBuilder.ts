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

import useGraphQL from './useGraphQL'

export default function useFlowQueryBuilder() {
  const { response, queryGraphQL } = useGraphQL();

  // Save an IngressFlowPlan
  const saveIngressFlowPlan = (flowPlan: Object) => {
    const query = {
      saveIngressFlowPlan: {
        __args: {
          ingressFlowPlan: flowPlan
        }
      }
    };
    return sendGraphQLQuery(query, "saveIngressFlowPlan", "mutation");
  };

  // Remove an IngressFlowPlan
  const removeIngressFlowPlanByName = (flowPlanName: string) => {
    const query = {
      removeIngressFlowPlan: {
        __args: {
          name: flowPlanName
        }
      }
    };
    return sendGraphQLQuery(query, "removeIngressFlowPlanByName", "mutation");
  };

  // Save an EgressFlowPlan
  const saveEgressFlowPlan = (flowPlan: Object) => {
    const query = {
      saveEgressFlowPlan: {
        __args: {
          egressFlowPlan: flowPlan
        }
      }
    };
    return sendGraphQLQuery(query, "saveEgressFlowPlan", "mutation");
  };

  // Remove an EgressFlowPlan
  const removeEgressFlowPlanByName = (flowPlanName: string) => {
    const query = {
      removeEgressFlowPlan: {
        __args: {
          name: flowPlanName
        }
      }
    };
    return sendGraphQLQuery(query, "removeEgressFlowPlanByName", "mutation");
  };

  const sendGraphQLQuery = async (query: any, operationName: string, queryType?: string) => {
    try {
      await queryGraphQL(query, operationName, queryType);
      return response.value;
    } catch {
      // Continue regardless of error
    }
  }

  return {
    saveIngressFlowPlan,
    removeIngressFlowPlanByName,
    saveEgressFlowPlan,
    removeEgressFlowPlanByName
  };
}
