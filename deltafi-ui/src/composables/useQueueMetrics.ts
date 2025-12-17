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

import { ref, Ref } from 'vue'
import useApi from './useApi'

export interface WarmQueueMetrics {
  actionClass: string;
  flowName: string;
  flowType: string;
  actionName: string;
  count: number;
  oldestQueuedAt: string | null;
}

export interface ColdQueueMetrics {
  flowName: string;
  flowType: string;
  actionName: string;
  actionClass: string;
  count: number;
  oldestQueuedAt: string | null;
}

export interface DetailedQueueMetrics {
  warmQueues: WarmQueueMetrics[];
  coldQueues: ColdQueueMetrics[];
  timestamp: string;
}

export type ActionPluginMapping = Record<string, string>;

export interface RunningTask {
  actionClass: string;
  actionName: string;
  did: string;
  startTime: string;
  runningForMs: number;
  appName: string | null;
}

export interface RunningTasksResponse {
  tasks: RunningTask[];
  heartbeatThresholdMs: number;
}

export default function useQueueMetrics() {
  const { response, get, loading, loaded } = useApi();
  const endpoint: string = 'metrics/queues/detailed';
  const data: Ref<DetailedQueueMetrics | null> = ref(null);

  const fetch = async () => {
    try {
      await get(endpoint);
      data.value = response.value as DetailedQueueMetrics;
    } catch {
      // Continue regardless of error
    }
  }

  return { data, loaded, loading, fetch };
}

export function useActionPlugins() {
  const { response, get, loading, loaded } = useApi();
  const endpoint: string = 'metrics/queues/action-plugins';
  const data: Ref<ActionPluginMapping | null> = ref(null);

  const fetch = async () => {
    try {
      await get(endpoint);
      data.value = response.value as ActionPluginMapping;
    } catch {
      // Continue regardless of error
    }
  }

  return { data, loaded, loading, fetch };
}

export function useRunningTasks() {
  const { response, get, loading, loaded } = useApi();
  const endpoint: string = 'metrics/queues/running';
  const data: Ref<RunningTasksResponse | null> = ref(null);

  const fetch = async () => {
    try {
      await get(endpoint);
      data.value = response.value as RunningTasksResponse;
    } catch {
      // Continue regardless of error
    }
  }

  return { data, loaded, loading, fetch };
}

export interface FlowQueueCounts {
  warm: number;
  cold: number;
  total: number;
}

// Aggregates queue metrics by flow name for use in system map and pipeline graphs
export function aggregateQueueCountsByFlow(metrics: DetailedQueueMetrics | null): Record<string, FlowQueueCounts> {
  const counts: Record<string, FlowQueueCounts> = {};
  if (!metrics) return counts;

  const ensureEntry = (flowName: string) => {
    if (!counts[flowName]) {
      counts[flowName] = { warm: 0, cold: 0, total: 0 };
    }
  };

  for (const warm of metrics.warmQueues || []) {
    ensureEntry(warm.flowName);
    counts[warm.flowName].warm += warm.count;
    counts[warm.flowName].total += warm.count;
  }
  for (const cold of metrics.coldQueues || []) {
    ensureEntry(cold.flowName);
    counts[cold.flowName].cold += cold.count;
    counts[cold.flowName].total += cold.count;
  }

  return counts;
}
