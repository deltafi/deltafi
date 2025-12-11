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

// ABOUTME: Composable for fetching action-level metrics from VictoriaMetrics.
// ABOUTME: Returns files in/out, bytes in/out, execution count, and timing per action.

import { ref, Ref } from "vue";
import useApi from "./useApi";

export interface ActionMetrics {
  actionName: string;
  executionCount: number;
  executionTimeMs: number;
}

export default function useActionMetrics() {
  const { response, post, loading, loaded, errors } = useApi();
  const endpoint = "metrics/action";
  const data: Ref<ActionMetrics[]> = ref([]);

  const fetch = async (actionNames: string[], minutes: number = 60) => {
    try {
      await post(endpoint + `?minutes=${minutes}`, actionNames);
      data.value = response.value || [];
    } catch {
      data.value = [];
    }
  };

  return { data, loaded, loading, fetch, errors };
}
