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
import { EnumType } from "json-to-graphql-query";

export default function useErrors() {
  const { response, queryGraphQL, loading, loaded, errors } = useGraphQL();
  const data = ref(null);

  const fetch = async (showAcknowledged: boolean, offSet: Number, perPage: Number, sortBy: string, sortDirection: string, flowName?: string, flowType?: string, errorCause?: string, filteredCause?: string) => {
    const flowFilters: Record<string, Array<string>> = {
      dataSources: [],
      dataSinks: [],
      transforms: [],
    }

    if (flowName && flowType) {
      switch (flowType) {
        case "dataSink":
          flowFilters.dataSinks.push(flowName)
          break;
        case "transform":
          flowFilters.transforms.push(flowName)
          break;
        case "timedDataSource":
          flowFilters.dataSources.push(flowName)
          break;
        case "restDataSource":
          flowFilters.dataSources.push(flowName)
          break;
      }
    }

    const searchParams = {
      deltaFiles: {
        __args: {
          limit: perPage,
          offset: offSet,
          filter: {
            ...flowFilters,
            stage: new EnumType("ERROR"),
            errorAcknowledged: showAcknowledged,
            errorCause: errorCause,
            filteredCause: filteredCause,
          },
          orderBy: {
            direction: new EnumType(sortDirection),
            field: sortBy,
          },
        },
        offset: true,
        count: true,
        totalCount: true,
        deltaFiles: {
          did: true,
          stage: true,
          modified: true,
          created: true,
          name: true,
          dataSource: true,
          flows: {
            name: true,
            created: true,
            modified: true,
            state: true,
            errorAcknowledged: true,
            errorAcknowledgedReason: true,
            actions: {
              name: true,
              created: true,
              modified: true,
              filteredCause: true,
              errorCause: true,
              errorContext: true,
              state: true,
              nextAutoResume: true,
              nextAutoResumeReason: true,
              content: {
                name: true,
                mediaType: true,
                size: true,
                segments: {
                  uuid: true,
                  offset: true,
                  size: true,
                  did: true,
                },
              },
            }
          },
        }
      },
    };

    await queryGraphQL(searchParams, "getErrors");
    data.value = response.value.data;
  };

  return { data, loading, loaded, fetch, errors };
}
