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
import { EnumType } from "json-to-graphql-query";
import useGraphQL from "./useGraphQL";

export default function useRateLimiting() {
  const { errors, loading, loaded, queryGraphQL, response } = useGraphQL();
  const data = ref(null);

  const setRestDataSourceRateLimit = async (name: string, unit: string, maxAmount: Number, durationSeconds: Number) => {
    const query = {
      setRestDataSourceRateLimit: {
        __args: {
          name: name,
          rateLimit: {
            unit: new EnumType(unit),
            maxAmount: maxAmount,
            durationSeconds: durationSeconds,
          },
        },
      },
    };

    return sendGraphQLQuery(query, "setRestDataSourceRateLimit", "mutation");
  };

  // Stops an egress flow
  const removeRestDataSourceRateLimit = (name: string) => {
    const query = {
      removeRestDataSourceRateLimit: {
        __args: {
          name: name,
        },
      },
    };
    return sendGraphQLQuery(query, "removeRestDataSourceRateLimit", "mutation");
  };

  const sendGraphQLQuery = async (query: any, operationName: string, queryType?: string) => {
    try {
      await queryGraphQL(query, operationName, queryType);
      data.value = response.value.data;
      return response.value.data;
    } catch {
      // Continue regardless of error
    }
  };

  return {
    errors,
    data,
    loading,
    loaded,
    response,
    setRestDataSourceRateLimit,
    removeRestDataSourceRateLimit,
  };
}
