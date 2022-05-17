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

import { ref, Ref } from 'vue'
import useApi from './useApi'

export default function useActionMetrics() {
  const { response, get, loading, loaded, errors } = useApi();
  const endpoint: string = 'metrics/action';
  const data: Ref<object> = ref([]);

  const fetch = async (paramsObj: Record<string, string>) => {
    try {
      const params = new URLSearchParams(paramsObj);
      await get(endpoint, params);
      data.value = response.value.actions;
    } catch {
      // Continue regardless of error
    }
  }

  return { data, loaded, loading, fetch, errors };
}