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
import { transformFlow, transformFlowPlan, normalizeFlow, normalizeFlowPlan, enrichFlow, enrichFlowPlan, egressFlow, egressFlowPlan } from "./useFlowPlanQueryVariables";

export default function useFlowQueryBuilder() {
  const { response, queryGraphQL } = useGraphQL();

  // Get all flows (no grouping)
  const getAllFlowPlans = () => {
    const query = {
      getAllFlowPlans: {
        transformPlans: {
          ...transformFlowPlan,
        },
        normalizePlans: {
          ...normalizeFlowPlan,
        },
        enrichPlans: {
          ...enrichFlowPlan,
        },
        egressPlans: {
          ...egressFlowPlan,
        },
      },
    };
    return sendGraphQLQuery(query, "getAllFlows");
  };

  // Save a TransformFlowPlan
  const saveTransformFlowPlan = (flowPlan: Object) => {
    const query = {
      saveTransformFlowPlan: {
        __args: {
          transformFlowPlan: flowPlan,
        },
        ...transformFlow,
      },
    };
    return sendGraphQLQuery(query, "saveTransformFlowPlan", "mutation", true);
  };

  // Remove an TransformFlowPlan
  const removeTransformFlowPlanByName = (flowPlanName: string) => {
    const query = {
      removeTransformFlowPlan: {
        __args: {
          name: flowPlanName,
        },
      },
    };
    return sendGraphQLQuery(query, "removeTransformFlowPlanByName", "mutation");
  };

  // Save an NormalizeFlowPlan
  const saveNormalizeFlowPlan = (flowPlan: Object) => {
    const query = {
      saveNormalizeFlowPlan: {
        __args: {
          normalizeFlowPlan: flowPlan,
        },
        ...normalizeFlow,
      },
    };
    return sendGraphQLQuery(query, "saveNormalizeFlowPlan", "mutation", true);
  };

  // Remove an NormalizeFlowPlan
  const removeNormalizeFlowPlanByName = (flowPlanName: string) => {
    const query = {
      removeNormalizeFlowPlan: {
        __args: {
          name: flowPlanName,
        },
      },
    };
    return sendGraphQLQuery(query, "removeNormalizeFlowPlanByName", "mutation");
  };

  // Save a EnrichFlowPlan
  const saveEnrichFlowPlan = (flowPlan: Object) => {
    const query = {
      saveEnrichFlowPlan: {
        __args: {
          enrichFlowPlan: flowPlan,
        },
        ...enrichFlow,
      },
    };
    return sendGraphQLQuery(query, "saveEnrichFlowPlan", "mutation", true);
  };

  // Remove an EnrichFlowPlan
  const removeEnrichFlowPlan = (flowPlanName: string) => {
    const query = {
      removeEnrichFlowPlan: {
        __args: {
          name: flowPlanName,
        },
      },
    };
    return sendGraphQLQuery(query, "removeEnrichFlowPlanByName", "mutation");
  };

  // Save an EgressFlowPlan
  const saveEgressFlowPlan = (flowPlan: Object) => {
    const query = {
      saveEgressFlowPlan: {
        __args: {
          egressFlowPlan: flowPlan,
        },
        ...egressFlow,
      },
    };
    return sendGraphQLQuery(query, "saveEgressFlowPlan", "mutation", true);
  };

  // Remove an EgressFlowPlan
  const removeEgressFlowPlanByName = (flowPlanName: string) => {
    const query = {
      removeEgressFlowPlan: {
        __args: {
          name: flowPlanName,
        },
      },
    };
    return sendGraphQLQuery(query, "removeEgressFlowPlanByName", "mutation");
  };

  const sendGraphQLQuery = async (query: any, operationName: string, queryType?: string, bypass?: boolean) => {
    try {
      await queryGraphQL(query, operationName, queryType, bypass);
      return response.value;
    } catch {
      // Continue regardless of error
    }
  };

  return {
    getAllFlowPlans,
    saveTransformFlowPlan,
    removeTransformFlowPlanByName,
    saveNormalizeFlowPlan,
    removeNormalizeFlowPlanByName,
    saveEgressFlowPlan,
    removeEgressFlowPlanByName,
    saveEnrichFlowPlan,
    removeEnrichFlowPlan,
  };
}
