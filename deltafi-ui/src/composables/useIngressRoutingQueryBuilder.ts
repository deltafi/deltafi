/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>

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

export default function useIngressRoutingQueryBuilder() {
  const { response, queryGraphQL } = useGraphQL();

  // Resolves Flow from Flow Assignment Rules
  const resolveFlowFromFlowAssignmentRules = (fileName: string, flowName: string, metadata: object) => {
    const query = {
      resolveFlowFromFlowAssignmentRules: {
        __args: {
          sourceInfo: {
            filename: fileName,
            flow: flowName,
            metadata: metadata,
          },
        },
      },
    };
    return sendGraphQLQuery(query, "resolveFlowFromFlowAssignmentRules");
  };

  // Enable a delete policy
  const getAllFlowAssignmentRules = () => {
    const query = {
      getAllFlowAssignmentRules: {
        id: true,
        name: true,
        flow: true,
        priority: true,
        filenameRegex: true,
        requiredMetadata: {
          key: true,
          value: true,
        },
      },
    };
    return sendGraphQLQuery(query, "getAllFlowAssignmentRules");
  };

  // Get current Flow Assignment Rule
  const getFlowAssignmentRule = (flowName: string) => {
    const query = {
      getFlowAssignmentRule: {
        __args: {
          name: flowName,
        },
        id: true,
        name: true,
        flow: true,
        priority: true,
        filenameRegex: true,
        requiredMetadata: {
          key: true,
          value: true,
        },
      },
    };
    return sendGraphQLQuery(query, "getFlowAssignmentRule");
  };

  // Load Flow Assignment Rules
  const loadFlowAssignmentRules = (rules: string) => {
    const query = {
      loadFlowAssignmentRules: {
        __args: {
          replaceAll: false,
          rules: rules,
        },
        success: true,
        errors: true,
      },
    };
    return sendGraphQLQuery(query, "loadFlowAssignmentRules", "mutation");
  };

  // Remove Flow Assignment Rule
  const removeFlowAssignmentRule = (ruleId: string) => {
    const query = {
      removeFlowAssignmentRule: {
        __args: {
          id: ruleId,
        },
      },
    };
    return sendGraphQLQuery(query, "removeFlowAssignmentRule", "mutation");
  };

  const sendGraphQLQuery = async (query: any, operationName: string, queryType?: string, bypass?: boolean) => {
    try {
      await queryGraphQL(query, operationName, queryType, bypass);
      return response.value;
    } catch (e: any) {
      return e.value;
    }
  };

  return {
    resolveFlowFromFlowAssignmentRules,
    getAllFlowAssignmentRules,
    getFlowAssignmentRule,
    loadFlowAssignmentRules,
    removeFlowAssignmentRule,
  };
}
