/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>

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

import { ref, readonly, Ref } from "vue";
import useGraphQL from "./useGraphQL";

const invalidDataSourceCount = ref(0);
const invalidTransformCount = ref(0);
const invalidDataSinkCount = ref(0);

let pollingInterval: ReturnType<typeof setInterval> | null = null;

const startPolling = () => {
  if (pollingInterval) return;
  fetchInvalidFlowCounts();
  pollingInterval = setInterval(fetchInvalidFlowCounts, 30000);
};

const fetchInvalidFlowCounts = async () => {
  const { response, queryGraphQL } = useGraphQL();

  const query = {
    getAllFlows: {
      restDataSource: {
        flowStatus: { valid: true },
      },
      timedDataSource: {
        flowStatus: { valid: true },
      },
      onErrorDataSource: {
        flowStatus: { valid: true },
      },
      transform: {
        flowStatus: { valid: true },
      },
      dataSink: {
        flowStatus: { valid: true },
      },
    },
  };

  try {
    await queryGraphQL(query, "getInvalidFlowCounts");
    const flows = response.value.data.getAllFlows;

    const countInvalid = (list: any[]) => list?.filter((f: any) => !f.flowStatus.valid).length ?? 0;

    invalidDataSourceCount.value =
      countInvalid(flows.restDataSource) + countInvalid(flows.timedDataSource) + countInvalid(flows.onErrorDataSource);
    invalidTransformCount.value = countInvalid(flows.transform);
    invalidDataSinkCount.value = countInvalid(flows.dataSink);
  } catch {
    // Silently fail — counts stay at previous values
  }
};

export default function useInvalidFlowCount(): {
  invalidDataSourceCount: Ref<number>;
  invalidTransformCount: Ref<number>;
  invalidDataSinkCount: Ref<number>;
  startPolling: () => void;
  fetchInvalidFlowCounts: () => Promise<void>;
} {
  return {
    invalidDataSourceCount: readonly(invalidDataSourceCount),
    invalidTransformCount: readonly(invalidTransformCount),
    invalidDataSinkCount: readonly(invalidDataSinkCount),
    startPolling,
    fetchInvalidFlowCounts,
  };
}
