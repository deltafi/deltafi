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

export default function useAcknowledgeErrors() {
  const { response, queryGraphQL, loading, loaded, errors } = useGraphQL();
  const data = ref(null);

  const post = async (dids: Array<string>, reason: string) => {
    const query = {
      acknowledge: {
        __args: {
          dids: dids,
          reason: reason
        },
        did: true,
        success: true,
        error: true
      },
    };
    await queryGraphQL(query, "getAcknowledgeErrors", "mutation");
    data.value = response.value.data;
  };

  return { data, loading, loaded, post, errors };
}
