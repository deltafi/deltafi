/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>

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
import { jsonToGraphQLQuery } from "json-to-graphql-query";
import { transformFlow, transformFlowPlan, dataSink, dataSinkPlan } from "./useFlowPlanQueryVariables";

// Render a json-to-graphql-query field tree as a bare GraphQL selection set string.
// Wraps in a sentinel root, serializes, then strips the wrapper.
const renderSelectionSet = (fields: Record<string, any>): string => {
  const wrapped = jsonToGraphQLQuery({ _root: fields }, { pretty: false });
  return wrapped.slice(wrapped.indexOf("{") + 1, wrapped.lastIndexOf("}")).trim();
};

export default function useFlowQueryBuilder() {
  const { response, queryGraphQL, sendGraphQLQueryWithVariables } = useGraphQL();

  // Get all flows (no grouping)
  const getAllFlowPlans = () => {
    const query = {
      getAllFlowPlans: {
        transformPlans: {
          ...transformFlowPlan,
        },
        dataSinkPlans: {
          ...dataSinkPlan,
        },
      },
    };
    return sendGraphQLQuery(query, "getAllFlows");
  };

  // Save a TransformFlowPlan
  const saveTransformFlowPlan = (flowPlan: Object) => {
    const mutation = `
      mutation saveTransformFlowPlan($transformFlowPlan: TransformFlowPlanInput!) {
        saveTransformFlowPlan(transformFlowPlan: $transformFlowPlan) { ${renderSelectionSet(transformFlow)} }
      }
    `;
    return sendGraphQLQueryWithVariables(mutation, { transformFlowPlan: flowPlan }, "saveTransformFlowPlan");
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

  // Save a DataSinkPlan
  const saveDataSinkPlan = (flowPlan: Object) => {
    const mutation = `
      mutation saveDataSinkPlan($dataSinkPlan: DataSinkPlanInput!) {
        saveDataSinkPlan(dataSinkPlan: $dataSinkPlan) { ${renderSelectionSet(dataSink)} }
      }
    `;
    return sendGraphQLQueryWithVariables(mutation, { dataSinkPlan: flowPlan }, "saveDataSinkPlan");
  };

  // Remove a DataSinkPlan
  const removeDataSinkPlanByName = (flowPlanName: string) => {
    const query = {
      removeDataSinkPlan: {
        __args: {
          name: flowPlanName,
        },
      },
    };
    return sendGraphQLQuery(query, "removeDataSinkPlanByName", "mutation");
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
    saveDataSinkPlan,
    removeDataSinkPlanByName,
  };
}
