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

import useGraphQL from "./useGraphQL";

export default function useSystemProperties() {
  const { response, queryGraphQL, loading, loaded, errors } = useGraphQL();

  const get = async (propertyName: string) => {
    const query = {
      getDeltaFiProperties: {} as Record<string, any>,
    };
    query.getDeltaFiProperties[propertyName] = true;
    try {
      await queryGraphQL(query, "getDeltaFiProperties");
      return response.value.data.getDeltaFiProperties[propertyName];
    } catch {
      // Continue regardless of error
    }
  };

  const set = async (propertyName: string, propertyValue: string) => {
    const query = {
      updateProperties: {
        __args: {
          updates: [
            {
              key: propertyName,
              value: propertyValue,
            },
          ],
        },
      },
    };
    try {
      await queryGraphQL(query, "updateProperties", "mutation");
      return response.value.data.updateProperties;
    } catch {
      // Continue regardless of error
    }
  };

  const reset = async (propertyName: string) => {
    const query = {
      removePropertyOverrides: {
        __args: {
          propertyNames: propertyName,
        },
      },
    };
    try {
      await queryGraphQL(query, "removePropertyOverrides", "mutation");
      return response.value.data.removePropertyOverrides;
    } catch {
      // Continue regardless of error
    }
  };

  return { loading, loaded, get, set, reset, errors };
}
