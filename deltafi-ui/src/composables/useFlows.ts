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

import { ref, Ref } from 'vue'
import { EnumType } from 'json-to-graphql-query';
import useGraphQL from './useGraphQL'
import _ from "lodash";

export default function useFlows() {
  const { response, queryGraphQL, loading, loaded, errors } = useGraphQL();
  const ingressFlows: Ref<Array<Record<string, string>>> = ref([]);
  const egressFlows: Ref<Array<Record<string, string>>> = ref([]);

  const buildQuery = (configType: EnumType) => {
    return {
      deltaFiConfigs: {
        __args: {
          configQuery: {
            configType: configType
          }
        },
        name: true
      }
    };
  };

  const fetchIngressFlows = async () => {
    await queryGraphQL(buildQuery(new EnumType('INGRESS_FLOW')), "getIngressFlows");
    ingressFlows.value = _.sortBy(response.value.data.deltaFiConfigs, ["name"]);
  }

  const fetchEgressFlows = async () => {
    await queryGraphQL(buildQuery(new EnumType('EGRESS_FLOW')), "getEgressFlows");
    egressFlows.value = _.sortBy(response.value.data.deltaFiConfigs, ["name"]);
  }

  return { ingressFlows, egressFlows, fetchIngressFlows, fetchEgressFlows, loading, loaded, errors };
}