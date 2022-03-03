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