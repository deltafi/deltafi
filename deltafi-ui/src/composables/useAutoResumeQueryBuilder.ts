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
import { EnumType } from 'json-to-graphql-query';

export default function useAutoResumeQueryBuilder() {
  const { response, errors, queryGraphQL, loaded, loading } = useGraphQL();

  // Get all Auto Resume Rules
  const getAllResumePolicies = () => {
    const query = {
      getAllResumePolicies: {
        id: true,
        name: true,
        errorSubstring: true,
        dataSource: true,
        action: true,
        actionType: true,
        maxAttempts: true,
        priority: true,
        backOff: {
          delay: true,
          maxDelay: true,
          multiplier: true,
          random: true,
        },
      },
    };
    return sendGraphQLQuery(query, "getAllResumePolicies");
  };

  // Get Auto Resume Rules with name
  const getResumePolicy = (id: string) => {
    const query = {
      getResumePolicy: {
        __args: {
          id: id,
        },
        id: true,
        name: true,
        errorSubstring: true,
        flow: true,
        action: true,
        actionType: true,
        maxAttempts: true,
        priority: true,
        backOff: {
          delay: true,
          maxDelay: true,
          multiplier: true,
          random: true,
        },
      },
    };
    return sendGraphQLQuery(query, "getResumePolicy");
  };

  // Load Flow Assignment Rules
  const loadResumePolicies = (policies: any) => {
    const policiesWithEnums = policies.map((policy: any) => {
      return {
        ...policy,
        actionType: new EnumType(policy.actionType)
      }
    })
    const query = {
      loadResumePolicies: {
        __args: {
          replaceAll: false,
          policies: policiesWithEnums,
        },
        success: true,
        errors: true,
      },
    };
    return sendGraphQLQuery(query, "loadResumePolicies", "mutation");
  };

  // Remove Flow Assignment Rule
  const removeResumePolicy = (policyId: string) => {
    const query = {
      removeResumePolicy: {
        __args: {
          id: policyId,
        },
      },
    };
    return sendGraphQLQuery(query, "removeResumePolicy", "mutation");
  };

  // Remove Flow Assignment Rule
  const updateResumePolicy = (policy: any) => {
    const policyWithEnum = {
      ...policy,
      actionType: new EnumType(policy.actionType)
    }
    const query = {
      updateResumePolicy: {
        __args: {
          resumePolicy: policyWithEnum,
        },
        success: true,
        info: true,
        errors: true,
      },
    };
    return sendGraphQLQuery(query, "updateResumePolicy", "mutation");
  };

  const applyResumePolicies = (policy: Array<string>) => {
    const query = {
      applyResumePolicies: {
        __args: {
          names: policy,
        },
        success: true,
        info: true,
        errors: true,
      },
    };
    return sendGraphQLQuery(query, "applyResumePolicies", "mutation");
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
    getAllResumePolicies,
    getResumePolicy,
    loadResumePolicies,
    removeResumePolicy,
    updateResumePolicy,
    applyResumePolicies,
    loaded,
    loading,
    errors,
  };
}
