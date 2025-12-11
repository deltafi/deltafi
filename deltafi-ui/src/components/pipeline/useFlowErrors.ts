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

// ABOUTME: Composable for fetching unacknowledged error counts per flow.
// ABOUTME: Returns a map of flowName → errorCount for display on graph nodes.

import { ref, computed, Ref } from "vue";
import useGraphQL from "@/composables/useGraphQL";

interface ErrorCountPerFlow {
  flow: string;
  count: number;
}

export default function useFlowErrors() {
  const { response, queryGraphQL, loading } = useGraphQL();
  const rawData: Ref<ErrorCountPerFlow[]> = ref([]);

  const fetch = async () => {
    const query = {
      errorSummaryByFlow: {
        __args: {
          filter: { errorAcknowledged: false },
        },
        countPerFlow: {
          flow: true,
          count: true,
        },
      },
    };

    await queryGraphQL(query, "getErrorCountsByFlow");
    rawData.value = response.value?.data?.errorSummaryByFlow?.countPerFlow || [];
  };

  // Map of flowName → errorCount for easy lookup
  const errorsByFlow = computed(() => {
    const map: Record<string, number> = {};
    for (const item of rawData.value) {
      map[item.flow] = item.count;
    }
    return map;
  });

  // Get error count for a specific flow
  const getErrorCount = (flowName: string): number => {
    return errorsByFlow.value[flowName] || 0;
  };

  return {
    errorsByFlow,
    getErrorCount,
    fetch,
    loading,
  };
}
