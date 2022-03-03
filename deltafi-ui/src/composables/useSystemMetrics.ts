import { ref } from 'vue'
import useApi from './useApi'

export default function useSystemMetrics() {
  const { response, get, loading, loaded } = useApi();
  const endpoint = 'metrics/system/nodes';
  const data = ref([]);

  const fetch = async () => {
    try {
      await get(endpoint);
      data.value = response.value.nodes;
    } catch {
      // Continue regardless of error
    }
  }

  return { data, loaded, loading, fetch };
}