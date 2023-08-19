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
export default function useMetadata() {
  const { response, queryGraphQL, loading, loaded, errors } = useGraphQL();
  const data = ref(null);

  const fetch = async (dids: Array<string>) => {
    const searchParams = {
      sourceMetadataUnion: {
        __args: {
          dids: dids,
        },
        key: true,
        values: true,
      },
    };
    await queryGraphQL(searchParams, "getMetadata");
    data.value = response.value.data.sourceMetadataUnion;
  };

  const fetchAll = async (dids: Array<string>) => {
    const searchParamsUnion = {
      errorMetadataUnion: {
        __args: {
          dids: dids,
        },
        flow: true,
        action: true,
        keyVals:{
          key: true,
          values: true,
        },
      },
    };
    await queryGraphQL(searchParamsUnion, "getMetadata");
    data.value = response.value.data.errorMetadataUnion;
  };

  return { data, loading, loaded, fetch, errors, fetchAll };
}
