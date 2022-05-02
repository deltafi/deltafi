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

import { ref } from 'vue'
import useApi from './useApi'

export default function useSystemMetrics() {
  const { response, get, loading, loaded } = useApi();
  const endpoint = 'metrics/system/nodes';
  const data = ref([]);

  const fetch = async () => {
    try {
      await get(endpoint);
      data.value = response.value.nodes;
    } catch {
      // Continue regardless of error
    }
  }

  return { data, loaded, loading, fetch };
}