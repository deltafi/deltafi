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

import { ref } from 'vue'
import useGraphQL from './useGraphQL'
import { EnumType } from 'json-to-graphql-query';
export default function useErrors() {
  const { response, queryGraphQL, loading, loaded, errors } = useGraphQL();
  const data = ref(null);

  const fetchByMessage = async (showAcknowledged: boolean, offSet: Number, perPage: Number, sortDirection: string, flow: string) => {
    const searchParams = {
      errorSummaryByMessage: {
        __args: {
          limit: perPage,
          offset: offSet,
          filter: {
            flow: flow,
            errorAcknowledged: showAcknowledged,
          },
          direction: new EnumType(sortDirection),
        },
        count: true,
        totalCount: true,
        countPerMessage: {
          count: true,
          message: true,
          flow: true,
          dids: true,
        },
      }
    }
    await queryGraphQL(searchParams, "getErrorsByMessage");
    data.value = response.value.data.errorSummaryByMessage;
  };
  const fetchAllMessage = async () => {
    const searchParams = {
      errorSummaryByMessage: {
        countPerMessage: {
          message: true,
        },
      }
    }
    await queryGraphQL(searchParams, "getErrorsByMessage");
    data.value = response.value.data.errorSummaryByMessage.countPerMessage;
  };

  const fetchByFlow = async (showAcknowledged: boolean, offSet: Number, perPage: Number, sortDirection: string, flow: string) => {
    const searchParamsFlow = {
      errorSummaryByFlow: {
        __args: {
          limit: perPage,
          offset: offSet,
          filter: {
            flow: flow,
            errorAcknowledged: showAcknowledged,
          },
          direction: new EnumType(sortDirection),
        },
        count: true,
        totalCount: true,
        countPerFlow: {
          count: true,
          flow: true,
          dids: true,
        },
      }
    }
    await queryGraphQL(searchParamsFlow, "getErrorsByFlow");
    data.value = response.value.data.errorSummaryByFlow;
  };

  return { data, loading, loaded, fetchByMessage, fetchByFlow, fetchAllMessage, errors };
}
