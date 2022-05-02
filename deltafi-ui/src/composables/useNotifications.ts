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

export default function useNotifications() {
  const toast = useToast();

  const defaultTTL = {
    success: 5000,
    info: 5000,
    warn: 10000,
    error: 10000,
  };

  const success = (summary: string, detail: string = "", ttl: number = defaultTTL.success) => {
    toast.add({ severity: "success", summary, detail, life: ttl });
  };

  const info = (summary: string, detail: string = "", ttl: number = defaultTTL.info) => {
    toast.add({ severity: "info", summary, detail, life: ttl });
  };

  const warn = (summary: string, detail: string = "", ttl: number = defaultTTL.warn) => {
    toast.add({ severity: "warn", summary, detail, life: ttl });
  };

  const error = (summary: string, detail: string = "", ttl: number = defaultTTL.error) => {
    toast.add({ severity: "error", summary, detail, life: ttl });
  };

  const clear = () => {
    toast.removeAllGroups();
  };

  return { success, info, warn, error, clear };
}