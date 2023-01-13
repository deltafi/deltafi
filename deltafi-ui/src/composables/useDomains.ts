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

import { ref } from 'vue'
import useGraphQL from './useGraphQL'

export default function useDomains() {
  const { response, queryGraphQL, loading, errors } = useGraphQL();
  const loaded = ref(false);

  const getDomains = async () => {
    await queryGraphQL("domains", "getDomains");
    loaded.value = true;
    return response.value.data.domains;
  }

  const getIndexedMetadataKeys = async (domain: String) => {
    const query = { indexedMetadataKeys: {} };
    if (domain) {
        query.indexedMetadataKeys = {
        __args: {
          domain: domain
        }
      }
    }
    await queryGraphQL(query, "getIndexedMetadataKeys", "query", true);
    loaded.value = true;
    return response.value.data.indexedMetadataKeys;
  }

  return { loading, loaded, getDomains, getIndexedMetadataKeys, errors };
}
