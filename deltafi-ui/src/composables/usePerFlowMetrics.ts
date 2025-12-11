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

// ABOUTME: Composable for fetching per-flow metrics from VictoriaMetrics.
// ABOUTME: Returns files in/out and bytes in/out per flow.

import { ref, Ref } from "vue";
import useApi from "./useApi";

export interface FlowKey {
  flowType: string;
  flowName: string;
}

export interface PerFlowMetrics {
  flowType: string;
  flowName: string;
  filesIn: number;
  filesOut: number;
  bytesIn: number;
  bytesOut: number;
}

export default function usePerFlowMetrics() {
  const { response, post, loading, loaded, errors } = useApi();
  const endpoint = "metrics/perflow";
  const data: Ref<PerFlowMetrics[]> = ref([]);

  const fetch = async (flowKeys: FlowKey[], minutes: number = 60) => {
    try {
      await post(endpoint + `?minutes=${minutes}`, flowKeys);
      data.value = response.value || [];
    } catch {
      data.value = [];
    }
  };

  return { data, loaded, loading, fetch, errors };
}
