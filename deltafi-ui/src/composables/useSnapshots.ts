/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>

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
import { EnumType } from "json-to-graphql-query";

interface SnapshotData {
  id: string;
  reason: string | null;
  created: string;
  schemaVersion: number;
  snapshot: any;
}

export default function useSystemSnapshots() {
  const { response, queryGraphQL, queryGraphQLWithVariables, loading, loaded, errors } = useGraphQL();
  const data = ref(null);
  const mutationData = ref(null);
  const totalCount = ref(0);

  const snapshotFields = {
    id: true,
    reason: true,
    created: true,
    schemaVersion: true,
    snapshot: true
  }

  const fetch = async (offset: number, limit: number, sortField: string, sortDirection: string, filter: Object) => {
    const query = {
      getSystemSnapshotsByFilter: {
        __args: {
          offset: offset,
          limit: limit,
          sortField: new EnumType(sortField.toUpperCase()),
          direction: new EnumType(sortDirection.toUpperCase()),
          filter: filter
        },
        count: true,
        offset: true,
        totalCount: true,
        systemSnapshots: {
          ...snapshotFields
        }
      }
    };
    await queryGraphQL(query, "getSystemSnapshotsByFilter");
    data.value = response.value.data.getSystemSnapshotsByFilter.systemSnapshots;
    totalCount.value = response.value.data.getSystemSnapshotsByFilter.totalCount;
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

  const importSnapshot = async (snapshot: SnapshotData) => {
    const mutation = `
      mutation postImportSystemSnapshot($snapshot: SystemSnapshotInput!) {
        importSnapshot(snapshot: $snapshot) {
          id
          reason
          created
          schemaVersion
          snapshot
        }
      }
    `;
    await queryGraphQLWithVariables(mutation, { snapshot }, "postImportSystemSnapshot");
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

  return { data, loading, loaded, fetch, create, revert, importSnapshot, deleteSnapshot, mutationData, errors, totalCount };
}
