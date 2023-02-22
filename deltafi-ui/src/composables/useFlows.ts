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

import { ref, Ref } from 'vue'
import { EnumType } from 'json-to-graphql-query';
import useGraphQL from './useGraphQL'

export default function useFlows() {
  const { response, queryGraphQL, loading, loaded, errors } = useGraphQL();
  const ingressFlows: Ref<Array<Record<string, string>>> = ref([]);
  const egressFlows: Ref<Array<Record<string, string>>> = ref([]);

  const buildQuery = (enrich: Boolean, egress: Boolean, ingress: Boolean, state?: string) => {
    return {
      getFlowNames: {
        __args: {
          state: state ? new EnumType(state) : null,
        },
        enrich: enrich,
        egress: egress,
        ingress: ingress
      }
    };
  };

  const fetchIngressFlowNames = async (state?: string) => {
    await queryGraphQL(buildQuery(false, false, true, state), "getIngressFlowNames");
    ingressFlows.value = response.value.data.getFlowNames.ingress.sort();
  }

  const fetchEgressFlowNames = async (state?: string) => {
    await queryGraphQL(buildQuery(false, true, false, state), "getEgressFlowNames");
    egressFlows.value = response.value.data.getFlowNames.egress.sort();
  }

  return { ingressFlows, egressFlows, fetchIngressFlowNames, fetchEgressFlowNames, loading, loaded, errors };
}
