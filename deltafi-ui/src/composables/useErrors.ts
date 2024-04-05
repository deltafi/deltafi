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
import _ from "lodash";
export default function useErrors() {
  const { response, queryGraphQL, loading, loaded, errors } = useGraphQL();
  const data = ref(null);

  const fetch = async (showAcknowledged: boolean, offSet: Number, perPage: Number, sortBy: string, sortDirection: string, flowName?: string, errorCause?: string, filteredCause?: string) => {
    const searchParams = {
      deltaFiles: {
        __args: {
          limit: perPage,
          offset: offSet,
          filter: {
            sourceInfo: {
              flow: flowName,
            },
            stage: new EnumType("ERROR"),
            errorAcknowledged: showAcknowledged,
            errorCause: !_.isEmpty(errorCause) ? `\\Q${errorCause}\\E` : null,
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
          flows: {
            name: true,
            created: true,
            modified: true,
            state: true,
            actions: {
              name: true,
              created: true,
              modified: true,
              filteredCause: true,
              errorCause: true,
              errorContext: true,
              state: true,
              errorAcknowledged: true,
              errorAcknowledgedReason: true,
              nextAutoResume: true,
              nextAutoResumeReason: true,
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
