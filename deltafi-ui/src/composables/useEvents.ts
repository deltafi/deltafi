/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>

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
import useNotifications from "./useNotifications";
import useUtilFunctions from "@/composables/useUtilFunctions";

export default function useEvents() {
  const notify = useNotifications();
  const { pluralize } = useUtilFunctions();
  const { response, get, put, loading, loaded } = useApi();
  const endpoint: string = 'events';
  const data: Ref<Array<any>> = ref([]);
  const errors: Ref<Array<string>> = ref([]);

  const fetch = async (options?: Record<string, any>) => {
    try {
      if (options) {
        const params = new URLSearchParams(options);
        await get(endpoint, params);
      } else {
        await get(endpoint);
      }
      data.value = response.value;
    } catch (response: any) {
      return Promise.reject(response);
    }
  }

  const acknowledgeEvent = async (ids: string | Array<string>, acknowledge: boolean = true) => {
    const action = acknowledge ? "acknowledge" : "unacknowledge";

    ids = Array.isArray(ids) ? ids : new Array(`${ids}`);

    const promises = ids.map((id) => {
      try {
        return put(`${endpoint}/${id}/${action}`, null);
      } catch (response: any) {
        return Promise.reject(response);
      }
    });

    await Promise.all(promises);
    const pluralized = pluralize(ids.length, "Event");
    const pastTenseVerb = `${action.charAt(0).toUpperCase() + action.slice(1)}d`;
    notify.info(`${pastTenseVerb} ${pluralized}`)
  }

  const unacknowledgeEvent = async (ids: string | Array<string>) => {
    await acknowledgeEvent(ids, false);
  }

  return { data, loaded, loading, fetch, acknowledgeEvent, unacknowledgeEvent, errors };
}