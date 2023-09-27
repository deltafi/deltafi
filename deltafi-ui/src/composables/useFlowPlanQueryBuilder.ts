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

import { ref } from "vue";
import useGraphQL from './useGraphQL'

export default function useFlowQueryBuilder() {
  const { response, queryGraphQL } = useGraphQL();
  const data = ref(null);

  // Save a TransformFlowPlan
  const saveTransformFlowPlan = async (flowPlan: Object) => {
    const query = {
      saveTransformFlowPlan: {
        __args: {
          transformFlowPlan: flowPlan
        },
        name: true
      }
    };
   await sendGraphQLQuery(query, "saveTransformFlowPlan", "mutation");
   data.value = response.value.data.saveTransformFlowPlan;
  };

  // Remove an TransformFlowPlan
  const removeTransformFlowPlanByName = async (flowPlanName: string) => {
    const query = {
      removeTransformFlowPlan: {
        __args: {
          name: flowPlanName
        }
      }
    };
     await sendGraphQLQuery(query, "removeTransformFlowPlanByName", "mutation");
     data.value = response.value.data.removeTransformFlowPlan;
  };

  // Save an NormalizeFlowPlan
  const saveNormalizeFlowPlan = async (flowPlan: Object) => {
    const query = {
      saveNormalizeFlowPlan: {
        __args: {
          normalizeFlowPlan: flowPlan
        },
        name: true
      }
    };
    await sendGraphQLQuery(query, "saveNormalizeFlowPlan", "mutation");
    data.value = response.value.data.saveNormalizeFlowPlan;
  };

  // Remove an NormalizeFlowPlan
  const removeNormalizeFlowPlanByName = async (flowPlanName: string) => {
    const query = {
      removeNormalizeFlowPlan: {
        __args: {
          name: flowPlanName
        }
      }
    };
    await sendGraphQLQuery(query, "removeNormalizeFlowPlanByName", "mutation");
    data.value = response.value.data.removeNormalizeFlowPlan;
  };

   // Save a EnrichFlowPlan
   const saveEnrichFlowPlan = async (flowPlan: Object) => {
    const query = {
      saveEnrichFlowPlan: {
        __args: {
          enrichFlowPlan: flowPlan
        },
        name: true
      }
    };
   await sendGraphQLQuery(query, "saveEnrichFlowPlan", "mutation",true);
   data.value = response.value.data.saveEnrichFlowPlan;
  };

  // Remove an TransformFlowPlan
  const removeEnrichFlowPlan = async (flowPlanName: string) => {
    const query = {
      removeEnrichFlowPlan: {
        __args: {
          name: flowPlanName
        }
      }
    };
    await sendGraphQLQuery(query, "removeEnrichFlowPlanByName", "mutation");
    data.value = response.value.data.removeEnrichFlowPlan;
  };
  // Save an EgressFlowPlan
  const saveEgressFlowPlan = async (flowPlan: Object) => {
    const query = {
      saveEgressFlowPlan: {
        __args: {
          egressFlowPlan: flowPlan
        },
        name: true
      }
    };
    await sendGraphQLQuery(query, "saveEgressFlowPlan", "mutation",true);
    data.value = response.value.data.saveEgressFlowPlan;
  };

  // Remove an EgressFlowPlan
  const removeEgressFlowPlanByName = async (flowPlanName: string) => {
    const query = {
      removeEgressFlowPlan: {
        __args: {
          name: flowPlanName
        }
      }
    };
    await sendGraphQLQuery(query, "removeEgressFlowPlanByName", "mutation");
    data.value = response.value.data.removeEgressFlowPlan;
  };

  const sendGraphQLQuery = async (query: any, operationName: string, queryType?: string, bypass?: boolean) => {
    try {
      await queryGraphQL(query, operationName, queryType,bypass);
      return response.value;
    } catch {
      // Continue regardless of error
    }
  }

  return {
    saveTransformFlowPlan,
    removeTransformFlowPlanByName,
    saveNormalizeFlowPlan,
    removeNormalizeFlowPlanByName,
    saveEgressFlowPlan,
    removeEgressFlowPlanByName,
    saveEnrichFlowPlan,
    removeEnrichFlowPlan,
    data
  };
}
