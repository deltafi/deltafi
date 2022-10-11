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

import { ref } from "vue";
import useGraphQL from "./useGraphQL";
export default function usePlugins() {
  const { response, queryGraphQL, loading, loaded } = useGraphQL();
  const data = ref(null);

  const fetch = async () => {
    const searchParams = {
      plugins: {
        displayName: true,
        description: true,
        actionKitVersion: true,
        pluginCoordinates: {
          artifactId: true,
          groupId: true,
          version: true,
        },
        actions: {
          name: true,
          description: true,
          requiresDomains: true,
          requiresEnrichments: true,
        },
        variables: {
          name: true,
          value: true,
          description: true,
          defaultValue: true,
          dataType: true,
        },
        propertySets: {
          id: true,
          description: true,
          properties: {
            key: true,
            value: true,
            defaultValue: true,
            description: true,
            editable: true,
            hidden: true,
            refreshable: true,
          },
        },
      },
    };
    await queryGraphQL(searchParams, "getPlugin");
    data.value = response.value.data;
  };

  const setPluginVariableValues = async (update: Object) => {
    const query = {
      setPluginVariableValues: {
        __args: update,
      }
    };
    await queryGraphQL(query, "updatePluginVariable", "mutation");
    return;
  }

  return { data, loading, loaded, fetch, setPluginVariableValues, response };
}
