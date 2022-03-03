import { ref } from 'vue'
import useGraphQL from './useGraphQL'

export default function useFlowConfiguration() {
  const { response, queryGraphQL, loading, loaded, errors } = useGraphQL();
  const data = ref(null);

  const query = {
    exportConfigAsYaml: true
  }

  const fetch = async () => {
    try {
      await queryGraphQL(query, "exportConfigAsYaml");
      data.value = response.value.data.exportConfigAsYaml;
    } catch {
      // Continue regardless of error
    }
  }

  return { data, loading, loaded, fetch, errors };
}