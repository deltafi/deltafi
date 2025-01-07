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

import { ref, readonly, Ref } from 'vue';
import { EnumType } from 'json-to-graphql-query';
import useGraphQL from './useGraphQL'
import useServerSentEvents from "@/composables/useServerSentEvents";

const errorCount = ref(0)

const { serverSentEvents } = useServerSentEvents();
serverSentEvents.addEventListener('errorCount', (event: any) => {
  errorCount.value = parseInt(event.data);
});

export default function useErrorCount(): {
  errorCount: Ref<number>
  fetchErrorCount: () => void;
  fetchErrorCountSince: () => void
} {
  const { response, queryGraphQL } = useGraphQL();

  const setErrorCount = ($errorCount: number) => {
    return (errorCount.value = $errorCount);
  };

  const buildQuery = async (since: Date = new Date(0)) => {
    const query = {
      deltaFiles: {
        __args: {
          filter: {
            stage: new EnumType('ERROR'),
            errorAcknowledged: false,
            modifiedAfter: since.toISOString()
          },
        },
        totalCount: true,
      }
    };
    await queryGraphQL(query, "getErrorCount");
    return response.value.data.deltaFiles.totalCount;
  }

  const fetchErrorCount = async (since: Date = new Date(0)) => {
    const count = buildQuery(since);
    setErrorCount(await count);
  };

  const fetchErrorCountSince = async (since: Date = new Date(0)) => {
    return buildQuery(since);
  }

  return {
    errorCount: readonly(errorCount),
    fetchErrorCount,
    fetchErrorCountSince,
  };
}
