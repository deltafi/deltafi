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

import { ref } from "vue";
import useGraphQL from "./useGraphQL";

export default function useDomains() {
  const { response, queryGraphQL, loading, errors } = useGraphQL();
  const loaded = ref(false);
  const domains = ref([]);

  const getAnnotationKeys = async () => {
    const query = {
      annotationKeys: {
        __args: {},
      },
    };
    await queryGraphQL(query, "getAnnotationKeys", "query", true);
    loaded.value = true;
    return response.value.data.annotationKeys;
  };

  return { loading, loaded, getAnnotationKeys: getAnnotationKeys, errors };
}
