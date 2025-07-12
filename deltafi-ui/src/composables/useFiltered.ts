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
import { EnumType } from "json-to-graphql-query";
import _ from "lodash";
export default function useFiltered() {
  const { response, queryGraphQL, loading, loaded, errors } = useGraphQL();
  const data = ref(null);

  interface queryParams {
    startTimeDateString: string;
    endTimeDateString: string;
  }

  const fetchAllFiltered = async (queryParams: queryParams, offSet: Number, perPage: Number, sortBy: string, sortDirection: string, flowName?: string, flowType?: string, filteredCause?: string) => {
    const flowFilters: Record<string, Array<string>> = {
      dataSources: [],
      dataSinks: [],
      transforms: [],
    };

    if (flowName && flowType) {
      switch (flowType) {
        case "dataSink":
          flowFilters.dataSinks.push(flowName);
          break;
        case "transform":
          flowFilters.transforms.push(flowName);
          break;
        case "timedDataSource":
          flowFilters.dataSources.push(flowName);
          break;
        case "restDataSource":
          flowFilters.dataSources.push(flowName);
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
            filtered: true,
            filteredCause: filteredCause,
            modifiedAfter: queryParams.startTimeDateString,
            modifiedBefore: queryParams.endTimeDateString,
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
          name: true,
          dataSource: true,
          stage: true,
          modified: true,
          created: true,
          flows: {
            name: true,
            state: true,
            created: true,
            modified: true,
            actions: {
              name: true,
              created: true,
              modified: true,
              filteredCause: true,
              state: true,
            },
          },
        },
      },
    };
    await queryGraphQL(searchParams, "getFiltered");
    data.value = response.value.data;
  };

  const fetchFilteredSummaryByFlow = async (queryParams: queryParams, offSet: Number, perPage: Number, sortField: string, sortDirection: string, flow: string) => {
    const searchParamsFlow = {
      filteredSummaryByFlow: {
        __args: {
          limit: perPage,
          offset: offSet,
          filter: {
            flow: flow,
            modifiedAfter: queryParams.startTimeDateString,
            modifiedBefore: queryParams.endTimeDateString,
          },
          direction: new EnumType(sortDirection),
          sortField: new EnumType(sortField),
        },
        count: true,
        totalCount: true,
        countPerFlow: {
          count: true,
          flow: true,
          type: true,
          dids: true,
        },
      },
    };
    await queryGraphQL(searchParamsFlow, "getFilteredByFlow");
    data.value = response.value.data.filteredSummaryByFlow;
  };

  const fetchFilteredSummaryByMessage = async (queryParams: queryParams, offSet: Number, perPage: Number, sortField: string, sortDirection: string, flow: string) => {
    const searchParams = {
      filteredSummaryByMessage: {
        __args: {
          limit: perPage,
          offset: offSet,
          filter: {
            flow: flow,
            modifiedAfter: queryParams.startTimeDateString,
            modifiedBefore: queryParams.endTimeDateString,
          },
          direction: new EnumType(sortDirection),
          sortField: new EnumType(sortField),
        },
        count: true,
        totalCount: true,
        countPerMessage: {
          count: true,
          message: true,
          flow: true,
          type: true,
          dids: true,
        },
      },
    };
    await queryGraphQL(searchParams, "getFilteredByMessage");
    data.value = response.value.data.filteredSummaryByMessage;
  };

  const fetchUniqueMessages = async () => {
    const searchParams = {
      filteredSummaryByMessage: {
        countPerMessage: {
          message: true,
        },
      },
    };
    await queryGraphQL(searchParams, "getFilteredByMessage");
    return _.chain(response.value.data.filteredSummaryByMessage.countPerMessage)
      .map((o) => o.message)
      .uniq()
      .sort()
      .value();
  };

  return { data, loading, loaded, fetchAllFiltered, fetchFilteredSummaryByFlow, fetchFilteredSummaryByMessage, fetchUniqueMessages, errors };
}
