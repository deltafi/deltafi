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
import useGraphQL from './useGraphQL'

export default function usePropertySets() {
  const { response, queryGraphQL, loading, loaded, errors } = useGraphQL();
  const data = ref(null);

  const fetchQuery = {
    getPropertySets: {
      id: true,
      displayName: true,
      description: true,
      properties: {
        key: true,
        value: true,
        hidden: true,
        editable: true,
        refreshable: true,
        description: true,
        propertySource: true
      }
    }
  }

  const fetch = async () => {
    try {
      await queryGraphQL(fetchQuery, "getPropertySets");
      data.value = response.value.data.getPropertySets;
    } catch {
      // Continue regardless of error
    }
  }

  const update = async (updates: Array<Object>) => {
    const query = {
      updateProperties: {
        __args: {
          updates: updates,
        },
      },
    };
    try {
      await queryGraphQL(query, "updatePropertySets", "mutation");
      data.value = response.value.data;
    } catch {
      // Continue regardless of error
    }
  }


  return { data, loading, loaded, fetch, update, errors };
}