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

import { useToast } from "primevue/usetoast";
import _ from "lodash";

const currentNotifications: Array<string> = [];

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

export default function useNotifications() {
  const showToast = (severity: Severity, summary: string, detail: string, ttl: number) => {
    const mountPointSelector = "#app";
    const mountPoint: any = document.querySelector(mountPointSelector);
    const isAppMounted = _.get(mountPoint, "__vue_app__", null);
    if (!isAppMounted) return;

    const toast = useToast();
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

      toast.add({ severity: severity, summary, detail, life: ttl });
    }
  };

  const success = (summary: string, detail: string = "", ttl: number = defaultTTL.success) => {
    showToast(Severity.SUCCESS, summary, detail, ttl);
  };

  const info = (summary: string, detail: string = "", ttl: number = defaultTTL.info) => {
    showToast(Severity.INFO, summary, detail, ttl);
  };

  const warn = (summary: string, detail: string = "", ttl: number = defaultTTL.warn) => {
    showToast(Severity.WARN, summary, detail, ttl);
  };

  const error = (summary: string, detail: string = "", ttl: number = defaultTTL.error) => {
    showToast(Severity.ERROR, summary, detail, ttl);
  };

  const clear = () => {
    const toast = useToast();
    toast.removeAllGroups();
    currentNotifications.length = 0;
  };

  return { success, info, warn, error, clear };
}