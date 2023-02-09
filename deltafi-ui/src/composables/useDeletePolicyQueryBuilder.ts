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

export default function useDeletePolicyQueryBuilder() {
  const { response, errors, queryGraphQL,loading, loaded } = useGraphQL();

  // Get current delete policies
  const getDeletePolicies = () => {
    const query = {
      getDeletePolicies: {
        id: true,
        name: true,
        flow: true,
        __typename: true,
        enabled: true,
        __on: [
          {
            __typeName: "TimedDeletePolicy",
            afterCreate: true,
            afterComplete: true,
            minBytes: true,
            deleteMetadata: true,
          },
          {
            __typeName: "DiskSpaceDeletePolicy",
            maxPercent: true,
          },
        ],
      },
    };
    return sendGraphQLQuery(query, "getDeletePolicies");
  };

  // Enable a delete policy
  const enablePolicy = (policyId: string, enabledState: boolean) => {
    const query = {
      enablePolicy: {
        __args: {
          id: policyId,
          enabled: enabledState,
        },
      },
    };
    return sendGraphQLQuery(query, "enablePolicy", "mutation");
  };

  // Remove a delete policy
  const removeDeletePolicy = (policyId: string) => {
    const query = {
      removeDeletePolicy: {
        __args: {
          id: policyId,
        },
      },
    };
    return sendGraphQLQuery(query, "removeDeletePolicy", "mutation");
  };
  // Load delete policies
  const loadDeletePolicies = (policies: string) => {
    const query = {
      loadDeletePolicies: {
        __args: {
          replaceAll: false,
          policies: policies,
        },
        success: true,
        errors: true,
      },
    };
    return sendGraphQLQuery(query, "loadDeletePolicies", "mutation", true);
  };

  const sendGraphQLQuery = async (query: any, operationName: string, queryType?: string, bypass?: boolean) => {
    try {
      await queryGraphQL(query, operationName, queryType, bypass);
      return response.value;
    } catch (e: any) {
      return e.value;
      // Continue regardless of error
    }
  };

  return {
    getDeletePolicies,
    enablePolicy,
    removeDeletePolicy,
    loadDeletePolicies,
    loading,
    loaded,
  };
}
