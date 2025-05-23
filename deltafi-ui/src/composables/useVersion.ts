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

import { ref, Ref } from "vue";
import useGraphQL from "./useGraphQL";
import useApi from "./useApi";

const version: Ref = ref("");

export default function useVersion() {
  const { response, queryGraphQL } = useGraphQL();

  const query = {
    version: true,
  };

  const fetchVersion = async () => {
    try {
      await queryGraphQL(query, "version", "query");
      version.value = response.value.data.version;

      // For development only
      if (import.meta.env.MODE === "development") {
        const { response: apiResponse, get } = useApi();
        await get("local-git-branch");
        const branch = apiResponse.value.branch;
        if (branch) version.value += ` (${branch})`;
      }
    } catch {
      // Continue regardless of error
    }
  };

  return { version, fetchVersion };
}
