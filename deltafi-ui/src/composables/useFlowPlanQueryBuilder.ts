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

export default function useFlowQueryBuilder() {
  const { response, queryGraphQL } = useGraphQL();

  const sourcePluginFields = {
    sourcePlugin: {
      artifactId: true,
      groupId: true,
      version: true,
    },
  };

  const variableFields = {
    variables: {
      name: true,
      value: true,
      description: true,
      defaultValue: true,
      dataType: true,
    },
  };

  const transformFlowFields = {
    name: true,
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
    maxErrors: true,
    transformActions: {
      name: true,
      type: true,
      parameters: true,
      apiVersion: true,
    },
    egressAction: {
      name: true,
      type: true,
      parameters: true,
      apiVersion: true,
    },
    ...variableFields,
    expectedAnnotations: true,
  };

  const normalizeFlowFields = {
    name: true,
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
    maxErrors: true,
    transformActions: {
      name: true,
      type: true,
      parameters: true,
      apiVersion: true,
    },
    loadAction: {
      name: true,
      type: true,
      parameters: true,
      apiVersion: true,
    },
    ...variableFields,
  };

  const enrichFlowFields = {
    name: true,
    description: true,
    flowStatus: {
      state: true,
      errors: {
        configName: true,
        message: true,
        errorType: true,
      },
    },
    domainActions: {
      name: true,
      type: true,
      requiresDomains: true,
      parameters: true,
      apiVersion: true,
    },
    enrichActions: {
      name: true,
      type: true,
      requiresDomains: true,
      requiresEnrichments: true,
      requiresMetadataKeyValues: {
        key: true,
        value: true,
      },
      parameters: true,
      apiVersion: true,
    },
    ...variableFields,
  };

  const egressFlowFields = {
    name: true,
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
    includeNormalizeFlows: true,
    excludeNormalizeFlows: true,
    formatAction: {
      name: true,
      type: true,
      requiresDomains: true,
      requiresEnrichments: true,
      parameters: true,
      apiVersion: true,
    },
    validateActions: {
      name: true,
      type: true,
      parameters: true,
      apiVersion: true,
    },
    egressAction: {
      name: true,
      type: true,
      parameters: true,
      apiVersion: true,
    },
    ...variableFields,
    expectedAnnotations: true,
  };

  // Save a TransformFlowPlan
  const saveTransformFlowPlan = (flowPlan: Object) => {
    const query = {
      saveTransformFlowPlan: {
        __args: {
          transformFlowPlan: flowPlan,
        },
        ...sourcePluginFields,
        ...transformFlowFields,
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
        ...sourcePluginFields,
        ...normalizeFlowFields,
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
        ...sourcePluginFields,
        ...enrichFlowFields,
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
        ...sourcePluginFields,
        ...egressFlowFields,
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
