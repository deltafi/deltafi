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

import { ref } from "vue";
import useGraphQL from "./useGraphQL";
export default function useFlowActions() {
  const { response, queryGraphQL, loading, loaded, errors } = useGraphQL();
  const data = ref(null);

  const getPluginActionSchema = async () => {
    const query = {
      plugins: {
        pluginCoordinates: {
          groupId: true,
          artifactId: true,
          version: true,
        },
        actions: {
          name: true,
          type: true,
          supportsJoin: true,
          schema: true,
          actionOptions: {
            description: true
          },
          docsMarkdown: true,
        },
        actionKitVersion: true,
      },
    };
    return sendGraphQLQuery(query, "getPluginActionSchema");
  };

  const sendGraphQLQuery = async (query: any, operationName: string) => {
    try {
      await queryGraphQL(query, operationName);
      data.value = response.value.data;
      return response.value;
    } catch {
      // Continue regardless of error
    }
  };

  return { data, loading, loaded, getPluginActionSchema, errors };
}
