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

import { ref } from 'vue'
import useGraphQL from './useGraphQL'
export default function useSystemSnapshots() {
  const { response, queryGraphQL, loading, loaded, errors } = useGraphQL();
  const data = ref(null);
  const mutationData = ref(null);

  const fetch = async () => {
    const query = {
      getSystemSnapshots: {
        id: true,
        reason: true,
        created: true,
        deletePolicies: {
          timedPolicies: {
            id: true,
            name: true,
            enabled: true,
            locked: true,
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
            locked: true,
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
        propertySets: {
          id: true,
          displayName: true,
          description: true,
          properties: {
            key: true,
            description: true,
            defaultValue: true,
            refreshable: true,
            editable: true,
            hidden: true,
            value: true,
          }
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
            value: true
          }
        },
        runningIngressFlows: true,
        runningEnrichFlows: true,
        runningEgressFlows: true,
        testIngressFlows: true,
        testEgressFlows: true
      }
    };
    await queryGraphQL(query, "getSystemSnapshots");
    data.value = response.value.data.getSystemSnapshots
      .sort((a:any, b:any) => (a.created < b.created ? 1 : -1));
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
  
  const importSnapshot = async (snapShot: JSON) => {
    const query = {
      importSnapshot: {
        __args: {
          snapshot: snapShot
        },
        id: true,
      },
    };
    await queryGraphQL(query, "postImportSystemSnapshot", "mutation");
    mutationData.value = response.value.data.importSnapshot;
  };

  return { data, loading, loaded, fetch, create, revert, importSnapshot,mutationData, errors };
}
