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

import { ref } from 'vue'
import useGraphQL from './useGraphQL'
export default function useSystemSnapshots() {
  const { response, queryGraphQL, loading, loaded, errors } = useGraphQL();
  const data = ref(null);
  const mutationData = ref(null);

  const snapshotFields = {
    id: true,
    reason: true,
    created: true,
    deletePolicies: {
      timedPolicies: {
        id: true,
        name: true,
        enabled: true,
        flow: true,
        afterCreate: true,
        afterComplete: true,
        minBytes: true,
        deleteMetadata: true
      },
      diskSpacePolicies: {
        id: true,
        name: true,
        enabled: true,
        flow: true,
        maxPercent: true
      }
    },
    flowAssignmentRules: {
      id: true,
      name: true,
      flow: true,
      priority: true,
      filenameRegex: true,
      requiredMetadata: {
        key: true,
        value: true
      }
    },
    resumePolicies: {
      id: true,
      name: true,
      errorSubstring: true,
      flow: true,
      action: true,
      actionType: true,
      maxAttempts: true,
      backOff: {
        delay: true,
        maxDelay: true,
        multiplier: true,
        random: true
      }
    },
    deltaFiProperties: {
      systemName: true,
      requeueDuration: true,
      autoResumeCheckFrequency: true,
      coreServiceThreads: true,
      coreInternalQueueSize: true,
      scheduledServiceThreads: true,
      ui: {
        useUTC: true,
        deltaFileLinks: {
          name: true,
          url: true,
          description: true
        },
        externalLinks: {
          name: true,
          url: true,
          description: true
        },
        topBar: {
          textColor: true,
          backgroundColor: true
        },
        securityBanner: {
          enabled: true,
          text: true,
          textColor: true,
          backgroundColor: true
        }
      },
      delete: {
        ageOffDays: true,
        frequency: true,
        policyBatchSize: true,
      },
      ingress: {
        enabled: true,
        diskSpaceRequirementInMb: true
      },
      metrics: {
        enabled: true
      },
      plugins: {
        imageRepositoryBase: true,
        imagePullSecret: true
      },
      checks: {
        actionQueueSizeThreshold: true,
        contentStoragePercentThreshold: true
      },
      inMemoryQueueSize: true,
      setProperties: true
    },
    pluginImageRepositories: {
      imageRepositoryBase: true,
      pluginGroupIds: true,
      imagePullSecret: true
    },
    installedPlugins: {
      groupId: true,
      artifactId: true,
      version: true
    },
    pluginVariables: {
      sourcePlugin: {
        groupId: true,
        artifactId: true,
        version: true
      },
      variables: {
        name: true,
        description: true,
        dataType: true,
        required: true,
        defaultValue: true,
        value: true,
        masked: true
      }
    },
    transformFlows: {
      name: true,
      running: true,
      testMode: true,
      maxErrors: true,
      expectedAnnotations: true
    },
    normalizeFlows: {
      name: true,
      running: true,
      testMode: true,
      maxErrors: true
    },
    enrichFlows: {
      name: true,
      running: true,
    },
    egressFlows: {
      name: true,
      running: true,
      testMode: true,
      expectedAnnotations: true
    },
    pluginCustomizationConfigs: {
      groupId: true,
      artifactId: true,
      urlTemplate: true,
      secretName: true
    }
  }

  const fetch = async () => {
    const query = {
      getSystemSnapshots: {
        ...snapshotFields
      }
    };
    await queryGraphQL(query, "getSystemSnapshots");
    data.value = response.value.data.getSystemSnapshots
      .sort((a: any, b: any) => (a.created < b.created ? 1 : -1));
  };

  const create = async (reason: string) => {
    const query = {
      snapshotSystem: {
        __args: {
          reason: reason,
        },
        id: true,
      },
    };
    await queryGraphQL(query, "postCreateSystemSnapshot", "mutation");
    mutationData.value = response.value.data.snapshotSystem.id;
  };
  const revert = async (id: string) => {
    const query = {
      resetFromSnapshotWithId: {
        __args: {
          snapshotId: id,
        },
        success: true,
        errors: true,
      },
    };
    await queryGraphQL(query, "postRevertSystemSnapshot", "mutation");
    mutationData.value = response.value.data.resetFromSnapshotWithId;
  };

  const importSnapshot = async (snapshot: JSON) => {
    const query = {
      importSnapshot: {
        __args: {
          snapshot: snapshot
        },
        ...snapshotFields
      },
    };
    await queryGraphQL(query, "postImportSystemSnapshot", "mutation", true);
    mutationData.value = response.value.data.importSnapshot;
  };

  const deleteSnapshot = async (id: string) => {
    const query = {
      deleteSnapshot: {
        __args: {
          snapshotId: id,
        },
        success: true,
        errors: true,
        info: true,
      },
    };
    await queryGraphQL(query, "deleteSystemSnapshot", "mutation");
    return response.value.data.deleteSnapshot;
  };

  return { data, loading, loaded, fetch, create, revert, importSnapshot, deleteSnapshot, mutationData, errors };
}
