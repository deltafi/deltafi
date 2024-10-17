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

import { ref, Ref } from "vue";
import { EnumType } from "json-to-graphql-query";
import useGraphQL from "./useGraphQL";

export default function useFlows() {
  const { response, queryGraphQL, loading, loaded, errors } = useGraphQL();
  const egressFlows: Ref<Array<Record<string, string>>> = ref([]);
  const restDataSourceFlowNames: Ref<Array<Record<string, string>>> = ref([]);
  const timedDataSourceFlowNames: Ref<Array<Record<string, string>>> = ref([]);

  interface getFlowNames {
    restDataSource: Array<string>;
    timedDataSource: Array<string>;
  }

  const allDataSourceFlowNames = ref<getFlowNames>({
    restDataSource: [],
    timedDataSource: [],
  });

  const buildQuery = (egress: Boolean, transform: Boolean, restDataSource: Boolean, timedDataSource: Boolean, state?: string) => {
    return {
      getFlowNames: {
        __args: {
          state: state ? new EnumType(state) : null,
        },
        egress: egress,
        transform: transform,
        restDataSource: restDataSource,
        timedDataSource: timedDataSource,
      },
    };
  };

  const fetchAllDataSourceFlowNames = async (state?: string) => {
    await queryGraphQL(buildQuery(false, false, true, true, state), "getAllDataSourceFlowNames");
    allDataSourceFlowNames.value = response.value.data.getFlowNames;
    allDataSourceFlowNames.value["restDataSource"] = response.value.data.getFlowNames.restDataSource.sort();
    allDataSourceFlowNames.value["timedDataSource"] = response.value.data.getFlowNames.timedDataSource.sort();
  };

  const fetchRestDataSourceFlowNames = async (state?: string) => {
    await queryGraphQL(buildQuery(false, false, true, false, state), "getRestDataSourceFlowNames");
    restDataSourceFlowNames.value = response.value.data.getFlowNames.restDataSource.sort();
  };

  const fetchTimedDataSourceFlowNames = async (state?: string) => {
    await queryGraphQL(buildQuery(false, false, false, true, state), "getTimedDataSourceFlowNames");
    timedDataSourceFlowNames.value = response.value.data.getFlowNames.timedDataSource.sort();
  };

  const fetchEgressFlowNames = async (state?: string) => {
    await queryGraphQL(buildQuery(true, false, false, false, state), "getEgressFlowNames");
    egressFlows.value = response.value.data.getFlowNames.egress.sort();
  };

  const fetchAllFlowNames = async (state?: string) => {
    await queryGraphQL(buildQuery(true, true, true, true, state), "getAllFlowNames");
    return response.value.data.getFlowNames;
  };

  return { egressFlows, allDataSourceFlowNames, restDataSourceFlowNames, timedDataSourceFlowNames, fetchEgressFlowNames, fetchAllDataSourceFlowNames, fetchRestDataSourceFlowNames, fetchTimedDataSourceFlowNames, fetchAllFlowNames, loading, loaded, errors };
}
