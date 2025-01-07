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

import { ref, Ref, computed } from "vue";

enum Severity {
  SUCCESS = "success",
  INFO = "info",
  WARN = "warn",
  ERROR = "error",
}

const defaultTTL = {
  success: 5000,
  info: 5000,
  warn: 10000,
  error: 10000,
};

const currentNotifications: Array<string> = [];
const queue: Ref<Array<Object>> = ref([]);
const queueSize = computed(() => {
  return queue.value.length;
});

export default function useNotifications() {
  const queueMessage = (severity: Severity, summary: string, detail: string, ttl: number) => {
    const hash = severity + summary + detail;

    // Check if message is in the list
    if (currentNotifications.includes(hash)) {
      // If it is, do nothing
      return;
    } else {
      // If it's not, put message in the list
      currentNotifications.push(hash);

      // Remove message from the list after TTL seconds
      setTimeout(() => {
        const index = currentNotifications.indexOf(hash);
        currentNotifications.splice(index, 1);
      }, ttl);

      queue.value.push({ severity: severity, summary, detail, life: ttl });
      console.debug(`[${severity.toUpperCase()}] ${summary} - ${detail}`);
    }
  };

  const success = (summary: string, detail: string = "", ttl: number = defaultTTL.success) => {
    queueMessage(Severity.SUCCESS, summary, detail, ttl);
  };

  const info = (summary: string, detail: string = "", ttl: number = defaultTTL.info) => {
    queueMessage(Severity.INFO, summary, detail, ttl);
  };

  const warn = (summary: string, detail: string = "", ttl: number = defaultTTL.warn) => {
    queueMessage(Severity.WARN, summary, detail, ttl);
  };

  const error = (summary: string, detail: string = "", ttl: number = defaultTTL.error) => {
    queueMessage(Severity.ERROR, summary, detail, ttl);
  };

  return { success, info, warn, error, queue, queueSize };
}
